package com.haio.bypass.network.haiocloud

import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName

/**
 * Gson DTOs for `https://portal.haio.ir/api/v1/`.
 *
 * Every field that can be absent is nullable: Gson instantiates objects without
 * calling the Kotlin constructor, so Kotlin default values are not applied to
 * missing JSON fields. Call sites use the helper accessors below instead of
 * trusting non-null declarations.
 */

data class OtpRequestBody(
    @SerializedName("mobile") val mobile: String
)

data class OtpRequestResult(
    @SerializedName("sent") val sent: Boolean? = null,
    @SerializedName("expires_in") val expiresIn: Int? = null,
    @SerializedName("retry_after") val retryAfter: Int? = null,
    @SerializedName("provider") val provider: String? = null,
    @SerializedName("reason") val reason: String? = null,
    @SerializedName("raw") val raw: Any? = null
)

data class OtpLoginBody(
    @SerializedName("mobile") val mobile: String,
    @SerializedName("otp") val otp: String
)

data class RefreshBody(
    @SerializedName("refresh") val refresh: String
)

data class TokenPair(
    @SerializedName("access") val access: String? = null,
    @SerializedName("refresh") val refresh: String? = null
)

data class SessionResponse(
    @SerializedName("access") val access: String? = null,
    @SerializedName("refresh") val refresh: String? = null,
    @SerializedName("user") val user: PortalUser? = null
)

data class PortalUser(
    @SerializedName("id") val id: Long? = null,
    @SerializedName("username") val username: String? = null,
    @SerializedName("email") val email: String? = null,
    @SerializedName("first_name") val firstName: String? = null,
    @SerializedName("last_name") val lastName: String? = null,
    @SerializedName("role") val role: String? = null,
    @SerializedName("display_name") val displayName: String? = null,
    @SerializedName("is_verified") val isVerified: Boolean? = null,
    @SerializedName("onboarding_completed") val onboardingCompleted: Boolean? = null,
    @SerializedName("mobile") val mobile: String? = null,
    @SerializedName("shahkar_status") val shahkarStatus: Int? = null,
    // Read-only on the server; never persisted locally.
    @SerializedName("national_id") val nationalId: String? = null,
    @SerializedName("created_at") val createdAt: String? = null
) {
    val shahkarVerified: Boolean get() = shahkarStatus == 1
    val hasValidMobile: Boolean get() = PortalValidators.isValidMobile(mobile.orEmpty())
    val title: String
        get() = displayName?.takeIf { it.isNotBlank() }
            ?: listOfNotNull(firstName, lastName)
                .filter { it.isNotBlank() }
                .joinToString(" ")
                .takeIf { it.isNotBlank() }
            ?: mobile.orEmpty().takeIf { it.isNotBlank() }
            ?: username.orEmpty().takeIf { it.isNotBlank() }
            ?: "کاربر هایو"
}

data class ShahkarVerifyBody(
    @SerializedName("mobile") val mobile: String,
    @SerializedName("national_id") val nationalId: String
)

data class ShahkarVerifyResult(
    @SerializedName("verified") val verified: Boolean? = null,
    @SerializedName("shahkar_status") val shahkarStatus: Int? = null,
    @SerializedName("reason") val reason: String? = null,
    @SerializedName("api_call_status_code") val apiCallStatusCode: Int? = null,
    @SerializedName("identified_response_code") val identifiedResponseCode: String? = null,
    @SerializedName("user") val user: PortalUser? = null
)

data class PagedResponse<T>(
    @SerializedName("count") val count: Int? = null,
    @SerializedName("next") val next: String? = null,
    @SerializedName("previous") val previous: String? = null,
    @SerializedName("results") val results: List<T>? = null
) {
    fun items(): List<T> = results.orEmpty()
}

data class HaioGatePackage(
    @SerializedName("id") val id: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("slug") val slug: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("price") val price: Long? = null,
    @SerializedName("traffic_bytes") val trafficBytes: Long? = null,
    @SerializedName("included_users") val includedUsers: Int? = null,
    @SerializedName("is_free") val isFree: Boolean? = null,
    @SerializedName("status") val status: String? = null,
    @SerializedName("sort_order") val sortOrder: Int? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("updated_at") val updatedAt: String? = null
) {
    val displayName: String get() = name.orEmpty().ifBlank { slug.orEmpty().ifBlank { "پکیج هایو گیت" } }
    /** 1/2/5 for the multi-user packages; null means the backend is older. */
    val usersCount: Int? get() = includedUsers?.takeIf { it > 0 }
    val isFreePlan: Boolean get() = isFree == true || (price ?: 0L) <= 0L
}

data class HaioGateAccount(
    @SerializedName("id") val id: String? = null,
    @SerializedName("package") val packageId: String? = null,
    @SerializedName("package_name") val packageName: String? = null,
    @SerializedName("username") val username: String? = null,
    @SerializedName("total_traffic_bytes") val totalTrafficBytes: Long? = null,
    @SerializedName("usage_bytes") val usageBytes: Long? = null,
    @SerializedName("usage_display") val usageDisplay: String? = null,
    @SerializedName("traffic_is_over") val trafficIsOver: Boolean? = null,
    @SerializedName("expired_at") val expiredAt: String? = null,
    @SerializedName("status") val status: String? = null,
    @SerializedName("status_display") val statusDisplay: String? = null,
    @SerializedName("last_sync") val lastSync: String? = null,
    @SerializedName("is_legacy") val isLegacy: Boolean? = null,
    @SerializedName("pending_reserves_count") val pendingReservesCount: Int? = null,
    @SerializedName("included_users") val includedUsers: Int? = null,
    @SerializedName("is_legacy_unlimited") val isLegacyUnlimited: Boolean? = null,
    @SerializedName("active_members_count") val activeMembersCount: Int? = null,
    @SerializedName("available_slots") val availableSlots: Int? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("updated_at") val updatedAt: String? = null
) {
    val category: AccountCategory get() = AccountClassifier.categoryOf(this)
    /** Explicit backend flag; `included_users` may be absent on older builds. */
    val isLegacyUnlimitedAccount: Boolean get() = isLegacyUnlimited == true
    val hasMultiUserInfo: Boolean get() = includedUsers != null || isLegacyUnlimited != null
    val availableSlotsCount: Int
        get() = availableSlots ?: ((includedUsers ?: 1) - 1).coerceAtLeast(0)
}

data class PendingReserve(
    @SerializedName("id") val id: String? = null,
    @SerializedName("package") val packageId: String? = null,
    @SerializedName("package_name") val packageName: String? = null,
    @SerializedName("traffic_bytes") val trafficBytes: Long? = null,
    @SerializedName("included_users") val includedUsers: Int? = null,
    @SerializedName("created_at") val createdAt: String? = null
)

data class PortalMember(
    @SerializedName("id") val id: String? = null,
    @SerializedName("display_name") val displayName: String? = null,
    @SerializedName("username") val username: String? = null,
    @SerializedName("password") val password: String? = null,
    @SerializedName("trojan_url") val trojanUrl: String? = null,
    @SerializedName("status") val status: String? = null,
    @SerializedName("member_index") val memberIndex: Int? = null,
    @SerializedName("mobile") val mobile: String? = null,
    @SerializedName("email") val email: String? = null,
    @SerializedName("activated_at") val activatedAt: String? = null,
    @SerializedName("revoked_at") val revokedAt: String? = null
) {
    val isRevoked: Boolean get() = status == "revoked"
    val isActive: Boolean get() = status == "active"
    val name: String get() = displayName.orEmpty().ifBlank { username.orEmpty() }
}

data class HaioGateAccountDetail(
    @SerializedName("id") val id: String? = null,
    @SerializedName("package") val packageId: String? = null,
    @SerializedName("package_name") val packageName: String? = null,
    @SerializedName("username") val username: String? = null,
    @SerializedName("password") val password: String? = null,
    @SerializedName("total_traffic_bytes") val totalTrafficBytes: Long? = null,
    @SerializedName("usage_bytes") val usageBytes: Long? = null,
    @SerializedName("usage_display") val usageDisplay: String? = null,
    @SerializedName("traffic_is_over") val trafficIsOver: Boolean? = null,
    @SerializedName("expired_at") val expiredAt: String? = null,
    @SerializedName("status") val status: String? = null,
    @SerializedName("status_display") val statusDisplay: String? = null,
    @SerializedName("last_sync") val lastSync: String? = null,
    @SerializedName("trojan_url") val trojanUrl: String? = null,
    @SerializedName("meta_data_json") val metaDataJson: JsonObject? = null,
    @SerializedName("is_legacy") val isLegacy: Boolean? = null,
    @SerializedName("usage_percent") val usagePercent: Double? = null,
    @SerializedName("pending_reserves") val pendingReserves: List<PendingReserve>? = null,
    @SerializedName("projected_expired_at") val projectedExpiredAt: String? = null,
    @SerializedName("pending_reserves_count") val pendingReservesCount: Int? = null,
    @SerializedName("included_users") val includedUsers: Int? = null,
    @SerializedName("is_legacy_unlimited") val isLegacyUnlimited: Boolean? = null,
    @SerializedName("active_members_count") val activeMembersCount: Int? = null,
    @SerializedName("available_slots") val availableSlots: Int? = null,
    @SerializedName("members") val members: List<PortalMember>? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("updated_at") val updatedAt: String? = null
) {
    val category: AccountCategory
        get() = AccountClassifier.categoryOf(status, expiredAt, trafficIsOver == true)
    val isLegacyUnlimitedAccount: Boolean get() = isLegacyUnlimited == true
    val hasMultiUserInfo: Boolean get() = includedUsers != null || isLegacyUnlimited != null
    val reserves: List<PendingReserve> get() = pendingReserves.orEmpty()
    val reserveCount: Int get() = pendingReservesCount ?: reserves.size
    val memberList: List<PortalMember> get() = members.orEmpty()
    val activeMemberCount: Int
        get() = activeMembersCount ?: memberList.count { !it.isRevoked }
    val availableSlotCount: Int
        get() = availableSlots
            ?: ((includedUsers ?: 1) - 1 - memberList.count { !it.isRevoked }).coerceAtLeast(0)
    val progressPercent: Float
        get() {
            val explicit = usagePercent
            if (explicit != null) return explicit.toFloat().coerceIn(0f, 100f)
            val total = totalTrafficBytes ?: 0L
            if (total <= 0L) return 0f
            return ((usageBytes ?: 0L).toDouble() / total * 100.0).toFloat().coerceIn(0f, 100f)
        }
}

data class PackageIntentBody(
    @SerializedName("package_id") val packageId: String
)

data class RenewIntentBody(
    @SerializedName("package_id") val packageId: String?,
    @SerializedName("included_users") val includedUsers: Int? = null
)

data class PurchaseIntentResult(
    @SerializedName("plan_id") val planId: String? = null,
    @SerializedName("package_id") val packageId: String? = null,
    @SerializedName("is_free") val isFree: Boolean? = null,
    @SerializedName("detail") val detail: String? = null
)

data class BuyPlanBody(
    @SerializedName("plan_id") val planId: String,
    @SerializedName("organization_id") val organizationId: String? = null,
    @SerializedName("coupon") val coupon: String? = null,
    @SerializedName("use_credit") val useCredit: Boolean? = null
)

data class BuyPlanResult(
    @SerializedName("status") val status: String? = null,
    @SerializedName("payment_url") val paymentUrl: String? = null,
    @SerializedName("payment_id") val paymentId: String? = null,
    @SerializedName("invoice_id") val invoiceId: String? = null,
    @SerializedName("subscription_id") val subscriptionId: String? = null,
    @SerializedName("original_amount") val originalAmount: Long? = null,
    @SerializedName("discount_amount") val discountAmount: Long? = null,
    @SerializedName("discount_percent") val discountPercent: Int? = null,
    @SerializedName("credit_applied") val creditApplied: Long? = null,
    @SerializedName("paid_by") val paidBy: String? = null
)

data class InvoicePlanSnapshot(
    @SerializedName("id") val id: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("slug") val slug: String? = null
)

data class Invoice(
    @SerializedName("id") val id: String? = null,
    @SerializedName("organization") val organization: Long? = null,
    @SerializedName("plan") val plan: InvoicePlanSnapshot? = null,
    @SerializedName("subscription") val subscription: String? = null,
    @SerializedName("amount") val amount: Long? = null,
    @SerializedName("original_amount") val originalAmount: Long? = null,
    @SerializedName("discount_amount") val discountAmount: Long? = null,
    @SerializedName("discount_percent") val discountPercent: Int? = null,
    @SerializedName("status") val status: String? = null,
    @SerializedName("due_date") val dueDate: String? = null,
    @SerializedName("paid_at") val paidAt: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("created_at") val createdAt: String? = null
)

data class InvoicePayBody(
    @SerializedName("use_credit") val useCredit: Boolean? = null
)

data class InvoicePayResult(
    @SerializedName("payment_url") val paymentUrl: String? = null,
    @SerializedName("payment_id") val paymentId: String? = null,
    @SerializedName("invoice_id") val invoiceId: String? = null,
    @SerializedName("status") val status: String? = null,
    @SerializedName("paid_by") val paidBy: String? = null,
    @SerializedName("credit_applied") val creditApplied: Long? = null,
    @SerializedName("subscription_id") val subscriptionId: String? = null
)

data class Organization(
    @SerializedName("id") val id: Long? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("slug") val slug: String? = null,
    @SerializedName("owner") val owner: Long? = null,
    @SerializedName("owner_email") val ownerEmail: String? = null,
    @SerializedName("my_role") val myRole: String? = null,
    @SerializedName("created_at") val createdAt: String? = null
)

data class OrganizationOption(
    @SerializedName("id") val id: String? = null,
    @SerializedName("name") val name: String? = null
)

data class AddMemberBody(
    @SerializedName("display_name") val displayName: String,
    @SerializedName("mobile") val mobile: String? = null,
    @SerializedName("email") val email: String? = null
)
