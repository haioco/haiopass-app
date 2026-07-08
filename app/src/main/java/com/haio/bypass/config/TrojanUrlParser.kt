package com.haio.bypass.config

import java.net.URLDecoder

object TrojanUrlParser {

    private val TROJAN_REGEX = Regex(
        "(?i)^trojan://([^@]+)@([^:/?#]+):(\\d+)(?:\\?([^#]*))?"
    )

    fun parse(url: String): TrojanConfig? {
        val match = TROJAN_REGEX.find(url.trim()) ?: return null

        val password = try {
            URLDecoder.decode(match.groupValues[1], "UTF-8")
        } catch (_: Exception) {
            match.groupValues[1]
        }

        val server = match.groupValues[2].lowercase()
        val port = match.groupValues[3].toIntOrNull() ?: return null

        if (password.isBlank() || server.isBlank() || port <= 0 || port > 65535) {
            return null
        }

        val query = match.groupValues[4]
        val sni = extractSni(query, server)

        return TrojanConfig(
            password = password,
            server = server,
            port = port,
            sni = sni
        )
    }

    private fun extractSni(query: String, defaultSni: String): String {
        if (query.isBlank()) return defaultSni

        val params = query.split("&").associate {
            val parts = it.split("=", limit = 2)
            parts[0].lowercase() to (parts.getOrNull(1) ?: "")
        }

        return params["sni"]?.takeIf { it.isNotBlank() } ?: defaultSni
    }
}
