package com.haio.bypass.service

import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.ServiceCompat
import com.haio.bypass.config.ConfigManager
import com.haio.bypass.domain.DomainStore
import com.haio.bypass.proxy.ProxyManager
import kotlinx.coroutines.*

class HaioVpnService : VpnService() {

    private var proxyManager: ProxyManager? = null
    private var vpnInterface: ParcelFileDescriptor? = null
    private var vpnFd: Int = -1
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var startJob: Job? = null
    private var isStopping = false
    private var isRunning = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            Log.i(TAG, "Stop action received")
            stopVpn()
            stopForegroundCompat()
            stopSelf()
            return START_NOT_STICKY
        }

        if (isRunning) {
            Log.w(TAG, "VPN already running, ignoring duplicate startCommand")
            return START_STICKY
        }

        val configManager = ConfigManager(applicationContext)
        val domainStore = DomainStore(applicationContext)

        val trojanConfig = configManager.getConfig().trojanConfig
        if (trojanConfig == null) {
            Log.e(TAG, "No Trojan config, stopping")
            ProxyManager.resetState()
            stopSelf()
            return START_NOT_STICKY
        }

        startForegroundCompat()

        vpnInterface = establishVpnInterface()
        if (vpnInterface == null) {
            Log.e(TAG, "Failed to establish VPN interface, stopping")
            ProxyManager.resetState()
            stopForegroundCompat()
            stopSelf()
            return START_NOT_STICKY
        }

        vpnFd = vpnInterface!!.detachFd()
        vpnInterface = null
        if (vpnFd < 0) {
            Log.e(TAG, "Invalid VPN fd, stopping")
            stopSelf()
            return START_NOT_STICKY
        }
        Log.i(TAG, "VPN interface established, fd=$vpnFd")
        isRunning = true

        proxyManager = ProxyManager(applicationContext, configManager, domainStore, this)
        startJob = scope.launch {
            try {
                proxyManager?.start(vpnFd, trojanConfig)
            } catch (e: ProxyManager.ProxyStartException) {
                Log.e(TAG, "Proxy failed to start: ${e.message}")
                stopVpn()
                stopForegroundCompat()
                stopSelf()
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error starting proxy", e)
                stopVpn()
                stopForegroundCompat()
                stopSelf()
            }
        }

        return START_STICKY
    }

    private fun startForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                VpnNotification.create(this),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, VpnNotification.create(this))
        }
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    private fun establishVpnInterface(): ParcelFileDescriptor? {
        return try {
            Builder()
                .setSession("HaioBypass")
                .setMtu(1500)
                .addAddress("172.19.0.1", 30)
                .addRoute("0.0.0.0", 0)
                .addDnsServer("8.8.8.8")
                .addDisallowedApplication(packageName)
                .establish()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to build VPN interface", e)
            null
        }
    }

    private fun stopVpn() {
        if (isStopping) return
        isStopping = true
        isRunning = false

        // Cancel an in-flight start coroutine before tearing things down so we
        // don't race with detachFd()/establish() of the TUN interface.
        startJob?.cancel()
        startJob = null

        try {
            proxyManager?.cleanup()
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up proxy manager", e)
        }
        proxyManager = null

        // Explicitly close the raw VPN fd to tear down the TUN interface.
        // This is required because detachFd() transferred ownership to the native process.
        // Closing the fd here ensures the VPN is revoked even if the process stays alive.
        try {
            if (vpnFd >= 0) {
                ParcelFileDescriptor.adoptFd(vpnFd).close()
                vpnFd = -1
            }
        } catch (_: Exception) {
        }

        vpnInterface = null
        ProxyManager.resetState()
    }

    override fun onDestroy() {
        stopVpn()
        stopForegroundCompat()
        scope.cancel()
        super.onDestroy()
    }

    override fun onRevoke() {
        stopVpn()
        stopForegroundCompat()
        scope.cancel()
        super.onRevoke()
    }

    companion object {
        private const val TAG = "HaioVpnService"
        private const val NOTIFICATION_ID = 1
        const val ACTION_STOP = "com.haio.bypass.STOP_VPN"

        fun createStopIntent(context: android.content.Context): Intent {
            return Intent(context, HaioVpnService::class.java).apply {
                action = ACTION_STOP
            }
        }
    }
}
