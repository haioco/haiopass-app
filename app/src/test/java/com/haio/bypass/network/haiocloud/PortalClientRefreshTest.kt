package com.haio.bypass.network.haiocloud

import com.haio.bypass.network.haiocloud.PortalClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class PortalClientRefreshTest {

    private class FakeTokenStore(
        override var accessToken: String?,
        override var refreshToken: String?
    ) : PortalTokenStore {
        var cleared = false
        override val hasSession: Boolean
            get() = !accessToken.isNullOrBlank() && !refreshToken.isNullOrBlank()

        override fun updateTokens(access: String, refresh: String) {
            accessToken = access
            refreshToken = refresh
        }

        override fun clear() {
            cleared = true
            accessToken = null
            refreshToken = null
        }
    }

    private lateinit var server: MockWebServer
    private lateinit var store: FakeTokenStore
    private lateinit var client: PortalClient
    private var expiredEvents = 0

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        store = FakeTokenStore("old-access", "refresh-1")
        client = PortalClient(
            baseUrl = server.url("/api/v1/").toString(),
            store = store,
            onSessionExpired = { expiredEvents++ }
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `401 refreshes retries once and persists the rotated pair`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"detail":"expired"}"""))
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody("""{"access":"new-access","refresh":"refresh-2"}""")
        )
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody("""{"id":1,"mobile":"09120000000","shahkar_status":1}""")
        )

        val response = client.api.me()

        assertTrue(response.isSuccessful)
        assertEquals("new-access", store.accessToken)
        assertEquals("refresh-2", store.refreshToken)
        assertEquals(3, server.requestCount)
        assertEquals(0, expiredEvents)
    }

    @Test
    fun `refresh failure clears the session and emits expiry once`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"detail":"invalid"}"""))

        val result = client.refreshAccessToken()

        assertNull(result)
        assertTrue(store.cleared)
        assertFalse(store.hasSession)
        assertEquals(1, expiredEvents)
    }

    @Test
    fun `transient refresh failure keeps tokens`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503).setBody("""{"detail":"down"}"""))

        val result = client.refreshAccessToken()

        assertNull(result)
        assertFalse(store.cleared)
        assertEquals("old-access", store.accessToken)
        assertEquals(0, expiredEvents)
    }

    @Test
    fun `concurrent 401s trigger a single refresh request`() = runBlocking {
        val refreshCalls = AtomicInteger(0)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.endsWith("/auth/refresh/") -> {
                        Thread.sleep(150)
                        refreshCalls.incrementAndGet()
                        MockResponse().setResponseCode(200)
                            .setBody("""{"access":"new-access","refresh":"refresh-2"}""")
                    }
                    path.endsWith("/auth/me/") -> {
                        if (request.getHeader("Authorization") == "Bearer new-access") {
                            MockResponse().setResponseCode(200).setBody("""{"id":1}""")
                        } else {
                            MockResponse().setResponseCode(401).setBody("""{"detail":"expired"}""")
                        }
                    }
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }

        val responses = withContext(Dispatchers.IO) {
            listOf(
                async { client.api.me() },
                async { client.api.me() }
            ).awaitAll()
        }

        assertTrue(responses.all { it.isSuccessful })
        assertEquals(1, refreshCalls.get())
        assertEquals("new-access", store.accessToken)
    }

    @Test
    fun `refresh sends the stored refresh token`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody("""{"access":"a2","refresh":"r2"}""")
        )
        client.refreshAccessToken()
        val recorded = server.takeRequest()
        assertEquals("/api/v1/auth/refresh/", recorded.path)
        assertTrue(recorded.body.readUtf8().contains("\"refresh\":\"refresh-1\""))
    }
}
