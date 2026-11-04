package com.haio.bypass.proxy

import android.content.Context
import android.net.VpnService
import android.util.Log
import com.haio.bypass.config.ConfigManager
import com.haio.bypass.config.TrojanConfig
import com.haio.bypass.domain.DomainFetcher
import com.haio.bypass.domain.DomainStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.drop

class ProxyManager(
    private val context: Context,
    private val configManager: ConfigManager,
    private val domainStore: DomainStore,
    private val vpnService: VpnService
) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val domainFetcher = DomainFetcher(context)
    private val xrayManager = XrayManager(context, vpnService)
    private val configGenerator = XrayConfigGenerator()
    private var refreshJob: Job? = null
    private var configReloadJob: Job? = null
    private var activeTrojanConfig: TrojanConfig? = null
    private var lastAppliedConfig: String? = null

    suspend fun start(tunFd: Int, trojanConfig: TrojanConfig) {
        _proxyState.value = ProxyState.CONNECTING
        _statusMessage.value = "در حال اتصال به روتر ابری..."
        activeTrojanConfig = trojanConfig

        var healthJob: Deferred<Boolean>? = null
        try {
            _statusMessage.value = "در حال راه‌اندازی روتر ابری..."

            val socksPort = configManager.getConfig().socksPort
            val configJson = configGenerator.generateConfig(
                socksPort = socksPort,
                trojanConfig = trojanConfig,
                bypassDomains = domainStore.getDomains(),
                directDomains = domainStore.getExcludedDomains()
            )

            // Probe the real server while Xray is starting so the health check
            // does not add another sequential delay to connection setup.
            healthJob = scope.async { TunnelHealthChecker.check(trojanConfig) }

            val xrayStarted = xrayManager.start(configJson, socksPort)
            if (!xrayStarted) {
                throw ProxyStartException("xray failed to start")
            }

            _statusMessage.value = "در حال اتصال تونل..."
            val tun2socksStarted = xrayManager.startTun2Socks(tunFd, socksPort)
            if (!tun2socksStarted) {
                throw ProxyStartException("tun2socks failed to start")
            }

            _statusMessage.value = "در حال بررسی اتصال..."
            if (!healthJob.await()) {
                throw ProxyStartException("health check failed")
            }

            lastAppliedConfig = configJson
            _proxyState.value = ProxyState.CONNECTED
            _statusMessage.value = connectedMessage()

            configManager.updateConfig { it.copy(enabled = true) }

            startPeriodicRefresh()
            startConfigObserver()
        } catch (e: CancellationException) {
            healthJob?.cancel()
            stop()
            throw e
        } catch (e: Exception) {
            healthJob?.cancel()
            stop()
            _proxyState.value = ProxyState.DISCONNECTED
            _statusMessage.value = when {
                e.message?.contains("health", ignoreCase = true) == true ->
                    "سرور روتر ابری در دسترس نیست"
                e.message?.contains("tun2socks", ignoreCase = true) == true ->
                    "راه‌اندازی تونل ناموفق بود"
                else -> "اتصال به روتر ابری برقرار نشد"
            }
            throw ProxyStartException(e.message ?: "unknown error")
        }
    }

    fun stop() {
        refreshJob?.cancel()
        refreshJob = null
        configReloadJob?.cancel()
        configReloadJob = null

        try {
            xrayManager.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping xray", e)
        }

        activeTrojanConfig = null
        lastAppliedConfig = null
        _proxyState.value = ProxyState.DISCONNECTED
        _statusMessage.value = "قطع شد"

        configManager.updateConfig { it.copy(enabled = false) }
    }

    fun refreshDomains(scope: CoroutineScope) {
        scope.launch {
            val domains = domainFetcher.fetch()
            if (domains.isNotEmpty()) {
                domainStore.setDomains(domains)
                if (_proxyState.value == ProxyState.CONNECTED) {
                    _statusMessage.value = connectedMessage()
                }
            }
        }
    }

    /**
     * Applies bypass-list changes to a running tunnel by restarting only
     * Xray. tun2socks and the TUN interface stay up, so the user does not see
     * a disconnect and domain changes apply immediately.
     */
    private fun startConfigObserver() {
        configReloadJob?.cancel()
        configReloadJob = scope.launch {
            combine(
                domainStore.domains,
                domainStore.excludedDomains
            ) { domains, excluded -> domains to excluded }
                .drop(1)
                .conflate()
                .collect { (domains, excluded) ->
                    // Coalesce bursts of edits before touching the process.
                    delay(CONFIG_RELOAD_DEBOUNCE_MS)

                    val routingConfig = activeTrojanConfig ?: return@collect
                    val socksPort = configManager.getConfig().socksPort
                    val newConfig = configGenerator.generateConfig(
                        socksPort = socksPort,
                        trojanConfig = routingConfig,
                        bypassDomains = domains,
                        directDomains = excluded
                    )
                    if (newConfig == lastAppliedConfig) return@collect

                    val previousConfig = lastAppliedConfig
                    _statusMessage.value = "در حال به‌روزرسانی مسیرها..."
                    val applied = xrayManager.reload(newConfig, socksPort)

                    if (applied) {
                        lastAppliedConfig = newConfig
                        _statusMessage.value = connectedMessage()
                    } else {
                        Log.e(TAG, "Failed to apply routing config, rolling back")
                        val rolledBack = previousConfig != null &&
                                xrayManager.reload(previousConfig, socksPort)
                        if (rolledBack) {
                            _statusMessage.value = connectedMessage()
                        } else {
                            xrayManager.stop()
                            _proxyState.value = ProxyState.DISCONNECTED
                            _statusMessage.value = "خطا در اعمال تغییرات مسیر"
                        }
                    }
                }
        }
    }

    private fun connectedMessage(): String =
        "متصل • ${domainStore.getDomainCount()} دامنه"

    private fun startPeriodicRefresh() {
        refreshJob?.cancel()
        refreshJob = scope.launch {
            // Refresh once shortly after the tunnel is up, then hourly. The
            // first fetch is deliberately after CONNECTED so it cannot delay
            // the user-visible connection.
            delay(INITIAL_REFRESH_DELAY)
            while (isActive) {
                val domains = domainFetcher.fetch()
                if (domains.isNotEmpty()) {
                    domainStore.setDomains(domains)
                    if (_proxyState.value == ProxyState.CONNECTED) {
                        _statusMessage.value = connectedMessage()
                    }
                }
                delay(REFRESH_INTERVAL)
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
        private const val TAG = "ProxyManager"
        private const val INITIAL_REFRESH_DELAY = 3_000L
        private const val CONFIG_RELOAD_DEBOUNCE_MS = 350L
        private val REFRESH_INTERVAL = 60 * 60 * 1000L

        private val _proxyState = MutableStateFlow(ProxyState.DISCONNECTED)
        val proxyState: StateFlow<ProxyState> = _proxyState.asStateFlow()

        private val _statusMessage = MutableStateFlow("Disconnected")
        val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

        fun resetState() {
            _proxyState.value = ProxyState.DISCONNECTED
            _statusMessage.value = "Disconnected"
        }
    }
}
