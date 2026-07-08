package com.haio.bypass.proxy

import android.content.Context
import android.net.VpnService
import android.util.Log
import com.haio.bypass.config.ConfigManager
import com.haio.bypass.config.TrojanConfig
import com.haio.bypass.dns.TunPacketHandler
import com.haio.bypass.domain.DomainFetcher
import com.haio.bypass.domain.DomainStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ProxyManager(
    private val context: Context,
    private val configManager: ConfigManager,
    private val domainStore: DomainStore,
    private val vpnService: VpnService
) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val domainFetcher = DomainFetcher()
    private val xrayManager = XrayManager(context, vpnService)
    private val configGenerator = XrayConfigGenerator()
    private var refreshJob: Job? = null
    private var tunPacketHandler: TunPacketHandler? = null

    suspend fun start(tunFd: Int, trojanConfig: TrojanConfig) {
        _proxyState.value = ProxyState.CONNECTING
        _statusMessage.value = "Fetching domains..."

        val domains = domainFetcher.fetch()
        if (domains.isNotEmpty()) {
            domainStore.setDomains(domains)
        } else {
            val cached = domainStore.getDomains()
            if (cached.isNotEmpty()) {
                _statusMessage.value = "Using ${cached.size} cached domains..."
            }
        }

        _statusMessage.value = "Starting xray..."

        val socksPort = configManager.getConfig().socksPort
        val configJson = configGenerator.generateConfig(
            socksPort = socksPort,
            trojanConfig = trojanConfig,
            bypassDomains = domainStore.getDomains()
        )

        val xrayStarted = xrayManager.start(configJson)
        if (!xrayStarted) {
            _proxyState.value = ProxyState.DISCONNECTED
            _statusMessage.value = "Failed to start xray"
            throw ProxyStartException("xray failed to start")
        }

        _statusMessage.value = "Starting tunnel..."
        tunPacketHandler = TunPacketHandler(vpnService, tunFd)
        val tunnelFdInt = tunPacketHandler!!.start()

        _statusMessage.value = "Starting tun2socks..."
        val tun2socksStarted = xrayManager.startTun2Socks(tunnelFdInt, socksPort)
        if (!tun2socksStarted) {
            _proxyState.value = ProxyState.DISCONNECTED
            _statusMessage.value = "Failed to start tun2socks"
            throw ProxyStartException("tun2socks failed to start")
        }

        _proxyState.value = ProxyState.CONNECTED
        _statusMessage.value = "Connected - ${domainStore.getDomainCount()} domains"

        configManager.updateConfig { it.copy(enabled = true) }

        startPeriodicRefresh()
    }

    fun stop() {
        refreshJob?.cancel()
        refreshJob = null

        try {
            tunPacketHandler?.stop()
        } catch (e: Exception) {
            Log.e("ProxyManager", "Error stopping tunPacketHandler", e)
        }
        tunPacketHandler = null

        try {
            xrayManager.stop()
        } catch (e: Exception) {
            Log.e("ProxyManager", "Error stopping xray", e)
        }

        _proxyState.value = ProxyState.DISCONNECTED
        _statusMessage.value = "Disconnected"

        configManager.updateConfig { it.copy(enabled = false) }
    }

    fun refreshDomains(scope: CoroutineScope) {
        scope.launch {
            val domains = domainFetcher.fetch()
            domainStore.setDomains(domains)
            _statusMessage.value = "Connected - ${domainStore.getDomainCount()} domains"
        }
    }

    private fun startPeriodicRefresh() {
        refreshJob?.cancel()
        refreshJob = scope.launch {
            while (isActive) {
                delay(REFRESH_INTERVAL)
                val domains = domainFetcher.fetch()
                if (domains.isNotEmpty()) {
                    domainStore.setDomains(domains)
                    _statusMessage.value = "Connected - ${domainStore.getDomainCount()} domains"
                }
            }
        }
    }

    fun cleanup() {
        stop()
        scope.cancel()
    }

    enum class ProxyState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED
    }

    class ProxyStartException(message: String) : Exception(message)

    companion object {
        private val REFRESH_INTERVAL = 60 * 60 * 1000L

        private val _proxyState = MutableStateFlow(ProxyState.DISCONNECTED)
        val proxyState: StateFlow<ProxyState> = _proxyState.asStateFlow()

        private val _statusMessage = MutableStateFlow("Disconnected")
        val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()
    }
}
