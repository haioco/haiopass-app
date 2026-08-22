package com.haio.bypass.proxy

import com.haio.bypass.config.TrojanConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class XrayConfigGenerator {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun generateConfig(
        socksPort: Int,
        trojanConfig: TrojanConfig,
        bypassDomains: List<String>
    ): String {
        val domainRules = bypassDomains.map { "domain:$it" }
        val fakeIpPool = "198.18.0.0/15"

        val config = buildJsonObject {
            put("log", buildJsonObject {
                put("loglevel", JsonPrimitive("debug"))
            })

            put("inbounds", buildJsonArray {
                add(buildJsonObject {
                    put("tag", JsonPrimitive("socks-in"))
                    put("port", JsonPrimitive(socksPort))
                    put("listen", JsonPrimitive("127.0.0.1"))
                    put("protocol", JsonPrimitive("socks"))
                    put("settings", buildJsonObject {
                        put("udp", JsonPrimitive(true))
                        put("auth", JsonPrimitive("noauth"))
                    })
                    put("sniffing", buildJsonObject {
                        put("enabled", JsonPrimitive(true))
                        // "fakedns" makes xray revert a fake-ip destination back to the
                        // real domain, so the trojan outbound gets the domain even when the
                        // app connected to the synthesized fake IP (no SNI needed).
                        put("destOverride", buildJsonArray {
                            add(JsonPrimitive("http"))
                            add(JsonPrimitive("tls"))
                            add(JsonPrimitive("quic"))
                            add(JsonPrimitive("fakedns"))
                        })
                        put("metadataOnly", JsonPrimitive(false))
                    })
                })
            })

            put("outbounds", buildJsonArray {
                add(buildJsonObject {
                    put("tag", JsonPrimitive("proxy"))
                    put("protocol", JsonPrimitive("trojan"))
                    put("settings", buildJsonObject {
                        put("servers", buildJsonArray {
                            add(buildJsonObject {
                                put("address", JsonPrimitive(trojanConfig.server))
                                put("port", JsonPrimitive(trojanConfig.port))
                                put("password", JsonPrimitive(trojanConfig.password))
                            })
                        })
                    })
                    put("streamSettings", buildJsonObject {
                        put("network", JsonPrimitive("tcp"))
                        put("security", JsonPrimitive("tls"))
                        put("tlsSettings", buildJsonObject {
                            put("serverName", JsonPrimitive(trojanConfig.sni))
                            put("alpn", buildJsonArray {
                                add(JsonPrimitive("h2"))
                                add(JsonPrimitive("http/1.1"))
                            })
                            put("fingerprint", JsonPrimitive("chrome"))
                        })
                    })
                })
                add(buildJsonObject {
                    put("tag", JsonPrimitive("direct"))
                    put("protocol", JsonPrimitive("freedom"))
                    put("settings", buildJsonObject {
                        put("domainStrategy", JsonPrimitive("UseIPv4"))
                    })
                })
                add(buildJsonObject {
                    put("tag", JsonPrimitive("block"))
                    put("protocol", JsonPrimitive("blackhole"))
                })
                // DNS outbound: lets routing send DNS queries into the dns module so
                // FakeDNS can synthesize IPs for listed domains.
                add(buildJsonObject {
                    put("tag", JsonPrimitive("dns-out"))
                    put("protocol", JsonPrimitive("dns"))
                })
            })

            put("fakedns", buildJsonObject {
                put("ipPool", JsonPrimitive(fakeIpPool))
                put("poolSize", JsonPrimitive(65535))
            })

            // DNS: FakeDNS for listed domains only (whitelist); everything else falls
            // back to the real recursive resolver so non-bypass domains resolve to real
            // IPs and stay "direct".
            put("dns", buildJsonObject {
                put("queryStrategy", JsonPrimitive("UseIPv4"))
                put("servers", buildJsonArray {
                    if (domainRules.isNotEmpty()) {
                        add(buildJsonObject {
                            put("address", JsonPrimitive("fakedns"))
                            put("domains", buildJsonArray {
                                domainRules.forEach { rule -> add(JsonPrimitive(rule)) }
                            })
                            put("queryStrategy", JsonPrimitive("UseIPv4"))
                        })
                    }
                    add(buildJsonObject {
                        put("address", JsonPrimitive("8.8.8.8"))
                        put("queryStrategy", JsonPrimitive("UseIPv4"))
                    })
                })
            })

            put("routing", buildJsonObject {
                put("domainStrategy", JsonPrimitive("IPIfNonMatch"))
                put("rules", buildJsonArray {
                    // 1) Hijack app plain-DNS queries into the dns module (FakeDNS).
                    add(buildJsonObject {
                        put("type", JsonPrimitive("field"))
                        put("port", JsonPrimitive(53))
                        put("network", JsonPrimitive("udp,tcp"))
                        put("outboundTag", JsonPrimitive("dns-out"))
                    })
                    // 2) Any connection to a FakeIP destination goes through the proxy.
                    add(buildJsonObject {
                        put("type", JsonPrimitive("field"))
                        put("ip", buildJsonArray { add(JsonPrimitive(fakeIpPool)) })
                        put("outboundTag", JsonPrimitive("proxy"))
                    })
                    // 3) Explicit domain rules -> proxy (covers SNI-visible traffic too).
                    if (domainRules.isNotEmpty()) {
                        add(buildJsonObject {
                            put("type", JsonPrimitive("field"))
                            put("domain", buildJsonArray {
                                domainRules.forEach { rule -> add(JsonPrimitive(rule)) }
                            })
                            put("outboundTag", JsonPrimitive("proxy"))
                        })
                    }
                    // 4) Everything else (non-bypass) goes direct.
                    add(buildJsonObject {
                        put("type", JsonPrimitive("field"))
                        put("network", JsonPrimitive("tcp,udp"))
                        put("outboundTag", JsonPrimitive("direct"))
                    })
                })
            })
        }

        return json.encodeToString(JsonObject.serializer(), config)
    }
}
