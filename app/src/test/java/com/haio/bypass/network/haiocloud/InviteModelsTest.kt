package com.haio.bypass.network.haiocloud

import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class InviteModelsTest {

    private val gson = Gson()

    @Test
    fun `preview response parses the public shape`() {
        val preview = gson.fromJson(
            """
            {
              "status": "pending",
              "account_name_masked": "a***@example.com",
              "package_name": "HaioGate 100GB",
              "traffic_bytes": 107374182400,
              "expires_at": "2026-11-18T14:30:00Z",
              "member_index": 1,
              "slots_total": 2,
              "account_id": "acct-1",
              "member_id": "member-1"
            }
            """.trimIndent(),
            InvitePreview::class.java
        )
        assertEquals("pending", preview.status)
        assertEquals("HaioGate 100GB", preview.displayPackage)
        assertEquals(100L * 1024 * 1024 * 1024, preview.trafficBytes)
        assertEquals(2, preview.slotNumber)
        assertEquals(2, preview.slotTotal)
        assertEquals("member-1", preview.memberId)
    }

    @Test
    fun `enrollment response parses certificate credential and package`() {
        val result = gson.fromJson(
            """
            {
              "certificate": "-----BEGIN CERTIFICATE-----\nMIIB\n-----END CERTIFICATE-----\n",
              "ca_chain": "-----BEGIN CERTIFICATE-----\nMIIB\n-----END CERTIFICATE-----\n",
              "trojan_url": "trojan://p@tahrim.haiocloud.com:443?sni=tahrim.haiocloud.com",
              "credential": {
                "server": "tahrim.haiocloud.com",
                "port": 443,
                "sni": "tahrim.haiocloud.com",
                "password": "fresh-at-enroll",
                "username": "antisanction_1_m1"
              },
              "package": {"id": "p1", "name": "HaioGate 100GB", "traffic_bytes": 107374182400},
              "expires_at": "2026-12-15T14:30:00Z",
              "account_id": "acct-1",
              "member_id": "member-1",
              "device_fingerprint": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
              "cert_expires_at": "2028-11-14T14:30:00Z"
            }
            """.trimIndent(),
            EnrollmentResult::class.java
        )
        assertEquals("fresh-at-enroll", result.credential?.password)
        assertEquals(443, result.credential?.port)
        assertEquals("HaioGate 100GB", result.packageInfo?.name)
        assertEquals("acct-1", result.accountId)
        assertTrue(result.deviceFingerprint!!.matches(Regex("[0-9a-f]{64}")))
    }

    @Test
    fun `invite payload prefers the https invite url`() {
        val invite = gson.fromJson(
            """
            {"token":"hgi_x","invite_url":"https://my.haio.ir/join/hgi_x",
             "deep_link":"haio://join/hgi_x","expires_at":"2026-11-18T14:30:00Z",
             "member_index":1,"package_name":"P"}
            """.trimIndent(),
            InviteToken::class.java
        )
        assertEquals("https://my.haio.ir/join/hgi_x", invite.shareLink)
    }

    @Test
    fun `locked member never reports usable credential fields`() {
        val member = gson.fromJson(
            """
            {"id":"m1","display_name":"Ali","username":"antisanction_1_m1",
             "status":"pending","member_index":1,"device_lock":true,
             "device":{"name":"Pixel 8","platform":"android","last_seen_at":"2026-11-15T10:00:00Z",
                       "cert_expires_at":"2028-11-14T14:30:00Z","status":"active"}}
            """.trimIndent(),
            PortalMember::class.java
        )
        assertTrue(member.isDeviceLocked)
        assertTrue(member.isPending)
        assertEquals("Pixel 8", member.device?.displayName)
        assertEquals(null, member.password)
        assertEquals(null, member.trojanUrl)
    }

    @Test
    fun `member device accepts both name and device_name shapes`() {
        val nested = gson.fromJson(
            """{"name":"Pixel 8","platform":"android"}""",
            MemberDevice::class.java
        )
        val serializer = gson.fromJson(
            """{"device_name":"Pixel 9","platform":"android"}""",
            MemberDevice::class.java
        )
        assertEquals("Pixel 8", nested.displayName)
        assertEquals("Pixel 9", serializer.displayName)
    }

    @Test
    fun `409 invite_used maps to a typed enrollment error`() {
        val error = PortalErrors.fromResponse(
            errorResponse(409, """{"detail":"Invite already consumed.","code":"invite_used"}""")
        )
        assertTrue(error is PortalError.DeviceEnrollment)
        assertEquals("invite_used", (error as PortalError.DeviceEnrollment).code)
        assertTrue(enrollmentMessage(error).contains("قبلاً استفاده شده"))
    }

    @Test
    fun `410 invite_expired and device_already_bound map with Persian copy`() {
        val expired = PortalErrors.fromResponse(
            errorResponse(410, """{"detail":"expired","code":"invite_expired"}""")
        )
        assertTrue(expired is PortalError.DeviceEnrollment)
        assertTrue(enrollmentMessage(expired).contains("منقضی"))

        val bound = PortalErrors.fromResponse(
            errorResponse(409, """{"detail":"bound","code":"device_already_bound"}""")
        )
        assertTrue(enrollmentMessage(bound).contains("دستگاه دیگری"))
    }

    @Test
    fun `unrelated status codes keep the generic mapping`() {
        val error = PortalErrors.fromResponse(errorResponse(500, """{"detail":"boom"}"""))
        assertTrue(error is PortalError.Server)
        assertFalse(error is PortalError.DeviceEnrollment)
    }

    private fun errorResponse(code: Int, body: String): Response<Any> {
        val raw = okhttp3.Response.Builder()
            .code(code)
            .message("error")
            .protocol(Protocol.HTTP_1_1)
            .request(Request.Builder().url("https://portal.haio.ir/api/v1/antisanction/invites/x/").build())
            .build()
        return Response.error(body.toResponseBody("application/json".toMediaType()), raw)
    }
}
