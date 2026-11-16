package com.haio.bypass.config

import kotlinx.serialization.Serializable

/**
 * Persisted connection configuration.
 *
 * [core] selects the local proxy core: `xray` for manual/legacy HaioGate
 * credentials, `singbox` for device-locked credentials that carry a client
 * certificate. [deviceIdentityId] is the leaf fingerprint of the active
 * device identity, if any. Both fields have defaults so older stored configs
 * decode unchanged and keep using Xray.
 */
@Serializable
data class AppConfig(
    val enabled: Boolean = false,
    val trojanUrl: String = "",
    val trojanConfig: TrojanConfig? = null,
    val socksPort: Int = 10808,
    val cachedDomains: List<String> = emptyList(),
    val lastFetchTime: Long = 0,
    val autostart: Boolean = false,
    val core: String = CORE_XRAY,
    val deviceIdentityId: String? = null
) {
    val isDeviceLocked: Boolean
        get() = core == CORE_SINGBOX && !deviceIdentityId.isNullOrBlank()

    companion object {
        const val CORE_XRAY = "xray"
        const val CORE_SINGBOX = "singbox"
    }
}

data class SubscriptionInfo(
    val title: String,
    val planName: String = "",
    val isPaid: Boolean = false,
    val trafficTotalMb: Float,
    val trafficUsedMb: Float,
    val trafficPercent: Float,
    val expiryDate: String
)
