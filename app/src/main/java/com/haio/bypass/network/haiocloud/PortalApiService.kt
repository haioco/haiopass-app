package com.haio.bypass.network.haiocloud

import com.google.gson.JsonObject
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit surface for `https://portal.haio.ir/api/v1/`.
 *
 * `billing/payments/verify/` is deliberately absent: it is the Zibal callback
 * and calling it from the app can mark a still-pending payment as failed.
 */
interface PortalApiService {

    // ── Auth ──────────────────────────────────────────────────────────────
    @POST("auth/otp/request/")
    suspend fun requestOtp(@Body body: OtpRequestBody): Response<OtpRequestResult>

    @POST("auth/otp/login/")
    suspend fun loginWithOtp(@Body body: OtpLoginBody): Response<SessionResponse>

    @POST("auth/refresh/")
    suspend fun refreshToken(@Body body: RefreshBody): Response<TokenPair>

    @GET("auth/me/")
    suspend fun me(): Response<PortalUser>

    @POST("auth/shahkar/verify/")
    suspend fun verifyShahkar(@Body body: ShahkarVerifyBody): Response<ShahkarVerifyResult>

    // ── HaioGate ──────────────────────────────────────────────────────────
    @GET("antisanction/packages/")
    suspend fun packages(
        @Query("page_size") pageSize: Int = 100
    ): Response<PagedResponse<HaioGatePackage>>

    @GET("antisanction/")
    suspend fun accounts(
        @Query("page_size") pageSize: Int = 100,
        @Query("page") page: Int? = null
    ): Response<PagedResponse<HaioGateAccount>>

    @GET("antisanction/{id}/")
    suspend fun account(@Path("id") id: String): Response<HaioGateAccountDetail>

    @GET("antisanction/{id}/quota/")
    suspend fun syncQuota(@Path("id") id: String): Response<HaioGateAccountDetail>

    @POST("antisanction/")
    suspend fun createPurchaseIntent(@Body body: PackageIntentBody): Response<PurchaseIntentResult>

    @POST("antisanction/{id}/renew/")
    suspend fun createRenewIntent(
        @Path("id") id: String,
        @Body body: RenewIntentBody
    ): Response<PurchaseIntentResult>

    // ── Members (multi-user plans) ────────────────────────────────────────
    @POST("antisanction/{id}/members/")
    suspend fun addMember(
        @Path("id") id: String,
        @Body body: AddMemberBody
    ): Response<JsonObject>

    @POST("antisanction/{id}/members/{memberId}/rotate/")
    suspend fun rotateMember(
        @Path("id") id: String,
        @Path("memberId") memberId: String
    ): Response<JsonObject>

    @DELETE("antisanction/{id}/members/{memberId}/")
    suspend fun revokeMember(
        @Path("id") id: String,
        @Path("memberId") memberId: String
    ): Response<JsonObject>

    // ── Billing ───────────────────────────────────────────────────────────
    @POST("billing/subscriptions/buy/")
    suspend fun buyPlan(@Body body: BuyPlanBody): Response<BuyPlanResult>

    @GET("billing/invoices/{id}/")
    suspend fun invoice(@Path("id") id: String): Response<Invoice>

    @GET("billing/invoices/")
    suspend fun invoices(
        @Query("page_size") pageSize: Int = 20,
        @Query("page") page: Int? = null
    ): Response<PagedResponse<Invoice>>

    @POST("billing/invoices/{id}/pay/")
    suspend fun payInvoice(
        @Path("id") id: String,
        @Body body: InvoicePayBody = InvoicePayBody()
    ): Response<InvoicePayResult>

    // ── Organizations ─────────────────────────────────────────────────────
    @GET("orgs/")
    suspend fun organizations(
        @Query("page_size") pageSize: Int = 100
    ): Response<PagedResponse<Organization>>
}
