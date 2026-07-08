package com.haio.bypass.service

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import com.haio.bypass.config.ConfigManager
import com.haio.bypass.domain.DomainStore
import com.haio.bypass.proxy.ProxyManager
import kotlinx.coroutines.*

class HaioVpnService : VpnService() {

    private var proxyManager: ProxyManager? = null
    private var vpnInterface: ParcelFileDescriptor? = null
    private var vpnFd: Int = -1
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isStopping = false
    private var isRunning = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            Log.i(TAG, "Stop action received")
            stopVpn()
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
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, VpnNotification.create(this))

        vpnInterface = establishVpnInterface()
        if (vpnInterface == null) {
            Log.e(TAG, "Failed to establish VPN interface, stopping")
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
        scope.launch {
            try {
                proxyManager?.start(vpnFd, trojanConfig)
            } catch (e: ProxyManager.ProxyStartException) {
                Log.e(TAG, "Proxy failed to start: ${e.message}")
                stopVpn()
                stopSelf()
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error starting proxy", e)
                stopVpn()
                stopSelf()
            }
        }

        return START_STICKY
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
        try {
            proxyManager?.cleanup()
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up proxy manager", e)
        }
        proxyManager = null
        try {
            if (vpnFd >= 0) {
                ParcelFileDescriptor.adoptFd(vpnFd).close()
                vpnFd = -1
            }
        } catch (_: Exception) {}
        try {
            vpnInterface?.close()
        } catch (_: Exception) {}
        vpnInterface = null
    }

    override fun onDestroy() {
        stopVpn()
        scope.cancel()
        super.onDestroy()
    }

    override fun onRevoke() {
        stopVpn()
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
