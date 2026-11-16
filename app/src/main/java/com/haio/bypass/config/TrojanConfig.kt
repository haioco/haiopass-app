package com.haio.bypass.config

import kotlinx.serialization.Serializable

/**
 * Trojan credential.
 *
 * [deviceIdentityId] is set only for device-locked HaioGate credentials; it is
 * the SHA-256 leaf fingerprint used to look up the encrypted device identity.
 * Manual links and legacy HaioGate accounts leave it null and keep using Xray.
 */
@Serializable
data class TrojanConfig(
    val password: String,
    val server: String,
    val port: Int,
    val sni: String,
    val deviceIdentityId: String? = null
) {
    val isDeviceLocked: Boolean get() = !deviceIdentityId.isNullOrBlank()
}
