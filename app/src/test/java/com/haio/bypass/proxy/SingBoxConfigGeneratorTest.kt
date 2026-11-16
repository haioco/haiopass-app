package com.haio.bypass.proxy

import com.google.gson.JsonParser
import com.haio.bypass.config.TrojanConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SingBoxConfigGeneratorTest {

    private val generator = SingBoxConfigGenerator()

    private fun generate(
        direct: List<String> = listOf("direct.example"),
        bypass: List<String> = listOf("blocked.example", "domain:already.example"),
        udp: List<String> = listOf("*:53", "1.2.3.0/24:2000-2010")
    ): String = generator.generateConfig(
        socksPort = 10808,
        trojanConfig = TrojanConfig(
            password = "secret-password",
            server = "tahrim.haiocloud.com",
            port = 443,
            sni = "tahrim.haiocloud.com",
            deviceIdentityId = "a".repeat(64)
        ),
        clientCertificatePath = "/data/user/0/com.haio.bypass/files/haiogate/device.crt",
        clientPrivateKeyPath = "/data/user/0/com.haio.bypass/files/haiogate/device.key",
        bypassDomains = bypass,
        directDomains = direct,
        udpAllow = udp
    )

    @Test
    fun `trojan outbound carries utls chrome and client certificate`() {
        val root = JsonParser.parseString(generate()).asJsonObject
        val outbound = root.getAsJsonArray("outbounds")[0].asJsonObject
        assertEquals("trojan", outbound.get("type").asString)
        assertEquals("tahrim.haiocloud.com", outbound.get("server").asString)
        assertEquals(443, outbound.get("server_port").asInt)
        assertEquals("secret-password", outbound.get("password").asString)

        val tls = outbound.getAsJsonObject("tls")
        assertTrue(tls.get("enabled").asBoolean)
        assertEquals("tahrim.haiocloud.com", tls.get("server_name").asString)
        assertEquals("chrome", tls.getAsJsonObject("utls").get("fingerprint").asString)
        assertEquals(
            "/data/user/0/com.haio.bypass/files/haiogate/device.crt",
            tls.get("client_certificate_path").asString
        )
        assertEquals(
            "/data/user/0/com.haio.bypass/files/haiogate/device.key",
            tls.get("client_key_path").asString
        )
    }

    @Test
    fun `socks inbound listens only on loopback`() {
        val root = JsonParser.parseString(generate()).asJsonObject
        val inbound = root.getAsJsonArray("inbounds")[0].asJsonObject
        assertEquals("socks", inbound.get("type").asString)
        assertEquals("127.0.0.1", inbound.get("listen").asString)
        assertEquals(10808, inbound.get("listen_port").asInt)
        // The legacy inbound `sniff` field was removed in sing-box 1.13;
        // sniffing is a route action now.
        assertFalse(inbound.has("sniff"))
    }

    @Test
    fun `routing mirrors direct exceptions quic block and udp allow`() {
        val root = JsonParser.parseString(generate()).asJsonObject
        val route = root.getAsJsonObject("route")
        assertEquals("direct", route.get("final").asString)
        val rules = route.getAsJsonArray("rules").map { it.asJsonObject }
        val raw = rules.joinToString("\n")

        assertTrue(raw.contains("direct.example"))
        assertTrue(raw.contains("blocked.example"))
        assertTrue(raw.contains("already.example"))
        // QUIC block exists.
        assertTrue(
            rules.any { rule ->
                rule.get("network")?.asString == "udp" &&
                    rule.get("port")?.asString == "443" &&
                    rule.get("action").asString == "reject"
            }
        )
        // UDP allow entries become proxy rules with ip_cidr/port(_range).
        assertTrue(
            rules.any { rule ->
                rule.get("action").asString == "route" &&
                    rule.get("outbound").asString == "proxy" &&
                    rule.get("port_range")?.asString == "2000:2010"
            }
        )
        assertTrue(
            rules.any { rule ->
                rule.get("action").asString == "route" &&
                    rule.get("outbound").asString == "proxy" &&
                    rule.get("port")?.asString == "53"
            }
        )
        assertTrue(rules.any { it.get("action")?.asString == "sniff" })
        // Everything else (direct/fake-IP) appears after the QUIC block.
        assertTrue(raw.contains("198.18.0.0/15"))
    }

    @Test
    fun `direct domains win over the bypass list`() {
        val config = generate(
            direct = listOf("shared.example"),
            bypass = listOf("shared.example")
        )
        val rules = JsonParser.parseString(config).asJsonObject
            .getAsJsonObject("route")
            .getAsJsonArray("rules")
            .map { it.asJsonObject }
        val sharedProxy = rules.any { rule ->
            rule.get("outbound")?.asString == "proxy" &&
                (rule.get("domain_suffix") as? com.google.gson.JsonArray)
                    ?.any { it.asString == "shared.example" } == true
        }
        val sharedDirect = rules.any { rule ->
            rule.get("outbound")?.asString == "direct" &&
                (rule.get("domain_suffix") as? com.google.gson.JsonArray)
                    ?.any { it.asString == "shared.example" } == true
        }
        assertTrue(sharedDirect)
        assertFalse(sharedProxy)
    }

    @Test
    fun `empty lists still produce a valid minimal config`() {
        val config = generate(direct = emptyList(), bypass = emptyList(), udp = emptyList())
        val root = JsonParser.parseString(config).asJsonObject
        assertEquals(2, root.getAsJsonArray("outbounds").size())
        assertEquals("direct", root.getAsJsonObject("route").get("final").asString)
    }
}
