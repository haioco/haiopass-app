package com.haio.bypass

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import java.util.UUID

class HaioPrefs(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("haio_prefs", Context.MODE_PRIVATE)

    private val androidId: String by lazy {
        val id = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        if (id.isNullOrBlank() || id == "9774d56d682e549c") {
            UUID.randomUUID().toString()
        } else {
            id
        }
    }

    var deviceId: String
        get() {
            val existing = prefs.getString(KEY_DEVICE_ID, null)
            if (existing != null) return existing
            prefs.edit().putString(KEY_DEVICE_ID, androidId).apply()
            return androidId
        }
        set(value) = prefs.edit().putString(KEY_DEVICE_ID, value).apply()

    val hasDeviceId: Boolean
        get() = prefs.getString(KEY_DEVICE_ID, null) != null

    var userId: Int
        get() = prefs.getInt(KEY_USER_ID, -1)
        set(value) = prefs.edit().putInt(KEY_USER_ID, value).apply()

    val hasUserId: Boolean
        get() = prefs.getInt(KEY_USER_ID, -1) != -1

    var trojanConfigUrl: String?
        get() = prefs.getString(KEY_TROJAN_CONFIG_URL, null)
        set(value) { prefs.edit().putString(KEY_TROJAN_CONFIG_URL, value).commit() }

    var subscriptionUuid: String?
        get() = prefs.getString(KEY_SUB_UUID, null)
        set(value) { prefs.edit().putString(KEY_SUB_UUID, value).commit() }

    var subscriptionTitle: String?
        get() = prefs.getString(KEY_SUB_TITLE, null)
        set(value) { prefs.edit().putString(KEY_SUB_TITLE, value).commit() }

    var subscriptionTrafficMb: Float
        get() = prefs.getFloat(KEY_SUB_TRAFFIC_TOTAL_MB, -1f)
        set(value) { prefs.edit().putFloat(KEY_SUB_TRAFFIC_TOTAL_MB, value).commit() }

    var subscriptionTrafficUsedMb: Float
        get() = prefs.getFloat(KEY_SUB_TRAFFIC_USED_MB, 0f)
        set(value) { prefs.edit().putFloat(KEY_SUB_TRAFFIC_USED_MB, value).commit() }

    var subscriptionExpiry: String?
        get() = prefs.getString(KEY_SUB_EXPIRY, null)
        set(value) { prefs.edit().putString(KEY_SUB_EXPIRY, value).commit() }

    var pendingPaymentPlanSlug: String?
        get() = prefs.getString(KEY_PENDING_PLAN_SLUG, null)
        set(value) { prefs.edit().putString(KEY_PENDING_PLAN_SLUG, value).apply() }

    var pendingPaymentId: Int
        get() = prefs.getInt(KEY_PENDING_PAYMENT_ID, -1)
        set(value) { prefs.edit().putInt(KEY_PENDING_PAYMENT_ID, value).apply() }

    fun clearPendingPayment() {
        prefs.edit()
            .remove(KEY_PENDING_PLAN_SLUG)
            .remove(KEY_PENDING_PAYMENT_ID)
            .apply()
    }

    var jwtAccessToken: String?
        get() = prefs.getString(KEY_JWT_ACCESS, null)
        set(value) = prefs.edit().putString(KEY_JWT_ACCESS, value).apply()

    var jwtRefreshToken: String?
        get() = prefs.getString(KEY_JWT_REFRESH, null)
        set(value) = prefs.edit().putString(KEY_JWT_REFRESH, value).apply()

    val isActivated: Boolean
        get() = prefs.getBoolean(KEY_FREE_ACTIVATED, false)

    fun markActivated() {
        prefs.edit().putBoolean(KEY_FREE_ACTIVATED, true).apply()
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_TROJAN_CONFIG_URL = "trojan_config_url"
        private const val KEY_JWT_ACCESS = "jwt_access_token"
        private const val KEY_JWT_REFRESH = "jwt_refresh_token"
        private const val KEY_FREE_ACTIVATED = "free_activated"
        private const val KEY_SUB_UUID = "subscription_uuid"
        private const val KEY_SUB_TITLE = "subscription_title"
        private const val KEY_SUB_TRAFFIC_TOTAL_MB = "subscription_traffic_total_mb"
        private const val KEY_SUB_TRAFFIC_USED_MB = "subscription_traffic_used_mb"
        private const val KEY_SUB_EXPIRY = "subscription_expiry"
        private const val KEY_PENDING_PLAN_SLUG = "pending_plan_slug"
        private const val KEY_PENDING_PAYMENT_ID = "pending_payment_id"
    }
}