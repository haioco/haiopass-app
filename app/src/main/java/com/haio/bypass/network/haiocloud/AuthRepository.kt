package com.haio.bypass.network.haiocloud

/**
 * OTP login, session restore, Shahkar verification, logout.
 */
class AuthRepository(
    private val client: PortalClient,
    private val store: PortalTokenStore,
    private val session: PortalSession,
    private val onLogout: () -> Unit = {}
) {

    suspend fun requestOtp(mobile: String): OtpRequestResult = portalCall {
        client.anonymousApi.requestOtp(OtpRequestBody(mobile = mobile))
    }

    suspend fun login(mobile: String, otp: String): PortalUser? {
        val result = portalCall {
            client.anonymousApi.loginWithOtp(OtpLoginBody(mobile = mobile, otp = otp))
        }
        val access = result.access
        val refresh = result.refresh
        if (access.isNullOrBlank() || refresh.isNullOrBlank()) {
            throw PortalException(PortalError.Unknown("پاسخ ورود ناقص است."))
        }
        session.onLoggedIn(result.user, access, refresh)
        return result.user
    }

    suspend fun me(): PortalUser {
        val user = portalCall { client.api.me() }
        session.onUserUpdated(user)
        return user
    }

    suspend fun verifyShahkar(mobile: String, nationalCode: String): ShahkarVerifyResult {
        val result = portalCall {
            client.api.verifyShahkar(
                ShahkarVerifyBody(mobile = mobile, nationalId = nationalCode)
            )
        }
        result.user?.let { session.onUserUpdated(it) }
        return result
    }

    /**
     * Validates a cached session once per process start. A network failure keeps
     * the cached `LoggedIn` state (offline tolerance); only a rejected session
     * clears the tokens.
     */
    suspend fun restoreSession() {
        if (!store.hasSession) return
        try {
            portalCall { client.api.me() }.let { session.onUserUpdated(it) }
        } catch (exception: PortalException) {
            when (exception.error) {
                is PortalError.Unauthorized -> session.expire()
                else -> Unit // keep the cached user; transient failures are retried on demand
            }
        }
    }

    fun logout() {
        session.logout()
        // The decrypted key/cert must never survive logout. Encrypted
        // identities stay in app-private storage so re-applying an account
        // does not require re-enrollment.
        onLogout()
    }
}
