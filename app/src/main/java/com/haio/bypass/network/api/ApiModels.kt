package com.haio.bypass.network.api

import com.google.gson.annotations.SerializedName

data class Plan(
    val id: Int,
    val slug: String,
    val name: String,
    val description: String? = null,
    @SerializedName("traffic_bytes") val trafficBytes: Long,
    @SerializedName("traffic_gb") val trafficGb: Double,
    @SerializedName("traffic_mb") val trafficMb: Double,
    @SerializedName("price_toman") val priceToman: Int,
    @SerializedName("duration_days") val durationDays: Int,
    // @SerializedName("bazaar_sku") val bazaarSku: String? = null,
    val features: Map<String, String>? = null
)

data class PlanListResponse(
    val results: List<Plan>,
    val count: Int,
    val next: String? = null,
    val previous: String? = null
)

data class Subscription(
    val id: Int,
    val uuid: String,
    @SerializedName("plan_detail") val planDetail: Plan? = null,
    val plan: String? = null,
    val status: String,
    val title: String,
    val username: String,
    val password: String,
    @SerializedName("device_id") val deviceId: String? = null,
    @SerializedName("traffic_usage_percent") val trafficUsagePercent: Double,
    @SerializedName("traffic_total_mb") val trafficTotalMb: Double,
    @SerializedName("traffic_usage_mb") val trafficUsageMb: Double,
    @SerializedName("total_traffic_byte") val totalTrafficByte: Long,
    @SerializedName("total_traffic_usage_byte") val totalTrafficUsageByte: Long,
    @SerializedName("traffic_is_over") val trafficIsOver: Boolean,
    @SerializedName("expired_at") val expiredAt: String?,
    @SerializedName("activated_at") val activatedAt: String?,
    @SerializedName("created_at") val createdAt: String?,
    @SerializedName("trojan_config_url") val trojanConfigUrl: String?
)

data class SubscriptionListResponse(
    val results: List<Subscription>,  // DRF paginated response
    val count: Int
)

data class AutoActivateRequest(
    @SerializedName("device_id") val deviceId: String
)

data class DeviceStatusRequest(
    @SerializedName("device_id") val deviceId: String
)

data class AutoActivateResponse(
    val subscription: Subscription,
    @SerializedName("trojan_config_url") val trojanConfigUrl: String,
    @SerializedName("user_id") val userId: Int
)

data class RegisterRequest(
    val username: String,
    val email: String? = null,
    val password: String,
    val mobile: String? = null
)

data class UserProfile(
    val balance: Int,
    val mobile: String? = null
)

data class RegisterUser(
    val id: Int,
    val username: String,
    val email: String? = null,
    val profile: UserProfile? = null
)

data class RegisterResponse(
    val user: RegisterUser,
    val tokens: TokenPair
)

data class TokenPair(
    val access: String,
    val refresh: String
)

data class LoginRequest(
    val username: String,
    val password: String
)

data class LoginResponse(
    val access: String,
    val refresh: String,
    val user: LoginUser
)

data class LoginUser(
    val id: Int,
    val username: String,
    val balance: Int
)

data class RefreshRequest(
    val refresh: String
)

data class RefreshResponse(
    val access: String
)

data class UserMeResponse(
    val id: Int,
    val username: String,
    val email: String? = null,
    val profile: UserProfile? = null
)

data class BalanceResponse(
    val balance: Int,
    val mobile: String? = null
)

data class CreateSubscriptionRequest(
    val plan: String,
    val title: String,
    @SerializedName("device_id") val deviceId: String? = null
)

data class CreatePaymentRequest(
    val plan: String
)

data class Payment(
    val id: Int,
    @SerializedName("plan_detail") val planDetail: Plan? = null,
    @SerializedName("amount_toman") val amountToman: Int,
    val gateway: String,
    val status: String,
    @SerializedName("purchase_token") val purchaseToken: String? = null,
    @SerializedName("product_id") val productId: String? = null,
    @SerializedName("transaction_ref") val transactionRef: String? = null,
    @SerializedName("verified_at") val verifiedAt: String? = null,
    @SerializedName("created_at") val createdAt: String?
)

data class VerifyPaymentRequest(
    @SerializedName("purchase_token") val purchaseToken: String,
    @SerializedName("product_id") val productId: String,
    @SerializedName("payment_id") val paymentId: Int
)

data class VerifyPaymentResponse(
    val payment: Payment,
    val subscription: Subscription
)

data class PaymentHistoryResponse(
    val data: List<Payment>,
    @SerializedName("total_items") val totalItems: Int
)

data class DetailResponse(
    val detail: String
)

fun Subscription.toSubscriptionInfo() = com.haio.bypass.config.SubscriptionInfo(
    title = title,
    planName = planDetail?.name ?: "",
    isPaid = (planDetail?.priceToman ?: 0) > 0,
    trafficTotalMb = trafficTotalMb.toFloat(),
    trafficUsedMb = trafficUsageMb.toFloat(),
    trafficPercent = trafficUsagePercent.toFloat(),
    expiryDate = expiredAt ?: ""
)