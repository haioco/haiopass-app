package com.haio.bypass.network.haiocloud

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Encrypted storage for portal access/refresh tokens and the cached user.
 *
 * If the Android keystore cannot open the store (factory reset, restored
 * backup, key invalidation) the encrypted file is discarded and the session is
 * kept in memory only, so the user is asked to log in again instead of the app
 * crashing or silently writing plaintext credentials.
 */
class PortalSessionStore(context: Context) : PortalTokenStore {

    private val appContext = context.applicationContext

    private val prefs: SharedPreferences? = try {
        openEncryptedStore()
    } catch (firstFailure: Exception) {
        Log.w(TAG, "Encrypted session store unavailable; resetting it", firstFailure)
        deleteStoreFile()
        try {
            openEncryptedStore()
        } catch (secondFailure: Exception) {
            Log.w(TAG, "Encrypted session store still unavailable; memory-only session", secondFailure)
            null
        }
    }

    private var memoryAccess: String? = null
    private var memoryRefresh: String? = null
    private var memoryUserJson: String? = null

    val isPersistent: Boolean get() = prefs != null

    override var accessToken: String?
        get() = prefs?.getString(KEY_ACCESS, null) ?: memoryAccess
        set(value) {
            memoryAccess = value
            prefs?.edit()?.putString(KEY_ACCESS, value)?.apply()
        }

    override var refreshToken: String?
        get() = prefs?.getString(KEY_REFRESH, null) ?: memoryRefresh
        set(value) {
            memoryRefresh = value
            prefs?.edit()?.putString(KEY_REFRESH, value)?.apply()
        }

    var cachedUserJson: String?
        get() = prefs?.getString(KEY_USER, null) ?: memoryUserJson
        set(value) {
            memoryUserJson = value
            prefs?.edit()?.putString(KEY_USER, value)?.apply()
        }

    override val hasSession: Boolean
        get() = !accessToken.isNullOrBlank() && !refreshToken.isNullOrBlank()

    fun saveSession(access: String, refresh: String, cachedUserJson: String?) {
        memoryAccess = access
        memoryRefresh = refresh
        memoryUserJson = cachedUserJson
        val editor = prefs?.edit() ?: return
        editor.putString(KEY_ACCESS, access)
        editor.putString(KEY_REFRESH, refresh)
        if (cachedUserJson != null) editor.putString(KEY_USER, cachedUserJson)
        else editor.remove(KEY_USER)
        // commit(): the session must survive process death right after login.
        editor.commit()
    }

    /** Persists a rotated token pair; both tokens must move together. */
    override fun updateTokens(access: String, refresh: String) {
        memoryAccess = access
        memoryRefresh = refresh
        val editor = prefs?.edit() ?: return
        editor.putString(KEY_ACCESS, access)
        editor.putString(KEY_REFRESH, refresh)
        editor.commit()
    }

    fun updateCachedUser(cachedUserJson: String?) {
        memoryUserJson = cachedUserJson
        val editor = prefs?.edit() ?: return
        if (cachedUserJson != null) editor.putString(KEY_USER, cachedUserJson)
        else editor.remove(KEY_USER)
        editor.apply()
    }

    override fun clear() {
        memoryAccess = null
        memoryRefresh = null
        memoryUserJson = null
        // A revoked Keystore key or a corrupted EncryptedSharedPreferences file
        // can throw here. Logout must still complete in memory; never crash the
        // app while clearing a session.
        runCatching { prefs?.edit()?.clear()?.commit() }
            .onFailure { Log.w(TAG, "Could not clear encrypted session store", it) }
    }

    private fun openEncryptedStore(): SharedPreferences {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            appContext,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private fun deleteStoreFile() {
        runCatching {
            appContext.deleteSharedPreferences(FILE_NAME)
        }
    }

    companion object {
        private const val TAG = "PortalSessionStore"
        private const val FILE_NAME = "haio_portal_session"
        private const val KEY_ACCESS = "access_token"
        private const val KEY_REFRESH = "refresh_token"
        private const val KEY_USER = "cached_user"
    }
}
