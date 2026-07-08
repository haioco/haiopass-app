package com.haio.bypass.proxy

import android.content.Context
import android.net.LocalServerSocket
import android.net.LocalSocket
import android.net.VpnService
import android.system.Os
import android.system.OsConstants
import android.system.StructCmsghdr
import android.util.Log
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.File
import java.io.FileDescriptor
import java.io.InputStreamReader
import java.nio.ByteBuffer

class XrayManager(private val context: Context, private val vpnService: VpnService) {

    private var xrayProcess: Process? = null
    private var tun2SocksProcess: Process? = null
    private var watchdogJob: Job? = null
    private var protectionJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var intentionalStop = false

    private val workDir: File by lazy {
        File(context.codeCacheDir, "xray").also { it.mkdirs() }
    }
    private val configFile: File by lazy { File(workDir, "config.json") }

    fun start(configJson: String): Boolean {
        stop()

        intentionalStop = false
        configFile.writeText(configJson)
        Log.d(TAG, "xray config:\n$configJson")

        val binary = extractBinary() ?: run {
            Log.e(TAG, "xray binary extraction failed")
            return false
        }

        return try {
            val command = mutableListOf(
                LINKER64_PATH,
                binary.absolutePath,
                "run",
                "-c",
                configFile.absolutePath
            )

            Log.i(TAG, "Starting xray: ${command.joinToString(" ")}")
            val pb = ProcessBuilder(command)
            pb.directory(workDir)
            pb.redirectErrorStream(true)

            xrayProcess = pb.start()
            protectProcessSockets(xrayProcess!!)
            Log.i(TAG, "xray socket protection started")

            scope.launch {
                try {
                    val reader = BufferedReader(InputStreamReader(xrayProcess!!.inputStream))
                    reader.forEachLine { line ->
                        Log.d(TAG, "[xray] $line")
                    }
                } catch (_: Exception) {}
            }

            Thread.sleep(3000)

            if (!isXrayRunning()) {
                val exitCode = try { xrayProcess?.exitValue() } catch (_: Exception) { -1 }
                Log.e(TAG, "xray exited with code $exitCode")
                xrayProcess = null
                return false
            }

            startWatchdog()
            Log.i(TAG, "xray started successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start xray", e)
            stop()
            false
        }
    }

    fun stop() {
        intentionalStop = true
        watchdogJob?.cancel()
        watchdogJob = null
        protectionJob?.cancel()
        protectionJob = null
        try {
            xrayProcess?.destroy()
        } catch (_: Exception) {}
        xrayProcess = null
        try {
            tun2SocksProcess?.destroy()
        } catch (_: Exception) {}
        tun2SocksProcess = null
        Log.i(TAG, "xray and tun2socks stopped")
    }

    fun isRunning(): Boolean {
        return isXrayRunning() && (tun2SocksProcess == null || isTun2SocksRunning())
    }

    private fun isXrayRunning(): Boolean {
        return try {
            xrayProcess?.exitValue()
            false
        } catch (_: IllegalThreadStateException) {
            true
        }
    }

    private fun startWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = scope.launch {
            while (isActive) {
                delay(WATCHDOG_INTERVAL)
                if (intentionalStop) break
                if (!isXrayRunning()) {
                    Log.w(TAG, "xray died, restarting...")
                    xrayProcess = null
                    delay(1000)
                    if (!intentionalStop) {
                        val config = configFile.readText()
                        start(config)
                    }
                }
            }
        }
    }

    private fun extractBinary(): File? {
        val rawName = "xray_arm64_v8a"
        val targetFile = File(workDir, "xray")

        val resId = context.resources.getIdentifier(
            rawName, "raw", context.packageName
        )
        if (resId == 0) {
            Log.e(TAG, "Binary not found in resources: $rawName")
            return null
        }

        val expectedSize = context.resources.openRawResourceFd(resId).use { it.length }
        if (targetFile.exists() && targetFile.length() == expectedSize) {
            Log.d(TAG, "Binary already exists at ${targetFile.absolutePath}")
            return targetFile
        }

        Log.i(TAG, "Extracting binary: $rawName")
        return try {
            context.resources.openRawResource(resId).use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            Log.d(TAG, "Binary written, size: ${targetFile.length()}")
            targetFile.setExecutable(true, false)
            targetFile.setReadable(true, false)
            Log.d(TAG, "Binary permissions set: exe=${targetFile.canExecute()}")
            targetFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract binary: $rawName", e)
            null
        }
    }

    fun startTun2Socks(tunFd: Int, socksPort: Int): Boolean {
        preProtectXraySockets()

        val wrapper = extractWrapperBinary() ?: run {
            Log.e(TAG, "tun_wrapper binary extraction failed")
            return false
        }

        val tun2socks = extractTun2SocksBinary() ?: run {
            Log.e(TAG, "tun2socks binary extraction failed")
            return false
        }

        return try {
            val SOCKET_NAME = "haio_tun_fd_socket"
            val serverSocket = LocalServerSocket(SOCKET_NAME)
            Log.i(TAG, "Created server socket: $SOCKET_NAME")

            val command = mutableListOf(
                LINKER64_PATH,
                wrapper.absolutePath,
                SOCKET_NAME,
                tun2socks.absolutePath,
                "--device", "fd://3",
                "--proxy", "socks5://127.0.0.1:$socksPort",
                "--mtu", "1500"
            )
            Log.i(TAG, "Starting tun2socks via wrapper: ${command.joinToString(" ")}")

            val pb = ProcessBuilder(command)
            pb.directory(workDir)
            pb.redirectErrorStream(true)

            tun2SocksProcess = pb.start()

            scope.launch {
                try {
                    val reader = BufferedReader(InputStreamReader(tun2SocksProcess!!.inputStream))
                    reader.forEachLine { line ->
                        Log.d(TAG, "[tun2socks] $line")
                    }
                } catch (_: Exception) {}
            }

            val clientSocket = serverSocket.accept()
            Log.i(TAG, "Wrapper connected to server socket")

            sendFdOverSocket(clientSocket, tunFd)
            clientSocket.close()
            serverSocket.close()

            Thread.sleep(500)
            if (!isTun2SocksRunning()) {
                val exitCode = try { tun2SocksProcess?.exitValue() } catch (_: Exception) { -1 }
                Log.e(TAG, "tun2socks exited with code $exitCode")
                tun2SocksProcess = null
                return false
            }
            Log.i(TAG, "tun2socks started successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start tun2socks", e)
            false
        }
    }

    private fun isTun2SocksRunning(): Boolean {
        return try {
            tun2SocksProcess?.exitValue()
            false
        } catch (_: IllegalThreadStateException) {
            true
        }
    }

    private fun extractWrapperBinary(): File? {
        val rawName = "tun_wrapper_arm64_v8a"
        val targetFile = File(workDir, "tun_wrapper")

        val resId = context.resources.getIdentifier(
            rawName, "raw", context.packageName
        )
        if (resId == 0) {
            Log.e(TAG, "Binary not found in resources: $rawName")
            return null
        }

        val expectedSize = context.resources.openRawResourceFd(resId).use { it.length }
        if (targetFile.exists() && targetFile.length() == expectedSize) {
            Log.d(TAG, "Wrapper already exists at ${targetFile.absolutePath}")
            return targetFile
        }

        Log.i(TAG, "Extracting wrapper: $rawName")
        return try {
            context.resources.openRawResource(resId).use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            targetFile.setExecutable(true, false)
            targetFile.setReadable(true, false)
            Log.d(TAG, "Wrapper permissions set: exe=${targetFile.canExecute()}")
            targetFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract wrapper: $rawName", e)
            null
        }
    }

    private fun extractTun2SocksBinary(): File? {
        val rawName = "tun2socks_arm64_v8a"
        val targetFile = File(workDir, "tun2socks")

        val resId = context.resources.getIdentifier(
            rawName, "raw", context.packageName
        )
        if (resId == 0) {
            Log.e(TAG, "tun2socks binary not found in resources: $rawName")
            return null
        }

        val expectedSize = context.resources.openRawResourceFd(resId).use { it.length }
        if (targetFile.exists() && targetFile.length() == expectedSize) {
            patchElfHeader(targetFile)
            return targetFile
        }

        Log.i(TAG, "Extracting tun2socks binary: $rawName")
        return try {
            context.resources.openRawResource(resId).use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            targetFile.setExecutable(true, false)
            targetFile.setReadable(true, false)
            patchElfHeader(targetFile)
            Log.d(TAG, "tun2socks permissions set: exe=${targetFile.canExecute()}")
            targetFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract tun2socks binary: $rawName", e)
            null
        }
    }

    private fun patchElfHeader(file: File) {
        try {
            val raf = java.io.RandomAccessFile(file, "rw")
            raf.seek(16)
            val b0 = raf.read().toInt() and 0xFF
            val b1 = raf.read().toInt() and 0xFF
            if (b0 == -1 || b1 == -1) {
                raf.close()
                Log.w(TAG, "tun2socks too small to read e_type")
                return
            }
            val etype = b0 or (b1 shl 8)
            Log.d(TAG, "tun2socks ELF e_type before patch: 0x${String.format("%04X", etype)}")
            if (etype == 2) {
                raf.seek(16)
                raf.write(3)
                raf.write(0)
                Log.i(TAG, "Patched tun2socks ELF header: ET_EXEC -> ET_DYN")
            }
            raf.close()
            patchTlsAlignment(file)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to patch ELF header", e)
        }
    }

    private fun patchTlsAlignment(file: File) {
        try {
            val raf = java.io.RandomAccessFile(file, "rw")
            val header = ByteArray(64)
            raf.readFully(header)
            val e_phoff = readLong(header, 32)
            val e_phentsize = readShort(header, 54).toInt()
            val e_phnum = readShort(header, 56).toInt()
            val PT_TLS = 7
            val MIN_TLS_ALIGN = 64L
            for (i in 0 until e_phnum) {
                val off = e_phoff + i.toLong() * e_phentsize
                raf.seek(off)
                val ph = ByteArray(e_phentsize)
                raf.readFully(ph)
                if (readInt(ph, 0) != PT_TLS) continue
                val p_align = readLong(ph, 48)
                if (p_align < MIN_TLS_ALIGN) {
                    raf.seek(off + 48)
                    writeLong(raf, MIN_TLS_ALIGN)
                    Log.i(TAG, "Patched tun2socks PT_TLS p_align: $p_align -> $MIN_TLS_ALIGN")
                }
                break
            }
            raf.close()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to patch TLS alignment", e)
        }
    }

    private fun readInt(b: ByteArray, off: Int): Int {
        return (b[off].toInt() and 0xFF) or
            ((b[off + 1].toInt() and 0xFF) shl 8) or
            ((b[off + 2].toInt() and 0xFF) shl 16) or
            ((b[off + 3].toInt() and 0xFF) shl 24)
    }

    private fun readShort(b: ByteArray, off: Int): Int {
        return (b[off].toInt() and 0xFF) or
            ((b[off + 1].toInt() and 0xFF) shl 8)
    }

    private fun readLong(b: ByteArray, off: Int): Long {
        var v = 0L
        for (i in 0 until 8) {
            v = v or ((b[off + i].toLong() and 0xFF) shl (i * 8))
        }
        return v
    }

    private fun writeLong(raf: java.io.RandomAccessFile, v: Long) {
        for (i in 0 until 8) {
            raf.write((v shr (i * 8)).toInt() and 0xFF)
        }
    }

    private fun sendFdOverSocket(socket: LocalSocket, fd: Int) {
        try {
            val os = socket.fileDescriptor

            val fdBytes = ByteArray(4)
            fdBytes[0] = (fd and 0xFF).toByte()
            fdBytes[1] = ((fd shr 8) and 0xFF).toByte()
            fdBytes[2] = ((fd shr 16) and 0xFF).toByte()
            fdBytes[3] = ((fd shr 24) and 0xFF).toByte()

            val iov = arrayOf(java.nio.ByteBuffer.wrap(byteArrayOf(0)))
            val SCM_RIGHTS = 0x01
            val cmsg = StructCmsghdr(OsConstants.SOL_SOCKET, SCM_RIGHTS, fdBytes)
            val msg = android.system.StructMsghdr(null, iov, arrayOf(cmsg), 0)

            Os.sendmsg(os, msg, 0)
            Log.d(TAG, "Sent TUN fd=$fd via Unix socket")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send fd via socket", e)
        }
    }

    private fun protectProcessSockets(process: Process) {
        protectionJob?.cancel()
        try {
            val pidField = process.javaClass.getDeclaredField("pid")
            pidField.isAccessible = true
            val pid = pidField.getInt(process)
            Log.i(TAG, "xray pid=$pid, starting socket protection")
            startProtectionLoop(pid)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get xray PID", e)
        }
    }

    private fun startProtectionLoop(pid: Int) {
        protectionJob = scope.launch {
            var fastCycles = 600
            while (isActive) {
                try {
                    protectAllProcessSockets(pid)
                } catch (e: Exception) {
                    Log.w(TAG, "Protection loop error", e)
                }
                if (fastCycles > 0) {
                    fastCycles--
                    delay(50)
                } else {
                    delay(200)
                }
            }
        }
    }

    private fun preProtectXraySockets() {
        val pid = getXrayPid() ?: return
        Log.i(TAG, "Pre-protecting Xray sockets before tun2socks start...")
        Log.i(TAG, "PID=$pid, checking /proc/$pid/fd exists=${File("/proc/$pid/fd").exists()}")
        val fdDir = File("/proc/$pid/fd")
        val allFds = fdDir.listFiles()
        Log.i(TAG, "Total fds in /proc/$pid/fd: ${allFds?.size ?: "null"}")
        allFds?.take(10)?.forEach { f ->
            try {
                Log.d(TAG, "  fd ${f.name} -> ${Os.readlink(f.absolutePath)}")
            } catch (e: Exception) {
                Log.d(TAG, "  fd ${f.name} -> readlink error: ${e.message}")
            }
        }
        for (i in 0..20) {
            val count = protectAllProcessSocketsSync(pid)
            if (i == 0 || i == 20 || count > 0) {
                Log.d(TAG, "Protection burst pass $i: $count sockets protected")
            }
            Thread.sleep(5)
        }
        Log.i(TAG, "Socket protection burst complete")
    }

    private fun getXrayPid(): Int? {
        return try {
            val pidField = xrayProcess?.javaClass?.getDeclaredField("pid")
            pidField?.isAccessible = true
            pidField?.getInt(xrayProcess)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get Xray PID", e)
            null
        }
    }

    private fun protectAllProcessSocketsSync(pid: Int): Int {
        val fdDir = File("/proc/$pid/fd")
        val fds = fdDir.listFiles() ?: run {
            Log.w(TAG, "Cannot list /proc/$pid/fd — null")
            return 0
        }
        var count = 0

        for (fdEntry in fds) {
            val fdPath = fdEntry.absolutePath
            try {
                val target = Os.readlink(fdPath)
                if (target != null && target.startsWith("socket:[")) {
                    val fd = Os.open(fdPath, OsConstants.O_RDONLY, 0)
                    val intFd = FileDescriptor::class.java
                        .getDeclaredField("fd")
                        .also { it.isAccessible = true }
                        .getInt(fd)
                    val result = vpnService.protect(intFd)
                    Os.close(fd)
                    count++
                    if (count <= 2) {
                        Log.d(TAG, "Protected socket fd=$intFd (from $fdPath) result=$result")
                    }
                }
            } catch (e: Exception) {
                if (count == 0 && fds.indexOf(fdEntry) < 8) {
                    Log.d(TAG, "fd[${fdEntry.name}] protect error: ${e.javaClass.simpleName}: ${e.message}")
                }
            }
        }
        return count
    }

    private fun protectAllProcessSockets(pid: Int) {
        val fdDir = File("/proc/$pid/fd")
        val fds = fdDir.listFiles() ?: return

        for (fdEntry in fds) {
            val fdPath = fdEntry.absolutePath

            try {
                val target = Os.readlink(fdPath)
                if (target != null && target.startsWith("socket:[")) {
                    val fd = Os.open(fdPath, OsConstants.O_RDONLY, 0)
                    val intFd = FileDescriptor::class.java
                        .getDeclaredField("fd")
                        .also { it.isAccessible = true }
                        .getInt(fd)
                    vpnService.protect(intFd)
                    Os.close(fd)
                }
            } catch (_: Exception) {}
        }
    }

    companion object {
        private const val TAG = "XrayManager"
        private const val LINKER64_PATH = "/apex/com.android.runtime/bin/linker64"
        private val WATCHDOG_INTERVAL = 10_000L
    }
}