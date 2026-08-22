package com.haio.bypass.network.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface HaioApiService {

    @GET("api/v1/plans/")
    suspend fun listPlans(): Response<PlanListResponse>

    @GET("api/v1/plans/{slug}/")
    suspend fun planDetail(@Path("slug") slug: String): Response<Plan>

    @POST("api/v1/auth/register/")
    suspend fun register(@Body body: RegisterRequest): Response<RegisterResponse>

    @POST("api/v1/auth/login/")
    suspend fun login(@Body body: LoginRequest): Response<LoginResponse>

    @POST("api/v1/auth/token/refresh/")
    suspend fun refreshToken(@Body body: RefreshRequest): Response<RefreshResponse>

    @GET("api/v1/user/me/")
    suspend fun userMe(): Response<UserMeResponse>

    @GET("api/v1/user/balance/")
    suspend fun userBalance(): Response<BalanceResponse>

    @POST("api/v1/subscriptions/auto-activate/")
    suspend fun autoActivate(@Body body: AutoActivateRequest): Response<AutoActivateResponse>

    @POST("api/v1/subscriptions/{uuid}/device-status/")
    suspend fun subscriptionDeviceStatus(
        @Path("uuid") uuid: String,
        @Body body: DeviceStatusRequest
    ): Response<Subscription>

    @GET("api/v1/subscriptions/")
    suspend fun listSubscriptions(): Response<SubscriptionListResponse>

    @GET("api/v1/subscriptions/{uuid}/")
    suspend fun subscriptionDetail(@Path("uuid") uuid: String): Response<Subscription>

    @POST("api/v1/subscriptions/create/")
    suspend fun createSubscription(@Body body: CreateSubscriptionRequest): Response<Subscription>

    @POST("api/v1/subscriptions/{uuid}/renew/")
    suspend fun renewSubscription(@Path("uuid") uuid: String): Response<Subscription>

    @POST("api/v1/payments/")
    suspend fun createPayment(@Body body: CreatePaymentRequest): Response<Payment>

    @POST("api/v1/payments/verify/")
    suspend fun verifyPayment(@Body body: VerifyPaymentRequest): Response<VerifyPaymentResponse>

    @GET("api/v1/payments/history/")
    suspend fun paymentHistory(): Response<PaymentHistoryResponse>
}