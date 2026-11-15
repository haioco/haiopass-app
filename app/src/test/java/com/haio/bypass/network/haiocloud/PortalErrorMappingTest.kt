package com.haio.bypass.network.haiocloud

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class PortalErrorMappingTest {

    @Test
    fun `403 shahkar_required maps to the Shahkar gate`() {
        val error = PortalErrors.fromResponse(
            errorResponse(403, """{"detail":"Shahkar verification required","code":"shahkar_required"}""")
        )
        assertTrue(error is PortalError.ShahkarRequired)
    }

    @Test
    fun `400 mobile_required is explicit`() {
        val error = PortalErrors.fromResponse(
            errorResponse(400, """{"detail":"Mobile required","code":"mobile_required"}""")
        )
        assertTrue(error is PortalError.MobileRequired)
    }

    @Test
    fun `organization_required carries the choices`() {
        val error = PortalErrors.fromResponse(
            errorResponse(
                400,
                """{"code":"organization_required","organizations":[{"id":"2","name":"Acme"}]}"""
            )
        )
        assertTrue(error is PortalError.OrganizationRequired)
        val organizations = (error as PortalError.OrganizationRequired).organizations
        assertEquals(1, organizations.size)
        assertEquals("Acme", organizations.first().name)
    }

    @Test
    fun `429 uses retry_after from the body`() {
        val error = PortalErrors.fromResponse(
            errorResponse(429, """{"sent":false,"reason":"rate_limited","retry_after":42}""")
        )
        assertEquals(42, (error as PortalError.RateLimited).retryAfterSeconds)
    }

    @Test
    fun `429 falls back to the Retry-After header`() {
        val error = PortalErrors.fromResponse(
            errorResponse(429, """{"detail":"slow down"}""", mapOf("Retry-After" to "90"))
        )
        assertEquals(90, (error as PortalError.RateLimited).retryAfterSeconds)
    }

    @Test
    fun `field errors are exposed as validation errors`() {
        val error = PortalErrors.fromResponse(
            errorResponse(400, """{"mobile":["Enter a valid mobile."],"otp":["Expired."]}""")
        )
        assertTrue(error is PortalError.Validation)
        val validation = error as PortalError.Validation
        assertEquals("Enter a valid mobile.", validation.fieldErrors["mobile"])
        assertEquals("Expired.", validation.fieldErrors["otp"])
    }

    @Test
    fun `server errors are transient`() {
        val error = PortalErrors.fromResponse(errorResponse(503, """{"detail":"nope"}"""))
        assertTrue(error is PortalError.Server)
        assertEquals(503, (error as PortalError.Server).code)
    }

    private fun errorResponse(
        code: Int,
        body: String,
        headers: Map<String, String> = emptyMap()
    ): Response<Any> {
        val raw = okhttp3.Response.Builder()
            .code(code)
            .message("error")
            .protocol(Protocol.HTTP_1_1)
            .request(Request.Builder().url("https://portal.haio.ir/api/v1/test/").build())
            .apply { headers.forEach { (key, value) -> addHeader(key, value) } }
            .build()
        return Response.error(body.toResponseBody("application/json".toMediaType()), raw)
    }
}
