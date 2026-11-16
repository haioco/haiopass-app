package com.haio.bypass.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Android Keystore AES-256-GCM key that wraps the software device key.
 *
 * The key is non-exportable and does not require user authentication (the VPN
 * may reconnect from the boot receiver). `setRandomizedEncryptionRequired`
 * guarantees a fresh IV per enrollment/rewrite.
 */
class DeviceKeyProtector {

    class KeyUnavailableException(message: String, cause: Throwable? = null) :
        Exception(message, cause)

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    fun wrappingKey(): SecretKey {
        try {
            val store = keyStore()
            (store.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

            val generator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE
            )
            generator.init(
                KeyGenParameterSpec.Builder(
                    ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setUserAuthenticationRequired(false)
                    .setRandomizedEncryptionRequired(true)
                    .build()
            )
            return generator.generateKey()
        } catch (exception: Exception) {
            throw KeyUnavailableException(
                "Android Keystore wrapping key is unavailable",
                exception
            )
        }
    }

    fun deleteWrappingKey() {
        runCatching {
            keyStore().deleteEntry(ALIAS)
        }
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val ALIAS = "haio_device_key_wrapping_v1"
    }
}
