package com.haio.bypass.config

import kotlinx.serialization.Serializable

@Serializable
data class AppConfig(
    val enabled: Boolean = false,
    val trojanUrl: String = "",
    val trojanConfig: TrojanConfig? = null,
    val socksPort: Int = 10808,
    val cachedDomains: List<String> = emptyList(),
    val lastFetchTime: Long = 0,
    val autostart: Boolean = false
)

data class SubscriptionInfo(
    val title: String,
    val planName: String = "",
    val isPaid: Boolean = false,
    val trafficTotalMb: Float,
    val trafficUsedMb: Float,
    val trafficPercent: Float,
    val expiryDate: String
)
