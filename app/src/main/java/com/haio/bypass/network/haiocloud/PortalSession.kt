package com.haio.bypass.network.haiocloud

import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed class SessionState {
    /** Startup: the cached keyring is being read. Never shown as guest. */
    data object Restoring : SessionState()
    data class LoggedOut(val expired: Boolean = false) : SessionState()
    data class LoggedIn(val user: PortalUser?) : SessionState()
}

/**
 * Process-wide portal session. Guest mode is simply `LoggedOut` — there is no
 * guest token or separate identity.
 */
class PortalSession(private val store: PortalSessionStore) {

    private val gson = Gson()

    private val _state = MutableStateFlow<SessionState>(SessionState.Restoring)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    val currentUser: PortalUser?
        get() = (_state.value as? SessionState.LoggedIn)?.user

    val isLoggedIn: Boolean
        get() = _state.value is SessionState.LoggedIn

    /** Synchronous cache restore, then network validation happens off-thread. */
    fun restoreFromCache() {
        if (!store.hasSession) {
            _state.value = SessionState.LoggedOut()
            return
        }
        _state.value = SessionState.LoggedIn(readCachedUser())
    }

    fun onLoggedIn(user: PortalUser?, access: String, refresh: String) {
        val json = user?.let { runCatching { gson.toJson(cacheableUser(it)) }.getOrNull() }
        store.saveSession(access, refresh, json)
        _state.value = SessionState.LoggedIn(user)
    }

    fun onUserUpdated(user: PortalUser?) {
        if (user == null) return
        // Never resurrect a session that was logged out (or expired) while the
        // network call was in flight.
        if (!store.hasSession) return
        val json = runCatching { gson.toJson(cacheableUser(user)) }.getOrNull()
        store.updateCachedUser(json)
        _state.value = SessionState.LoggedIn(user)
    }

    fun logout() {
        store.clear()
        _state.value = SessionState.LoggedOut(expired = false)
    }

    /** Refresh failed: clear tokens and tell the UI to ask for login again. */
    fun expire() {
        store.clear()
        _state.value = SessionState.LoggedOut(expired = true)
    }

    fun accessToken(): String? = store.accessToken

    fun refreshToken(): String? = store.refreshToken

    /**
     * National code is read-only session data and must never be persisted —
     * not even inside the encrypted user cache.
     */
    private fun cacheableUser(user: PortalUser): PortalUser =
        if (user.nationalId == null) user else user.copy(nationalId = null)

    private fun readCachedUser(): PortalUser? {
        val json = store.cachedUserJson ?: return null
        return runCatching { gson.fromJson(json, PortalUser::class.java) }.getOrNull()
    }
}
