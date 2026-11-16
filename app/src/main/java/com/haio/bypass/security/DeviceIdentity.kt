package com.haio.bypass.security

import com.haio.bypass.config.TrojanConfig
import com.haio.bypass.network.haiocloud.EnrollmentResult

/**
 * A device credential bound to one HaioGate slot.
 *
 * The private key is stored only as PKCS#8 bytes encrypted with an AES-256-GCM
 * key that lives in the Android Keystore. The certificate, CA chain and
 * credential metadata are public/mTLS material.
 *
 * [id] is local bookkeeping (`accountId:memberId|primary`). The server binding
 * value is [fingerprint] (SHA-256 of the leaf DER).
 */
data class DeviceIdentity(
    val id: String,
    val fingerprint: String,
    val certificatePem: String,
    val caChainPem: String,
    val encryptedPrivateKeyBase64: String,
    val ivBase64: String,
    val accountId: String?,
    val memberId: String?,
    val packageName: String?,
    val deviceName: String?,
    val server: String,
    val port: Int,
    val sni: String,
    val password: String,
    val username: String?,
    val certExpiresAt: String?,
    val accountExpiresAt: String?,
    /** Wall clock (epoch millis) of the last successful enrollment. */
    val enrolledAt: Long
) {
    /** Config consumed by [com.haio.bypass.proxy.ProxyManager] for sing-box. */
    fun toTrojanConfig(): TrojanConfig = TrojanConfig(
        password = password,
        server = server,
        port = port,
        sni = sni,
        deviceIdentityId = fingerprint
    )

    companion object {
        fun localizedId(accountId: String?, memberId: String?): String =
            "${accountId.orEmpty()}:${memberId ?: "primary"}"

        fun fromEnrollment(
            result: EnrollmentResult,
            encrypted: DeviceCrypto.EncryptedPayload,
            deviceName: String,
            now: Long = System.currentTimeMillis()
        ): DeviceIdentity {
            val credential = result.credential
                ?: throw IllegalArgumentException("missing credential")
            val certificate = result.certificate
                ?: throw IllegalArgumentException("missing certificate")
            val computedFingerprint = DeviceCrypto.certificateFingerprint(certificate)
            val serverFingerprint = result.deviceFingerprint
                ?.trim()
                ?.lowercase()
                ?.takeIf { it.isNotEmpty() }
            val fingerprint = serverFingerprint ?: computedFingerprint
            return DeviceIdentity(
                id = localizedId(result.accountId, result.memberId),
                fingerprint = fingerprint,
                certificatePem = certificate,
                caChainPem = result.caChain.orEmpty(),
                encryptedPrivateKeyBase64 = encrypted.ciphertextBase64,
                ivBase64 = encrypted.ivBase64,
                accountId = result.accountId,
                memberId = result.memberId,
                packageName = result.packageInfo?.name,
                deviceName = deviceName.take(100),
                server = credential.server.orEmpty(),
                port = credential.port ?: 443,
                sni = credential.sni?.takeIf { it.isNotBlank() }
                    ?: credential.server.orEmpty(),
                password = credential.password.orEmpty(),
                username = credential.username,
                certExpiresAt = result.certExpiresAt
                    ?: DeviceCrypto.certificateExpiryIso(certificate),
                accountExpiresAt = result.expiresAt,
                enrolledAt = now
            )
        }
    }
}
