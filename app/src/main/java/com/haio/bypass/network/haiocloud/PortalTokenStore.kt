package com.haio.bypass.network.haiocloud

/**
 * Minimal token surface used by [PortalClient]. Kept as an interface so the
 * refresh authenticator can be unit-tested with an in-memory fake instead of
 * Android's EncryptedSharedPreferences.
 */
interface PortalTokenStore {
    val accessToken: String?
    val refreshToken: String?
    val hasSession: Boolean

    /** Persists a rotated pair atomically. */
    fun updateTokens(access: String, refresh: String)

    fun clear()
}
