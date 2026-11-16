package com.haio.bypass.network.haiocloud

import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Request-level contract for the invite/enrollment endpoints: paths, method,
 * anonymous vs bearer client, and the public_key payload (never a password or
 * a `csr` when Android uses SPKI).
 */
class InviteApiContractTest {

    private class FakeTokenStore : PortalTokenStore {
        override var accessToken: String? = "owner-access"
        override var refreshToken: String? = "owner-refresh"
        override val hasSession: Boolean = true
        override fun updateTokens(access: String, refresh: String) {
            accessToken = access
            refreshToken = refresh
        }
        override fun clear() {
            accessToken = null
            refreshToken = null
        }
    }

    private lateinit var server: MockWebServer
    private lateinit var client: PortalClient
    private lateinit var repository: InviteRepository

    private val token = "hgi_" + "a".repeat(43)

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = PortalClient(
            baseUrl = server.url("/api/v1/").toString(),
            store = FakeTokenStore(),
            onSessionExpired = {}
        )
        repository = InviteRepository(client)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `preview is anonymous and never sends the bearer token`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"status":"pending","account_name_masked":"a***@example.com",
                   "package_name":"P","traffic_bytes":1,"expires_at":"2030-01-01T00:00:00Z",
                   "member_index":1,"slots_total":2}"""
            )
        )

        val preview = repository.preview(token)

        assertEquals("P", preview.packageName)
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/api/v1/antisanction/invites/$token/", request.path)
        assertNull(request.getHeader("Authorization"))
    }

    @Test
    fun `invalid token is rejected before any network call`() = runBlocking {
        try {
            repository.preview("hgi_short")
            throw AssertionError("expected failure")
        } catch (exception: PortalException) {
            assertEquals("invite_not_found", (exception.error as PortalError.DeviceEnrollment).code)
        }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `enroll sends exactly one SPKI public key and no password`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"certificate":"-----BEGIN CERTIFICATE-----\nAA==\n-----END CERTIFICATE-----",
                   "ca_chain":"","trojan_url":"trojan://x@h:443",
                   "credential":{"server":"h","port":443,"sni":"h","password":"p","username":"u"},
                   "package":{"id":"p","name":"P","traffic_bytes":1},
                   "expires_at":"2030-01-01T00:00:00Z","account_id":"a","member_id":null,
                   "device_fingerprint":"f","cert_expires_at":"2031-01-01T00:00:00Z"}"""
            )
        )

        repository.enroll(
            token = token,
            publicKeyPem = "-----BEGIN PUBLIC KEY-----\nAA==\n-----END PUBLIC KEY-----",
            deviceName = "Pixel 8",
            appVersion = "4.0.0",
            requestId = "0b8f8b8e-1d5b-4a1a-9f5d-2f4c1b0a9e11"
        )

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/antisanction/invites/$token/enroll/", request.path)
        assertNull(request.getHeader("Authorization"))
        val body = JsonParser.parseString(request.body.readUtf8()).asJsonObject
        assertTrue(body.has("public_key"))
        assertFalse(body.has("csr"))
        assertFalse(body.has("password"))
        assertEquals("android", body.get("platform").asString)
        assertEquals("Pixel 8", body.get("device_name").asString)
        assertEquals("4.0.0", body.get("app_version").asString)
        assertEquals(
            "0b8f8b8e-1d5b-4a1a-9f5d-2f4c1b0a9e11",
            body.get("request_id").asString
        )
    }

    @Test
    fun `member invite uses the authenticated client`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(201).setBody(
                """{"token":"hgi_x","invite_url":"https://my.haio.ir/join/hgi_x",
                   "deep_link":"haio://join/hgi_x","expires_at":"2030-01-01T00:00:00Z",
                   "member_id":"m","member_index":1,"package_name":"P","account_id":"a"}"""
            )
        )

        repository.createMemberInvite("acct", "member")

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/antisanction/acct/members/member/invite/", request.path)
        assertEquals("Bearer owner-access", request.getHeader("Authorization"))
    }

    @Test
    fun `primary reset posts an optional reason`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody("""{"reset":true,"devices_revoked":1}""")
        )

        val result = repository.resetPrimaryDevice("acct", reason = "lost phone")

        assertEquals(1, result.devicesRevoked)
        val request = server.takeRequest()
        assertEquals("/api/v1/antisanction/acct/primary/reset-device/", request.path)
        assertEquals(
            "lost phone",
            JsonParser.parseString(request.body.readUtf8())
                .asJsonObject.get("reason").asString
        )
    }
}
