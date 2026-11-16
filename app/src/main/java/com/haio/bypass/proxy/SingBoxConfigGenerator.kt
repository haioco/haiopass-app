package com.haio.bypass.proxy

import com.haio.bypass.config.TrojanConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * sing-box 1.14.x configuration for **device-locked HaioGate only**.
 *
 * Xray still generates manual/legacy configs. This generator mirrors the Xray
 * routing semantics as far as the sing-box route engine allows:
 *
 *  - direct-domain exceptions (highest priority, before the QUIC block);
 *  - explicit UDP allow entries (`host:ports`);
 *  - QUIC (UDP/443) block, so apps fall back to TCP/TLS;
 *  - the Fake-IP pool range is still routed to the proxy (harmless; tun2socks
 *    may hand out IPs from that range);
 *  - bypass domain suffixes go through the Trojan outbound;
 *  - everything else goes direct.
 *
 * Parity gaps (documented instead of hidden):
 *  - sing-box has no FakeDNS module; domain-based routing relies on the SOCKS
 *    inbound sniffing TLS/HTTP. A connection that carries no hostname can only
 *    match IP rules.
 *  - The Xray `dns-out` rule is approximated with sing-box's `hijack-dns`
 *    action; sing-box resolves names with its own default resolver rather
 *    than FakeDNS.
 *  - ShadowSocks/VMess etc. are out of scope: locked credentials are Trojan
 *    with a client certificate and uTLS Chrome.
 */
class SingBoxConfigGenerator {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun generateConfig(
        socksPort: Int,
        trojanConfig: TrojanConfig,
        clientCertificatePath: String,
        clientPrivateKeyPath: String,
        bypassDomains: List<String>,
        directDomains: List<String> = emptyList(),
        udpAllow: List<String> = emptyList()
    ): String {
        val directSet = directDomains.toSet()
        val directSuffixes = normalizeDomains(directDomains)
        val proxySuffixes = normalizeDomains(
            bypassDomains.filterNot { directSet.contains(it) }
        )
        val udpRules = buildUdpRules(udpAllow)
        val fakeIpPool = "198.18.0.0/15"

        val config = buildJsonObject {
            put("log", buildJsonObject {
                put("level", JsonPrimitive("warn"))
            })

            put("inbounds", buildJsonArray {
                add(buildJsonObject {
                    put("type", JsonPrimitive("socks"))
                    put("tag", JsonPrimitive("socks-in"))
                    put("listen", JsonPrimitive("127.0.0.1"))
                    put("listen_port", JsonPrimitive(socksPort))
                })
            })

            put("outbounds", buildJsonArray {
                add(buildJsonObject {
                    put("type", JsonPrimitive("trojan"))
                    put("tag", JsonPrimitive("proxy"))
                    put("server", JsonPrimitive(trojanConfig.server))
                    put("server_port", JsonPrimitive(trojanConfig.port))
                    put("password", JsonPrimitive(trojanConfig.password))
                    put("tls", buildJsonObject {
                        put("enabled", JsonPrimitive(true))
                        put("server_name", JsonPrimitive(trojanConfig.sni))
                        put("alpn", buildJsonArray {
                            add(JsonPrimitive("h2"))
                            add(JsonPrimitive("http/1.1"))
                        })
                        put("utls", buildJsonObject {
                            put("enabled", JsonPrimitive(true))
                            put("fingerprint", JsonPrimitive("chrome"))
                        })
                        put("client_certificate_path", JsonPrimitive(clientCertificatePath))
                        put("client_key_path", JsonPrimitive(clientPrivateKeyPath))
                    })
                })
                add(buildJsonObject {
                    put("type", JsonPrimitive("direct"))
                    put("tag", JsonPrimitive("direct"))
                })
            })

            put("route", buildJsonObject {
                put("rules", buildJsonArray {
                    // Sniff the destination hostname so domain rules work even
                    // when tun2socks connects to a numeric address. The legacy
                    // inbound `sniff` field was removed in sing-box 1.13.
                    add(buildJsonObject {
                        put("action", JsonPrimitive("sniff"))
                    })

                    // DNS is hijacked into sing-box's DNS module (the legacy
                    // `dns` outbound was removed in 1.13).
                    add(buildJsonObject {
                        put("protocol", JsonPrimitive("dns"))
                        put("action", JsonPrimitive("hijack-dns"))
                    })

                    // User exclusions always win: direct domains (and
                    // subdomains) connect directly even if the downloaded
                    // bypass list contains them. Above the QUIC block so a
                    // direct domain may keep using UDP/443.
                    if (directSuffixes.isNotEmpty()) {
                        add(buildJsonObject {
                            put("domain_suffix", domainArray(directSuffixes))
                            put("action", JsonPrimitive("route"))
                            put("outbound", JsonPrimitive("direct"))
                        })
                    }

                    // Explicit UDP destinations from the allow list. Above
                    // the QUIC block so an allowed UDP/443 entry wins.
                    udpRules.forEach { rule ->
                        add(buildJsonObject {
                            if (rule.ip != null) {
                                put("ip_cidr", buildJsonArray {
                                    add(JsonPrimitive(rule.ip))
                                })
                            }
                            put("network", JsonPrimitive("udp"))
                            putPortRule(rule.ports)
                            put("action", JsonPrimitive("route"))
                            put("outbound", JsonPrimitive("proxy"))
                        })
                    }

                    // Drop QUIC (UDP/443) so apps fall back to TCP/TLS,
                    // which works reliably through the Trojan TCP outbound.
                    add(buildJsonObject {
                        put("network", JsonPrimitive("udp"))
                        put("port", JsonPrimitive(443))
                        put("action", JsonPrimitive("reject"))
                    })

                    // Fake-IP destinations always go through the proxy.
                    add(buildJsonObject {
                        put("ip_cidr", buildJsonArray {
                            add(JsonPrimitive(fakeIpPool))
                        })
                        put("action", JsonPrimitive("route"))
                        put("outbound", JsonPrimitive("proxy"))
                    })

                    // Bypass list domains -> proxy.
                    if (proxySuffixes.isNotEmpty()) {
                        add(buildJsonObject {
                            put("domain_suffix", domainArray(proxySuffixes))
                            put("action", JsonPrimitive("route"))
                            put("outbound", JsonPrimitive("proxy"))
                        })
                    }
                })
                put("final", JsonPrimitive("direct"))
            })
        }

        return json.encodeToString(JsonObject.serializer(), config)
    }

    private fun domainArray(domains: List<String>) = buildJsonArray {
        domains.forEach { add(JsonPrimitive(it)) }
    }

    private fun normalizeDomains(entries: List<String>): List<String> =
        entries.mapNotNull { entry ->
            entry.trim()
                .removePrefix("domain:")
                .removePrefix("full:")
                .trim()
                .removePrefix(".")
                .takeIf { it.isNotBlank() && !it.contains('/') && !it.contains(' ') }
        }.distinct()

    private data class UdpRule(val ip: String?, val ports: String)

    /**
     * Parse "host:ports" allow entries exactly like XrayConfigGenerator:
     *  - `*:53`                    -> ports only
     *  - `1.2.3.0/24:2000-2010`    -> ip + ports
     *  - `[2001:db8::/32]:3074`    -> IPv6 ip + ports
     * Comma-separated port lists become one rule per port (sing-box `port`
     * does not accept lists).
     */
    private fun buildUdpRules(entries: List<String>): List<UdpRule> {
        val rules = mutableListOf<UdpRule>()
        for (entry in entries) {
            val trimmed = entry.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
            val host: String
            val ports: String
            if (trimmed.startsWith("[")) {
                val close = trimmed.indexOf(']')
                if (close <= 1 || close + 1 >= trimmed.length || trimmed[close + 1] != ':') continue
                host = trimmed.substring(1, close)
                ports = trimmed.substring(close + 2)
            } else {
                val split = trimmed.lastIndexOf(':')
                if (split <= 0 || split == trimmed.length - 1) continue
                host = trimmed.substring(0, split)
                ports = trimmed.substring(split + 1)
            }
            if (ports.isBlank()) continue
            val ip = if (host == "*") null else host
            ports.split(',')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .forEach { port -> rules.add(UdpRule(ip, port)) }
        }
        return rules
    }

    private fun portValue(ports: String): JsonPrimitive {
        val single = ports.toIntOrNull()
        if (single != null) return JsonPrimitive(single)
        // Xray uses `2000-2010`; sing-box expects `2000:2010` in port_range.
        return JsonPrimitive(ports.replace('-', ':'))
    }

    /**
     * `port` accepts a single uint16; ranges belong in `port_range`
     * (string form `2000:2010`).
     */
    private fun kotlinx.serialization.json.JsonObjectBuilder.putPortRule(ports: String) {
        val single = ports.toIntOrNull()
        if (single != null) {
            put("port", JsonPrimitive(single))
        } else {
            put("port_range", portValue(ports))
        }
    }
}
