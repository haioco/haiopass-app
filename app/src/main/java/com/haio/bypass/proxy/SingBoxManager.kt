package com.haio.bypass.proxy

import android.content.Context
import android.net.LocalServerSocket
import android.net.LocalSocket
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * sing-box 1.14.4 core for device-locked HaioGate credentials.
 *
 * The binary is shipped as `assets/sing_box_arm64_v8a` and extracted to
 * `codeCacheDir/singbox` (0700) before launch, exactly like Xray. sing-box
 * exposes a local SOCKS inbound; tun2socks is started with the same wrapper
 * and FD-passing path used by [XrayManager].
 *
 * Secrets: the generated config contains the Trojan password and the device
 * identity file paths. This class never logs the config, unlike the debug-only
 * Xray config dump.
 */
class SingBoxManager(
    private val context: Context,
    private val vpnService: VpnService
) : ProxyCore {

    private var singBoxProcess: Process? = null
    private var tun2SocksProcess: Process? = null
    private var watchdogJob: Job? = null
    private var protectionJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var singBoxPid: Int? = null
    private var tun2SocksPid: Int? = null
    private var intentionalStop = false
    private var currentSocksPort: Int = 10808

    private val workDir: File by lazy {
        File(context.codeCacheDir, "singbox").also {
            it.mkdirs()
            runCatching { Os.chmod(it.absolutePath, OsConstants.S_IRWXU) }
        }
    }
    private val configFile: File by lazy { File(workDir, "config.json") }

    override suspend fun start(configJson: String, socksPort: Int): Boolean {
        stop()
        return startSingBox(configJson, socksPort, manageWatchdog = true)
    }

    override suspend fun reload(configJson: String, socksPort: Int): Boolean {
        if (tun2SocksProcess == null) {
            return start(configJson, socksPort)
        }
        stopSingBoxOnly()
        return startSingBox(configJson, socksPort, manageWatchdog = true)
    }

    private suspend fun startSingBox(
        configJson: String,
        socksPort: Int,
        manageWatchdog: Boolean
    ): Boolean {
        currentSocksPort = socksPort
        intentionalStop = false
        configFile.writeText(configJson)
        runCatching { Os.chmod(configFile.absolutePath, OsConstants.S_IRUSR or OsConstants.S_IWUSR) }

        val binary = extractBinary() ?: run {
            Log.e(TAG, "sing-box binary extraction failed")
            return false
        }

        return try {
            val command = mutableListOf(
                getLinker64Path(),
                binary.absolutePath,
                "run",
                "-c",
                configFile.absolutePath
            )
            Log.i(TAG, "Starting sing-box")
            val builder = ProcessBuilder(command)
            builder.directory(workDir)
            builder.redirectErrorStream(true)

            val process = builder.start()
            singBoxProcess = process
            protectSingBoxSockets(process)

            scope.launch {
                try {
                    BufferedReader(InputStreamReader(process.inputStream)).forEachLine { line ->
                        // sing-box logs never contain the config dump, but be
                        // conservative: only emit line text at debug level.
                        if (Log.isLoggable(TAG, Log.DEBUG)) Log.d(TAG, "[sing-box] $line")
                    }
                } catch (_: Exception) {
                }
            }

            val ready = waitForSocksPort(socksPort, SOCKS_READY_TIMEOUT_MS)
            if (!ready || !isSingBoxRunning()) {
                val exitCode = try { singBoxProcess?.exitValue() } catch (_: Exception) { -1 }
                Log.e(TAG, "sing-box exited with code $exitCode")
                killSingBoxProcessOnly()
                return false
            }

            if (manageWatchdog) startWatchdog()
            Log.i(TAG, "sing-box started successfully")
            true
        } catch (exception: Exception) {
            Log.e(TAG, "Failed to start sing-box", exception)
            killSingBoxProcessOnly()
            false
        }
    }

    private fun killSingBoxProcessOnly() {
        killProcess(singBoxProcess)
        singBoxProcess = null
        singBoxPid = null
    }

    private fun stopSingBoxOnly() {
        intentionalStop = true
        watchdogJob?.cancel()
        watchdogJob = null
        protectionJob?.cancel()
        protectionJob = null
        killSingBoxProcessOnly()
        Log.i(TAG, "sing-box stopped")
    }

    override fun stop() {
        stopSingBoxOnly()
        killProcess(tun2SocksProcess)
        tun2SocksProcess = null
        tun2SocksPid = null
        Log.i(TAG, "sing-box and tun2socks stopped")
    }

    override fun isRunning(): Boolean =
        isSingBoxRunning() && (tun2SocksProcess == null || isTun2SocksRunning())

    private fun killProcess(process: Process?) {
        if (process == null) return
        try {
            process.destroy()
        } catch (_: Exception) {
        }
        try {
            if (!process.waitFor(2_000, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly()
                process.waitFor(2_000, TimeUnit.MILLISECONDS)
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            runCatching { process.destroyForcibly() }
        } catch (_: Exception) {
            runCatching { process.destroyForcibly() }
        }
    }

    private suspend fun waitForSocksPort(port: Int, timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (!isSingBoxRunning()) return false
            try {
                java.net.Socket().use { socket ->
                    socket.connect(java.net.InetSocketAddress("127.0.0.1", port), 100)
                    return true
                }
            } catch (_: Exception) {
                delay(50)
            }
        }
        return false
    }

    private fun isSingBoxRunning(): Boolean = try {
        singBoxProcess?.exitValue()
        false
    } catch (_: IllegalThreadStateException) {
        true
    }

    private fun startWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = scope.launch {
            while (isActive) {
                delay(WATCHDOG_INTERVAL)
                if (intentionalStop) break
                if (!isSingBoxRunning()) {
                    Log.w(TAG, "sing-box died, restarting...")
                    singBoxProcess = null
                    delay(1_000)
                    if (!intentionalStop) {
                        val config = configFile.readText()
                        startSingBox(config, currentSocksPort, manageWatchdog = false)
                    }
                }
            }
        }
    }

    // ── Binary extraction (verified against the pinned upstream artifact) ──

    private fun extractBinary(): File? {
        val assetName = "sing_box_arm64_v8a"
        val targetFile = File(workDir, "sing-box")

        if (targetFile.exists() && targetFile.length() == ASSET_EXPECTED_SIZE) {
            return targetFile
        }

        Log.i(TAG, "Extracting sing-box binary: $assetName")
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            context.assets.open(assetName).use { input ->
                targetFile.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        digest.update(buffer, 0, read)
                        output.write(buffer, 0, read)
                    }
                }
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            if (actual != ASSET_SHA256) {
                Log.e(TAG, "sing-box asset checksum mismatch")
                targetFile.delete()
                return null
            }
            runCatching { Os.chmod(targetFile.absolutePath, OsConstants.S_IRWXU) }
            targetFile.setExecutable(true, false)
            targetFile.setReadable(true, false)
            targetFile
        } catch (exception: Exception) {
            Log.e(TAG, "Failed to extract sing-box binary", exception)
            null
        }
    }

    // ── tun2socks (same wrapper/FD protocol as XrayManager) ────────────────

    override suspend fun startTun2Socks(tunFd: Int, socksPort: Int): Boolean {
        preProtectSingBoxSockets()

        val wrapper = extractAuxBinary("tun_wrapper_arm64_v8a", "tun_wrapper") ?: run {
            Log.e(TAG, "tun_wrapper binary extraction failed")
            return false
        }
        val tun2socks = extractAuxBinary("tun2socks_arm64_v8a", "tun2socks") ?: run {
            Log.e(TAG, "tun2socks binary extraction failed")
            return false
        }

        return try {
            val socketName = "haio_singbox_tun_fd_socket"
            val serverSocket = LocalServerSocket(socketName)
            val command = mutableListOf(
                getLinker64Path(),
                wrapper.absolutePath,
                socketName,
                tun2socks.absolutePath,
                "--device", "fd://3",
                "--proxy", "socks5://127.0.0.1:$socksPort",
                "--mtu", "1420"
            )
            Log.i(TAG, "Starting tun2socks for sing-box")

            val builder = ProcessBuilder(command)
            builder.directory(workDir)
            builder.redirectErrorStream(true)
            tun2SocksProcess = builder.start()

            scope.launch {
                try {
                    BufferedReader(InputStreamReader(tun2SocksProcess!!.inputStream)).forEachLine { line ->
                        if (Log.isLoggable(TAG, Log.DEBUG)) Log.d(TAG, "[tun2socks] $line")
                    }
                } catch (_: Exception) {
                }
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
                Log.e(TAG, "tun2socks wrapper did not connect to server socket")
                runCatching { serverSocket.close() }
                runCatching { tun2SocksProcess?.destroy() }
                tun2SocksProcess = null
                return false
            }

            sendFdOverSocket(clientSocket, tunFd)
            clientSocket.close()
            serverSocket.close()

            val deadline = System.currentTimeMillis() + TUN2SOCKS_START_GRACE_MS
            while (System.currentTimeMillis() < deadline) {
                if (!isTun2SocksRunning()) break
                delay(100)
            }
            if (!isTun2SocksRunning()) {
                Log.e(TAG, "tun2socks exited during startup")
                tun2SocksProcess = null
                return false
            }
            Log.i(TAG, "tun2socks started successfully")
            protectTun2SocksSockets(tun2SocksProcess!!)
            true
        } catch (exception: Exception) {
            Log.e(TAG, "Failed to start tun2socks", exception)
            false
        }
    }

    private fun isTun2SocksRunning(): Boolean = try {
        tun2SocksProcess?.exitValue()
        false
    } catch (_: IllegalThreadStateException) {
        true
    }

    private fun sendFdOverSocket(socket: LocalSocket, fd: Int) {
        ParcelFileDescriptor.fromFd(fd).use { duplicate ->
            socket.setFileDescriptorsForSend(arrayOf(duplicate.fileDescriptor))
            socket.outputStream.write(0)
            socket.outputStream.flush()
            socket.setFileDescriptorsForSend(null)
        }
    }

    private fun extractAuxBinary(assetName: String, fileName: String): File? {
        val targetFile = File(workDir, fileName)
        try {
            val expectedSize = context.assets.openFd(assetName).use { it.length }
            if (targetFile.exists() && targetFile.length() == expectedSize) return targetFile
        } catch (_: Exception) {
        }
        return try {
            context.assets.open(assetName).use { input ->
                targetFile.outputStream().use { output -> input.copyTo(output) }
            }
            targetFile.setExecutable(true, false)
            targetFile.setReadable(true, false)
            targetFile
        } catch (exception: Exception) {
            Log.e(TAG, "Failed to extract $assetName", exception)
            null
        }
    }

    // ── Socket protection (VPN routing loop prevention) ────────────────────

    private fun protectSingBoxSockets(process: Process) {
        protectionJob?.cancel()
        try {
            singBoxPid = ProcessUtils.getPid(process)
            startProtectionLoop()
        } catch (exception: Exception) {
            Log.e(TAG, "Failed to get sing-box PID", exception)
        }
    }

    private fun protectTun2SocksSockets(process: Process) {
        try {
            tun2SocksPid = ProcessUtils.getPid(process)
            startProtectionLoop()
        } catch (exception: Exception) {
            Log.e(TAG, "Failed to get tun2socks PID", exception)
        }
    }

    private fun startProtectionLoop() {
        protectionJob?.cancel()
        protectionJob = scope.launch {
            var fastCycles = 60
            while (isActive) {
                try {
                    singBoxPid?.let { protectAllProcessSockets(it) }
                    tun2SocksPid?.let { protectAllProcessSockets(it) }
                } catch (exception: Exception) {
                    Log.w(TAG, "Protection loop error", exception)
                }
                if (fastCycles > 0) {
                    fastCycles--
                    delay(200)
                } else {
                    delay(1_000)
                }
            }
        }
    }

    private fun preProtectSingBoxSockets() {
        val pid = singBoxPid ?: return
        for (i in 0..20) {
            protectAllProcessSocketsSync(pid)
            Thread.sleep(5)
        }
    }

    private fun protectAllProcessSocketsSync(pid: Int): Int {
        val fds = File("/proc/$pid/fd").listFiles() ?: return 0
        var count = 0
        for (fdEntry in fds) {
            try {
                val target = Os.readlink(fdEntry.absolutePath)
                if (target != null && target.startsWith("socket:[")) {
                    val dupFd = Os.open(fdEntry.absolutePath, OsConstants.O_RDONLY, 0)
                    val intFd = ProcessUtils.getFdInt(dupFd)
                    vpnService.protect(intFd)
                    Os.close(dupFd)
                    count++
                }
            } catch (_: Exception) {
            }
        }
        return count
    }

    private fun protectAllProcessSockets(pid: Int) {
        val fds = File("/proc/$pid/fd").listFiles() ?: return
        for (fdEntry in fds) {
            try {
                val target = Os.readlink(fdEntry.absolutePath)
                if (target != null && target.startsWith("socket:[")) {
                    val dupFd = Os.open(fdEntry.absolutePath, OsConstants.O_RDONLY, 0)
                    val intFd = ProcessUtils.getFdInt(dupFd)
                    vpnService.protect(intFd)
                    Os.close(dupFd)
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun getLinker64Path(): String {
        val apex = "/apex/com.android.runtime/bin/linker64"
        return if (File(apex).exists()) apex else "/system/bin/linker64"
    }

    companion object {
        private const val TAG = "SingBoxManager"

        /** Pinned sing-box 1.14.4 android-arm64 binary from the Phase 0 spike. */
        const val ASSET_SHA256 =
            "2ce1c1c2a265e7be21ffa7aaf2b58619986629e9caecfc2d3f80ef4d25b7f965"
        const val ASSET_EXPECTED_SIZE = 82_765_688L

        private const val WATCHDOG_INTERVAL = 10_000L
        private const val SOCKS_READY_TIMEOUT_MS = 5_000L
        private const val TUN2SOCKS_START_GRACE_MS = 1_200L
    }
}
