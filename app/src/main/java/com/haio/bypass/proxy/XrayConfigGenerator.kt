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
                        put("destOverride", buildJsonArray {
                            add(JsonPrimitive("http"))
                            add(JsonPrimitive("tls"))
                            add(JsonPrimitive("quic"))
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
                        put("domainStrategy", JsonPrimitive("UseIP"))
                    })
                })
                add(buildJsonObject {
                    put("tag", JsonPrimitive("block"))
                    put("protocol", JsonPrimitive("blackhole"))
                })
            })

            put("dns", buildJsonObject {
                put("servers", buildJsonArray {
                    add(JsonPrimitive("8.8.8.8"))
                })
            })

            put("routing", buildJsonObject {
                put("domainStrategy", JsonPrimitive("IPIfNonMatch"))
                put("rules", buildJsonArray {
                    if (domainRules.isNotEmpty()) {
                        add(buildJsonObject {
                            put("type", JsonPrimitive("field"))
                            put("domain", buildJsonArray {
                                domainRules.forEach { rule -> add(JsonPrimitive(rule)) }
                            })
                            put("outboundTag", JsonPrimitive("proxy"))
                        })
                    }
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