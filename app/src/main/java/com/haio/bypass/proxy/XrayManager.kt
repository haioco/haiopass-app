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
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import java.nio.ByteBuffer

class XrayManager(private val context: Context, private val vpnService: VpnService) {

    private var xrayProcess: Process? = null
    private var tun2SocksProcess: Process? = null
    private var watchdogJob: Job? = null
    private var protectionJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var xrayPid: Int? = null
    private var tun2SocksPid: Int? = null
    private var intentionalStop = false

    private val workDir: File by lazy {
        File(context.codeCacheDir, "xray").also { it.mkdirs() }
    }
    private val configFile: File by lazy { File(workDir, "config.json") }

    suspend fun start(configJson: String): Boolean {
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

            val deadline = System.currentTimeMillis() + 5_000
            while (System.currentTimeMillis() < deadline) {
                if (!isXrayRunning()) break
                delay(200)
            }

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

        killProcess(xrayProcess)
        xrayProcess = null
        killProcess(tun2SocksProcess)
        tun2SocksProcess = null

        xrayPid = null
        tun2SocksPid = null
        Log.i(TAG, "xray and tun2socks stopped")
    }

    private fun killProcess(process: Process?) {
        if (process == null) return
        try {
            process.destroy()
        } catch (_: Exception) {
        }
        try {
            if (!process.waitFor(2_000, TimeUnit.MILLISECONDS)) {
                Log.w(TAG, "process didn't exit on SIGTERM, sending SIGKILL")
                process.destroyForcibly()
                process.waitFor(2_000, TimeUnit.MILLISECONDS)
            }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            try { process.destroyForcibly() } catch (_: Exception) {
            }
        } catch (_: Exception) {
            try { process.destroyForcibly() } catch (_: Exception) {
            }
        }
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
        val assetName = "xray_arm64_v8a"
        val targetFile = File(workDir, "xray")

        val expectedSize = try {
            context.assets.openFd(assetName).use { it.length }
        } catch (_: Exception) { 0L }
        if (targetFile.exists() && targetFile.length() == expectedSize) {
            Log.d(TAG, "Binary already exists at ${targetFile.absolutePath}")
            return targetFile
        }

        Log.i(TAG, "Extracting binary: $assetName")
        return try {
            context.assets.open(assetName).use { input ->
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
            Log.e(TAG, "Failed to extract binary: $assetName", e)
            null
        }
    }

    suspend fun startTun2Socks(tunFd: Int, socksPort: Int): Boolean {
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

            val clientSocket = withTimeoutOrNull(8_000) {
                var socket: LocalSocket? = null
                while (socket == null && isTun2SocksRunning()) {
                    try {
                        socket = serverSocket.accept()
                    } catch (_: Exception) {
                        delay(50)
                    }
                }
                socket
            }
            if (clientSocket == null) {
                Log.e(TAG, "tun2socks wrapper did not connect to server socket (timeout or process died)")
                try { serverSocket.close() } catch (_: Exception) {}
                try { tun2SocksProcess?.destroy() } catch (_: Exception) {}
                tun2SocksProcess = null
                return false
            }
            Log.i(TAG, "Wrapper connected to server socket")

            sendFdOverSocket(clientSocket, tunFd)
            clientSocket.close()
            serverSocket.close()

            val deadline = System.currentTimeMillis() + 5_000
            while (System.currentTimeMillis() < deadline) {
                if (!isTun2SocksRunning()) break
                delay(100)
            }
            if (!isTun2SocksRunning()) {
                val exitCode = try { tun2SocksProcess?.exitValue() } catch (_: Exception) { -1 }
                Log.e(TAG, "tun2socks exited with code $exitCode")
                tun2SocksProcess = null
                return false
            }
            Log.i(TAG, "tun2socks started successfully")
            protectTun2SocksSockets(tun2SocksProcess!!)
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
        val assetName = "tun_wrapper_arm64_v8a"
        val targetFile = File(workDir, "tun_wrapper")

        val expectedSize = try {
            context.assets.openFd(assetName).use { it.length }
        } catch (_: Exception) { 0L }
        if (targetFile.exists() && targetFile.length() == expectedSize) {
            Log.d(TAG, "Wrapper already exists at ${targetFile.absolutePath}")
            return targetFile
        }

        Log.i(TAG, "Extracting wrapper: $assetName")
        return try {
            context.assets.open(assetName).use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            targetFile.setExecutable(true, false)
            targetFile.setReadable(true, false)
            Log.d(TAG, "Wrapper permissions set: exe=${targetFile.canExecute()}")
            targetFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract wrapper: $assetName", e)
            null
        }
    }

    private fun extractTun2SocksBinary(): File? {
        val assetName = "tun2socks_arm64_v8a"
        val targetFile = File(workDir, "tun2socks")

        val expectedSize = try {
            context.assets.openFd(assetName).use { it.length }
        } catch (_: Exception) { 0L }
        if (targetFile.exists() && targetFile.length() == expectedSize) {
            return targetFile
        }

        Log.i(TAG, "Extracting tun2socks binary: $assetName")
        return try {
            context.assets.open(assetName).use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            targetFile.setExecutable(true, false)
            targetFile.setReadable(true, false)
            Log.d(TAG, "tun2socks permissions set: exe=${targetFile.canExecute()}")
            targetFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract tun2socks binary: $assetName", e)
            null
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
            val pid = ProcessUtils.getPid(process)
            xrayPid = pid
            Log.i(TAG, "xray pid=$pid, starting socket protection")
            startProtectionLoop()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get xray PID", e)
        }
    }

    private fun protectTun2SocksSockets(process: Process) {
        try {
            val pid = ProcessUtils.getPid(process)
            tun2SocksPid = pid
            Log.i(TAG, "tun2socks pid=$pid, adding to socket protection")
            startProtectionLoop()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get tun2socks PID", e)
        }
    }

    private fun startProtectionLoop() {
        protectionJob?.cancel()
        protectionJob = scope.launch {
            var fastCycles = 600
            while (isActive) {
                try {
                    xrayPid?.let { protectAllProcessSockets(it) }
                    tun2SocksPid?.let { protectAllProcessSockets(it) }
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
        val process = xrayProcess ?: return null
        return try {
            ProcessUtils.getPid(process)
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
                    val dupFd = Os.open(fdPath, OsConstants.O_RDONLY, 0)
                    val intFd = ProcessUtils.getFdInt(dupFd)
                    val result = vpnService.protect(intFd)
                    Os.close(dupFd)
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
                    val dupFd = Os.open(fdPath, OsConstants.O_RDONLY, 0)
                    val intFd = ProcessUtils.getFdInt(dupFd)
                    vpnService.protect(intFd)
                    Os.close(dupFd)
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