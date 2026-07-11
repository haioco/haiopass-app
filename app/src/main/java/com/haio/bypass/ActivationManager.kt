package com.haio.bypass

import android.util.Log
import com.haio.bypass.config.TrojanUrlParser
import com.haio.bypass.network.api.ApiClient
import com.haio.bypass.network.api.AutoActivateRequest

class ActivationManager(private val prefs: HaioPrefs) {

    suspend fun run(): ActivationResult {
        if (prefs.isActivated && prefs.trojanConfigUrl != null) {
            Log.i(TAG, "Already activated, skipping auto-activate")
            val parsed = TrojanUrlParser.parse(prefs.trojanConfigUrl!!)
            return ActivationResult.AlreadyActivated(prefs.trojanConfigUrl!!, parsed)
        }

        val deviceId = prefs.deviceId
        Log.i(TAG, "Running auto-activate for device=$deviceId")

        return try {
            val api = ApiClient.getApiService()
            val response = api.autoActivate(AutoActivateRequest(deviceId = deviceId))

            if (response.isSuccessful) {
                val body = response.body()!!
                val configUrl = body.trojanConfigUrl
                val sub = body.subscription

                prefs.userId = body.userId
                prefs.trojanConfigUrl = configUrl
                prefs.markActivated()
                prefs.subscriptionUuid = sub.uuid
                prefs.subscriptionTitle = sub.title
                prefs.subscriptionTrafficMb = sub.trafficTotalMb.toFloat()
                prefs.subscriptionTrafficUsedMb = sub.trafficUsageMb.toFloat()
                prefs.subscriptionExpiry = sub.expiredAt

                val parsed = TrojanUrlParser.parse(configUrl)
                Log.i(TAG, "Auto-activate success: uuid=${sub.uuid}, user_id=${body.userId}, status=${sub.status}")

                ActivationResult.Success(configUrl, parsed, body.userId, sub)
            } else {
                val errorBody = response.errorBody()?.string() ?: "Unknown error"
                Log.w(TAG, "Auto-activate failed: code=${response.code()}, body=$errorBody")

                if (response.code() == 400 && errorBody.contains("already activated")) {
                    prefs.markActivated()
                    ActivationResult.Error("This device has already activated the free plan.")
                } else {
                    ActivationResult.Error("Auto-activation failed (${response.code()}): $errorBody")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Auto-activate network error", e)
            ActivationResult.Error("Network error: ${e.message}")
        }
    }

    sealed class ActivationResult {
        data class Success(
            val configUrl: String,
            val parsedConfig: com.haio.bypass.config.TrojanConfig?,
            val userId: Int,
            val subscription: com.haio.bypass.network.api.Subscription
        ) : ActivationResult()

        data class AlreadyActivated(
            val configUrl: String,
            val parsedConfig: com.haio.bypass.config.TrojanConfig?
        ) : ActivationResult()

        data class Error(val message: String) : ActivationResult()
    }

    companion object {
        private const val TAG = "ActivationManager"
    }
}