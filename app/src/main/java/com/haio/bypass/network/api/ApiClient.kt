package com.haio.bypass.network.api

import com.haio.bypass.HaioPrefs
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    private const val BASE_URL = "https://haiobypass.haiocloud.com/"

    private var retrofit: Retrofit? = null
    private var accessToken: String? = null

    private val authInterceptor = Interceptor { chain ->
        val original = chain.request()
        val token = accessToken
        if (token.isNullOrBlank()) {
            chain.proceed(original)
        } else {
            val request = original.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
            chain.proceed(request)
        }
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    fun setAccessToken(token: String?) {
        accessToken = token
    }

    fun getApiService(): HaioApiService {
        return retrofit?.create(HaioApiService::class.java) ?: run {
            val newRetrofit = Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            retrofit = newRetrofit
            newRetrofit.create(HaioApiService::class.java)
        }
    }

    fun tryRefreshAccessToken(prefs: HaioPrefs): String? {
        val refreshToken = prefs.jwtRefreshToken ?: return null
        return runBlocking {
            try {
                val response = getApiService().refreshToken(RefreshRequest(refresh = refreshToken))
                if (response.isSuccessful) {
                    val newAccess = response.body()?.access
                    if (newAccess != null) {
                        prefs.jwtAccessToken = newAccess
                        setAccessToken(newAccess)
                    }
                    newAccess
                } else {
                    null
                }
            } catch (_: Exception) {
                null
            }
        }
    }
}