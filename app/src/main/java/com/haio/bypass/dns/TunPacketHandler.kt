package com.haio.bypass.dns

import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.util.Log
import kotlinx.coroutines.*
import kotlin.coroutines.coroutineContext
import java.io.FileInputStream
import java.nio.ByteBuffer

class TunPacketHandler(
    private val vpnService: VpnService,
    private val tunFdInt: Int
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var tunnelPfdA: ParcelFileDescriptor? = null
    private var tunnelPfdB: ParcelFileDescriptor? = null
    private var tunPfd: ParcelFileDescriptor? = null
    private var tunInput: FileInputStream? = null
    private var tunOutputFd: java.io.FileDescriptor? = null

    fun start(): Int {
        val pair = ParcelFileDescriptor.createSocketPair()
        tunnelPfdA = pair[0]
        tunnelPfdB = pair[1]

        tunPfd = ParcelFileDescriptor.fromFd(tunFdInt)
        tunInput = FileInputStream(tunPfd!!.fileDescriptor)
        tunOutputFd = tunPfd!!.fileDescriptor

        scope.launch { runReadFromTun() }
        scope.launch { runReadFromTunnel() }

        val tunnelFdInt = tunnelPfdB!!.detachFd()
        Log.i(TAG, "TunPacketHandler started, tunnel fd=$tunnelFdInt, tun fd=$tunFdInt")
        return tunnelFdInt
    }

    fun stop() {
        scope.cancel()
        try { tunnelPfdA?.close() } catch (_: Exception) {}
        tunnelPfdA = null
        tunnelPfdB = null
        try { tunInput?.close() } catch (_: Exception) {}
        tunInput = null
        try { tunPfd?.close() } catch (_: Exception) {}
        tunPfd = null
        tunOutputFd = null
    }

    private suspend fun runReadFromTun() {
        val buffer = ByteArray(2000)
        val input = tunInput ?: return

        while (coroutineContext.isActive) {
            try {
                val bytesRead = input.read(buffer)
                if (bytesRead <= 0) continue

                val tunnelA = tunnelPfdA
                if (tunnelA != null) {
                    Os.write(tunnelA.fileDescriptor, ByteBuffer.wrap(buffer, 0, bytesRead))
                }
            } catch (e: ErrnoException) {
                if (e.errno == OsConstants.EAGAIN) continue
                if (coroutineContext.isActive) Log.w(TAG, "Error reading from TUN", e)
                delay(1)
            } catch (e: Exception) {
                if (coroutineContext.isActive) Log.w(TAG, "Error reading from TUN", e)
                delay(1)
            }
        }
    }

    private suspend fun runReadFromTunnel() {
        val buffer = ByteArray(2000)
        val bb = ByteBuffer.wrap(buffer)
        val fdA = tunnelPfdA?.fileDescriptor ?: return
        val outFd = tunOutputFd ?: return
        val directBuf = ByteBuffer.allocateDirect(2000)

        while (coroutineContext.isActive) {
            try {
                bb.clear()
                val bytesRead = Os.read(fdA, bb)
                if (bytesRead <= 0) continue

                directBuf.clear()
                directBuf.put(buffer, 0, bytesRead)
                directBuf.flip()
                Os.write(outFd, directBuf)
            } catch (e: ErrnoException) {
                if (e.errno == OsConstants.EAGAIN) continue
                if (coroutineContext.isActive) Log.w(TAG, "Error reading from tunnel", e)
                delay(1)
            } catch (e: Exception) {
                if (coroutineContext.isActive) Log.w(TAG, "Error reading from tunnel", e)
                delay(1)
            }
        }
    }

    companion object {
        private const val TAG = "TunPacketHandler"
    }
}