package com.haio.bypass.security

import android.content.Context
import android.content.SharedPreferences
import android.system.Os
import android.system.OsConstants
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

/**
 * App-private storage for HaioGate device identities.
 *
 * Layout:
 *  - The private key is persisted only as an AES-GCM ciphertext + IV.
 *  - Ciphertext, certificate, CA chain, fingerprint, credential metadata and
 *    expiry live in EncryptedSharedPreferences (falling back to private
 *    SharedPreferences for the ciphertext if the encrypted store cannot be
 *    opened — the value is still Keystore-encrypted).
 *  - On connect, [materialize] decrypts the PKCS#8 key to
 *    `filesDir/haiogate/device.key` (0600) and writes `device.crt`.
 *    [deleteMaterializedFiles] removes both on disconnect/logout/reset.
 *
 * Android Keystore cannot export a private key, and sing-box 1.14.4 only reads
 * key material from a path, so decrypted files are an accepted limitation
 * (documented in PLAN-DEVICE-LOCK §12.3). A rooted device can read the key
 * while connected; mTLS here is not DRM.
 */
class DeviceIdentityStore(context: Context) {

    private val appContext = context.applicationContext
    private val gson = Gson()
    private val protector = DeviceKeyProtector()

    /** True when EncryptedSharedPreferences could not be opened. */
    private var fallbackStore = false

    private val prefs: SharedPreferences = openPrefs()

    private fun openPrefs(): SharedPreferences = try {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            appContext,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (exception: Exception) {
        Log.w(TAG, "Encrypted identity store unavailable; using a Keystore-encrypted blob", exception)
        fallbackStore = true
        runCatching { appContext.deleteSharedPreferences(FILE_NAME) }
        appContext.getSharedPreferences("${FILE_NAME}_fallback", Context.MODE_PRIVATE)
    }

    fun hasIdentity(): Boolean = readAll().isNotEmpty()

    fun find(fingerprint: String?): DeviceIdentity? {
        val value = fingerprint?.takeIf { it.isNotBlank() } ?: return null
        return readAll().values.firstOrNull { it.fingerprint.equals(value, ignoreCase = true) }
    }

    fun findForAccount(accountId: String?): DeviceIdentity? {
        val id = accountId?.takeIf { it.isNotBlank() } ?: return null
        return readAll().values
            .filter { it.accountId == id }
            .maxByOrNull { it.enrolledAt }
    }

    fun findForMember(accountId: String?, memberId: String?): DeviceIdentity? {
        val account = accountId?.takeIf { it.isNotBlank() } ?: return null
        return readAll().values
            .filter { it.accountId == account && it.memberId == memberId }
            .maxByOrNull { it.enrolledAt }
    }

    fun loadAll(): List<DeviceIdentity> = readAll().values.sortedByDescending { it.enrolledAt }

    fun save(identity: DeviceIdentity) {
        val all = readAll().toMutableMap()
        all[identity.id] = identity
        writeAll(all)
    }

    fun remove(id: String) {
        val all = readAll().toMutableMap()
        all.remove(id)
        writeAll(all)
    }

    /** Removes every identity belonging to an account (device reset/logout). */
    fun removeForAccount(accountId: String?) {
        val id = accountId?.takeIf { it.isNotBlank() } ?: return
        val all = readAll().toMutableMap()
        all.entries.removeAll { it.value.accountId == id }
        writeAll(all)
    }

    private fun writeAll(all: Map<String, DeviceIdentity>) {
        val json = gson.toJson(all)
        if (fallbackStore) {
            // The fallback SharedPreferences file is not encrypted by the
            // AndroidX library, so encrypt the whole JSON (including the
            // credential password) with the Keystore wrapping key. Do not
            // silently store a plaintext bearer credential.
            val encrypted = DeviceCrypto.encrypt(
                json.toByteArray(Charsets.UTF_8),
                protector.wrappingKey(),
                letProviderChooseIv = true
            )
            prefs.edit().putString(KEY_IDENTITIES, gson.toJson(encrypted)).commit()
        } else {
            prefs.edit().putString(KEY_IDENTITIES, json).commit()
        }
    }

    /** Full wipe of all identities and the Keystore wrapping key. */
    fun clear() {
        prefs.edit().clear().commit()
        protector.deleteWrappingKey()
        deleteMaterializedFiles()
    }

    /**
     * Decrypts the private key and writes the pair sing-box needs.
     * The caller must invoke [deleteMaterializedFiles] on disconnect.
     */
    fun materialize(identity: DeviceIdentity): MaterializedFiles {
        val wrappingKey = protector.wrappingKey()
        val pkcs8 = DeviceCrypto.decrypt(
            DeviceCrypto.EncryptedPayload(
                ciphertextBase64 = identity.encryptedPrivateKeyBase64,
                ivBase64 = identity.ivBase64
            ),
            wrappingKey
        )
        val dir = identityDir(appContext)
        if (!dir.exists() && !dir.mkdirs()) {
            throw IllegalStateException("cannot create device identity directory")
        }
        secureDirectory(dir)

        val keyFile = File(dir, KEY_FILE)
        val certFile = File(dir, CERT_FILE)
        keyFile.writeBytes(pkcs8)
        certFile.writeText(identity.certificatePem)
        secureFile(keyFile)
        secureFile(certFile)
        return MaterializedFiles(keyFile, certFile)
    }

    fun deleteMaterializedFiles() {
        deleteMaterialized(appContext)
    }

    private fun readAll(): Map<String, DeviceIdentity> {
        val raw = prefs.getString(KEY_IDENTITIES, null) ?: return emptyMap()
        return try {
            val json = if (fallbackStore) {
                val payload = gson.fromJson(raw, DeviceCrypto.EncryptedPayload::class.java)
                    ?: return emptyMap()
                String(
                    DeviceCrypto.decrypt(payload, protector.wrappingKey()),
                    Charsets.UTF_8
                )
            } else {
                raw
            }
            val type = object : TypeToken<Map<String, DeviceIdentity>>() {}.type
            gson.fromJson<Map<String, DeviceIdentity>>(json, type) ?: emptyMap()
        } catch (exception: Exception) {
            Log.w(TAG, "Identity cache is unreadable; ignoring it", exception)
            emptyMap()
        }
    }

    data class MaterializedFiles(val keyFile: File, val certFile: File)

    companion object {
        private const val TAG = "DeviceIdentityStore"
        private const val FILE_NAME = "haio_device_identity"
        private const val KEY_IDENTITIES = "identities"
        private const val KEY_FILE = "device.key"
        private const val CERT_FILE = "device.crt"

        fun identityDir(context: Context): File = File(context.filesDir, "haiogate")

        fun keyFile(context: Context): File = File(identityDir(context), KEY_FILE)

        fun certFile(context: Context): File = File(identityDir(context), CERT_FILE)

        /** Safe cleanup path usable from logout/service teardown. */
        fun deleteMaterialized(context: Context) {
            val dir = identityDir(context.applicationContext)
            runCatching { File(dir, KEY_FILE).delete() }
            runCatching { File(dir, CERT_FILE).delete() }
            runCatching {
                if (dir.isDirectory && dir.list().isNullOrEmpty()) dir.delete()
            }
        }

        private fun secureDirectory(dir: File) {
            runCatching {
                Os.chmod(dir.absolutePath, OsConstants.S_IRWXU)
            }
        }

        private fun secureFile(file: File) {
            runCatching {
                Os.chmod(file.absolutePath, OsConstants.S_IRUSR or OsConstants.S_IWUSR)
            }.onFailure {
                runCatching {
                    file.setReadable(false, false)
                    file.setWritable(false, false)
                    file.setReadable(true, true)
                    file.setWritable(true, true)
                }
            }
        }
    }
}
