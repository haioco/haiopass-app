package com.haio.bypass.proxy

import android.content.Context
import android.net.VpnService
import android.util.Log
import com.haio.bypass.config.AppConfig
import com.haio.bypass.config.ConfigManager
import com.haio.bypass.config.TrojanConfig
import com.haio.bypass.domain.DomainFetcher
import com.haio.bypass.domain.DomainStore
import com.haio.bypass.security.DeviceIdentity
import com.haio.bypass.security.DeviceIdentityStore
import com.haio.bypass.security.DeviceIdentityStore.MaterializedFiles
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
    private val singBoxManager = SingBoxManager(context, vpnService)
    private val configGenerator = XrayConfigGenerator()
    private val singBoxConfigGenerator = SingBoxConfigGenerator()
    private val deviceIdentityStore = DeviceIdentityStore(context)
    private var activeCore: ProxyCore = xrayManager
    private var activeIdentity: DeviceIdentity? = null
    private var activeMaterialized: MaterializedFiles? = null
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
            val lockedByCore = configManager.getConfig().core == AppConfig.CORE_SINGBOX
            val locked = lockedByCore || trojanConfig.isDeviceLocked
            activeCore = if (locked) singBoxManager else xrayManager
            activeIdentity = if (locked) {
                deviceIdentityStore.find(trojanConfig.deviceIdentityId)
                    ?: throw ProxyStartException("device identity missing")
            } else {
                null
            }
            val configJson = buildActiveConfig(
                socksPort = socksPort,
                trojanConfig = trojanConfig,
                bypassDomains = domainStore.getDomains(),
                directDomains = domainStore.getExcludedDomains(),
                udpAllow = domainStore.getUdpAllow()
            )
            Log.i(
                TAG,
                "routing config (${if (locked) "sing-box" else "xray"}): " +
                    "${domainStore.getDomainCount()} domains, " +
                    "${domainStore.getExcludedDomains().size} direct, " +
                    "${domainStore.getUdpAllow().size} udp allow entries"
            )

            // The plain TLS probe cannot prove mTLS accepts our client
            // certificate (the server deliberately allows cert-less
            // handshakes to reach camouflage). Skip it for locked configs;
            // sing-box readiness plus the first proxied request is the probe.
            if (!locked) {
                healthJob = scope.async { TunnelHealthChecker.check(trojanConfig) }
            }

            val coreStarted = activeCore.start(configJson, socksPort)
            if (!coreStarted) {
                throw ProxyStartException(
                    if (locked) "sing-box failed to start" else "xray failed to start"
                )
            }

            _statusMessage.value = "در حال اتصال تونل..."
            val tun2socksStarted = activeCore.startTun2Socks(tunFd, socksPort)
            if (!tun2socksStarted) {
                throw ProxyStartException("tun2socks failed to start")
            }

            if (!locked) {
                _statusMessage.value = "در حال بررسی اتصال..."
                if (healthJob?.await() != true) {
                    throw ProxyStartException("health check failed")
                }
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
                e.message?.contains("identity", ignoreCase = true) == true ->
                    "هویت دستگاه پیدا نشد؛ لطفاً دوباره ثبت دستگاه کنید"
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
            activeCore.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping active proxy core", e)
        }
        // Stop both cores defensively: a failed switch must not leave an
        // orphan process holding the SOCKS port.
        if (activeCore !== xrayManager) runCatching { xrayManager.stop() }
        if (activeCore !== singBoxManager) runCatching { singBoxManager.stop() }
        releaseIdentityFiles()

        activeCore = xrayManager
        activeTrojanConfig = null
        lastAppliedConfig = null
        _proxyState.value = ProxyState.DISCONNECTED
        _statusMessage.value = "قطع شد"

        configManager.updateConfig { it.copy(enabled = false) }
    }

    /**
     * Builds the config for the selected core. Locked configs materialize the
     * decrypted key/cert (0600) only for the lifetime of the tunnel.
     */
    private fun buildActiveConfig(
        socksPort: Int,
        trojanConfig: TrojanConfig,
        bypassDomains: List<String>,
        directDomains: List<String>,
        udpAllow: List<String>
    ): String {
        val identity = activeIdentity
        if (identity == null) {
            return configGenerator.generateConfig(
                socksPort = socksPort,
                trojanConfig = trojanConfig,
                bypassDomains = bypassDomains,
                directDomains = directDomains,
                udpAllow = udpAllow
            )
        }

        val files = activeMaterialized ?: try {
            deviceIdentityStore.materialize(identity)
        } catch (exception: Exception) {
            throw ProxyStartException("device identity unavailable: ${exception.message}")
        }.also { activeMaterialized = it }

        return singBoxConfigGenerator.generateConfig(
            socksPort = socksPort,
            trojanConfig = trojanConfig,
            clientCertificatePath = files.certFile.absolutePath,
            clientPrivateKeyPath = files.keyFile.absolutePath,
            bypassDomains = bypassDomains,
            directDomains = directDomains,
            udpAllow = udpAllow
        )
    }

    private fun releaseIdentityFiles() {
        activeIdentity = null
        activeMaterialized = null
        try {
            deviceIdentityStore.deleteMaterializedFiles()
        } catch (exception: Exception) {
            Log.w(TAG, "Could not delete materialized device key", exception)
        }
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
            val udpAllow = domainFetcher.fetchUdpAllow()
            if (udpAllow.isNotEmpty()) {
                domainStore.setUdpAllow(udpAllow)
            }
        }
    }

    /**
     * Applies bypass-list changes to a running tunnel by restarting only the
     * active proxy core. tun2socks and the TUN interface stay up, so the user
     * does not see a disconnect and domain changes apply immediately.
     */
    private fun startConfigObserver() {
        configReloadJob?.cancel()
        configReloadJob = scope.launch {
            combine(
                domainStore.domains,
                domainStore.excludedDomains,
                domainStore.udpAllow
            ) { domains, excluded, udpAllow -> Triple(domains, excluded, udpAllow) }
                .drop(1)
                .conflate()
                .collect { (domains, excluded, udpAllow) ->
                    // Coalesce bursts of edits before touching the process.
                    delay(CONFIG_RELOAD_DEBOUNCE_MS)

                    val routingConfig = activeTrojanConfig ?: return@collect
                    val socksPort = configManager.getConfig().socksPort
                    val newConfig = buildActiveConfig(
                        socksPort = socksPort,
                        trojanConfig = routingConfig,
                        bypassDomains = domains,
                        directDomains = excluded,
                        udpAllow = udpAllow
                    )
                    if (newConfig == lastAppliedConfig) return@collect

                    val previousConfig = lastAppliedConfig
                    _statusMessage.value = "در حال به‌روزرسانی مسیرها..."
                    val applied = activeCore.reload(newConfig, socksPort)

                    if (applied) {
                        lastAppliedConfig = newConfig
                        _statusMessage.value = connectedMessage()
                    } else {
                        Log.e(TAG, "Failed to apply routing config, rolling back")
                        val rolledBack = previousConfig != null &&
                                activeCore.reload(previousConfig, socksPort)
                        if (rolledBack) {
                            _statusMessage.value = connectedMessage()
                        } else {
                            activeCore.stop()
                            releaseIdentityFiles()
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
                val udpAllow = domainFetcher.fetchUdpAllow()
                if (udpAllow.isNotEmpty()) {
                    domainStore.setUdpAllow(udpAllow)
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
