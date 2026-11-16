package com.haio.bypass.security

import com.haio.bypass.HaioPrefs
import com.haio.bypass.config.AppConfig
import com.haio.bypass.config.ConfigManager
import com.haio.bypass.network.haiocloud.EnrollmentResult

/**
 * Orchestrates the device-lock lifecycle: key generation, encrypted
 * persistence, active-config switching and cleanup.
 *
 * There is one software EC P-256 key per enrolled credential (primary or
 * member). Multiple accounts on one device are allowed and produce different
 * fingerprints, as required by the plan (§16).
 */
class DeviceKeyManager(
    private val store: DeviceIdentityStore,
    private val configManager: ConfigManager,
    private val prefs: HaioPrefs
) {

    fun generate(): DeviceCrypto.GeneratedKeyPair = DeviceCrypto.generateKeyPair()

    /**
     * Encrypts the private key with the Android Keystore wrapping key and
     * persists the certificate + credential metadata. Never persists a
     * plaintext PKCS#8 key.
     */
    fun persist(
        result: EnrollmentResult,
        generated: DeviceCrypto.GeneratedKeyPair,
        deviceName: String
    ): DeviceIdentity {
        val certificate = result.certificate
            ?.takeIf { it.contains("BEGIN CERTIFICATE") }
            ?: throw IllegalStateException("پاسخ ثبت دستگاه گواهی معتبر ندارد.")
        if (!DeviceCrypto.certificateMatchesPublicKey(certificate, generated.keyPair.public)) {
            throw IllegalStateException("گواهی دریافتی با کلید این دستگاه هم‌خوانی ندارد.")
        }
        val serverFingerprint = result.deviceFingerprint?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        if (serverFingerprint != null &&
            serverFingerprint != DeviceCrypto.certificateFingerprint(certificate)
        ) {
            throw IllegalStateException("اثر انگشت گواهی با پاسخ سرور هم‌خوانی ندارد.")
        }
        val pkcs8 = DeviceCrypto.privateKeyPkcs8(generated.keyPair.private)
        val encrypted = try {
            DeviceCrypto.encrypt(pkcs8, protectorKey(), letProviderChooseIv = true)
        } catch (exception: DeviceKeyProtector.KeyUnavailableException) {
            throw IllegalStateException(
                "ذخیره امن کلید روی این دستگاه ممکن نیست؛ دوباره تلاش کنید.",
                exception
            )
        }
        val identity = DeviceIdentity.fromEnrollment(
            result = result.copy(certificate = certificate),
            encrypted = encrypted,
            deviceName = deviceName
        )
        // EncryptedSharedPreferences writes asynchronously; a config applied
        // immediately after enrollment must still find its key on cold start.
        try {
            store.save(identity)
        } catch (exception: DeviceKeyProtector.KeyUnavailableException) {
            throw IllegalStateException(
                "ذخیره امن کلید روی این دستگاه ممکن نیست؛ دوباره تلاش کنید.",
                exception
            )
        }
        return identity
    }

    /** Makes a freshly enrolled identity the active HaioGate configuration. */
    fun apply(identity: DeviceIdentity) {
        configManager.updateConfig {
            it.copy(
                // The invite credential is password + client certificate.
                // Do not keep a standalone trojan URL as the active config:
                // it cannot connect without the certificate.
                trojanUrl = "",
                trojanConfig = identity.toTrojanConfig(),
                core = AppConfig.CORE_SINGBOX,
                deviceIdentityId = identity.fingerprint
            )
        }
        prefs.activeConfigSource = HaioPrefs.SOURCE_HAIOGATE
        prefs.haiogateAccountId = identity.accountId
        prefs.haiogateMemberId = identity.memberId
        prefs.deviceFingerprint = identity.fingerprint
    }

    fun persistAndApply(
        result: EnrollmentResult,
        generated: DeviceCrypto.GeneratedKeyPair,
        deviceName: String
    ): DeviceIdentity {
        val identity = persist(result, generated, deviceName)
        apply(identity)
        return identity
    }

    /**
     * Removes the local identity for an account and, if it is the active
     * device-locked config, clears that config (a revoked certificate cannot
     * connect). Manual/legacy configs are never touched.
     */
    fun forgetAccount(accountId: String?) {
        val id = accountId?.takeIf { it.isNotBlank() } ?: return
        store.removeForAccount(id)
        if (clearIfActive(id)) {
            store.deleteMaterializedFiles()
        }
    }

    /** Removes only the primary-slot identity (owner reset-device flow). */
    fun forgetPrimary(accountId: String?) {
        val id = accountId?.takeIf { it.isNotBlank() } ?: return
        val identity = store.findForMember(id, null) ?: return
        store.remove(identity.id)
        val activeFingerprint = prefs.deviceFingerprint
            ?: configManager.getConfig().deviceIdentityId
        if (activeFingerprint.equals(identity.fingerprint, ignoreCase = true) &&
            clearIfActive(id)
        ) {
            store.deleteMaterializedFiles()
        }
    }

    private fun clearIfActive(accountId: String): Boolean {
        val config = configManager.getConfig()
        val isActive = config.isDeviceLocked &&
            (prefs.haiogateAccountId == null || prefs.haiogateAccountId == accountId)
        if (isActive) {
            configManager.updateConfig {
                it.copy(
                    trojanUrl = "",
                    trojanConfig = null,
                    core = AppConfig.CORE_XRAY,
                    deviceIdentityId = null
                )
            }
            prefs.clearHaiogateBinding()
            prefs.activeConfigSource = HaioPrefs.SOURCE_MANUAL
        }
        return isActive
    }

    /** Deletes only the decrypted files; encrypted identities survive. */
    fun deleteMaterializedFiles() {
        store.deleteMaterializedFiles()
    }

    fun deleteAll() {
        store.clear()
    }

    private fun protectorKey() = DeviceKeyProtector().wrappingKey()
}
