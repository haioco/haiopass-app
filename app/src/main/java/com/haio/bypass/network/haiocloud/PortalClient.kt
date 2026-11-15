package com.haio.bypass.network.haiocloud

import com.haio.bypass.BuildConfig
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.Route
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * OkHttp/Retrofit factory for the portal API.
 *
 * - `Authorization: Bearer <access>` is added by an interceptor.
 * - 401 responses trigger one single-flight refresh (Mutex) that persists both
 *   rotated tokens before the original request is retried once.
 * - Refresh uses a separate client without the authenticator to avoid recursion.
 * - Request/response bodies are never logged: auth, Shahkar and billing payloads
 *   contain secrets.
 */
class PortalClient(
    private val baseUrl: String,
    private val store: PortalTokenStore,
    private val onSessionExpired: () -> Unit
) {

    private val refreshMutex = Mutex()

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BASIC
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
        redactHeader("Authorization")
        redactHeader("Cookie")
    }

    private val authInterceptor = Interceptor { chain ->
        val original = chain.request()
        val token = store.accessToken
        if (token.isNullOrBlank() || original.header("Authorization") != null) {
            chain.proceed(original)
        } else {
            chain.proceed(
                original.newBuilder()
                    .header("Authorization", "Bearer $token")
                    .build()
            )
        }
    }

    private val tokenAuthenticator = Authenticator { _: Route?, response: Response ->
        when {
            response.request.url.encodedPath.contains("/auth/refresh/") -> null
            responseCount(response) >= 2 -> null
            else -> {
                val staleAccess = response.request.header("Authorization")
                    ?.removePrefix("Bearer ")
                    ?.trim()
                val newAccess = runBlocking { refreshAccessToken(staleAccess) }
                if (newAccess == null) {
                    null
                } else {
                    response.request.newBuilder()
                        .header("Authorization", "Bearer $newAccess")
                        .build()
                }
            }
        }
    }

    private fun baseClientBuilder(): OkHttpClient.Builder = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .addInterceptor(loggingInterceptor)

    private val authenticatedClient: OkHttpClient by lazy {
        baseClientBuilder()
            .addInterceptor(authInterceptor)
            .authenticator(tokenAuthenticator)
            .build()
    }

    private val anonymousClient: OkHttpClient by lazy {
        baseClientBuilder().build()
    }

    private fun retrofit(client: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    /** Authenticated API. */
    val api: PortalApiService by lazy { retrofit(authenticatedClient).create(PortalApiService::class.java) }

    /** OTP request/login/refresh: no bearer token, no authenticator. */
    val anonymousApi: PortalApiService by lazy {
        retrofit(anonymousClient).create(PortalApiService::class.java)
    }

    /**
     * Single-flight refresh. Returns the new access token, or null when the
     * session cannot be refreshed (tokens cleared, UI must ask for login).
     *
     * [staleAccess] is the access token that produced the 401. If another
     * thread has already rotated the token while we waited for the mutex, the
     * current token is returned without a second refresh request.
     */
    suspend fun refreshAccessToken(staleAccess: String? = null): String? = refreshMutex.withLock {
        val currentAccess = store.accessToken
        if (!staleAccess.isNullOrBlank() &&
            !currentAccess.isNullOrBlank() &&
            currentAccess != staleAccess
        ) {
            return@withLock currentAccess
        }

        val refresh = store.refreshToken
        if (refresh.isNullOrBlank()) {
            onSessionExpired()
            return@withLock null
        }
        val response = try {
            anonymousApi.refreshToken(RefreshBody(refresh))
        } catch (_: Exception) {
            // Transient network failure: keep tokens so a later retry can work.
            return@withLock null
        }
        val body = response.body()
        if (response.isSuccessful && !body?.access.isNullOrBlank()) {
            store.updateTokens(
                access = body!!.access!!,
                refresh = body.refresh?.takeIf { it.isNotBlank() } ?: refresh
            )
            return@withLock body.access
        }
        if (response.code() == 400 || response.code() == 401 || response.code() == 403) {
            store.clear()
            onSessionExpired()
        }
        null
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }
}
