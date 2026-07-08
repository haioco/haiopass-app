package com.haio.bypass.domain

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class DomainFetcher {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun fetch(): List<String> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(DOMAIN_URL)
                .header("Cache-Control", "no-cache")
                .header("Pragma", "no-cache")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext emptyList()

            val body = response.body?.string() ?: return@withContext emptyList()
            parseDomainText(body)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseDomainText(text: String): List<String> {
        val seen = mutableSetOf<String>()
        val result = mutableListOf<String>()

        text.lines().forEach { line ->
            val trimmed = line.trim().lowercase()
            if (trimmed.isEmpty()) return@forEach
            if (trimmed.startsWith("#")) return@forEach
            if (trimmed.contains("/") || trimmed.contains(":") || trimmed.contains(" ")) return@forEach
            if (!trimmed.contains(".")) return@forEach

            if (isDnsInfrastructure(trimmed)) return@forEach

            if (seen.add(trimmed)) {
                result.add(trimmed)
            }
        }

        return result
    }

    private fun isDnsInfrastructure(domain: String): Boolean {
        val prefixes = listOf("ns1.", "ns2.", "ns3.", "ns4.", "ns5.")
        if (prefixes.any { domain.startsWith(it) }) return true

        val suffixes = listOf("-hostmaster.", "dns-admin.", "hostmaster.", "dns1.")
        if (suffixes.any { domain.contains(it) }) return true

        if (domain.endsWith("nsone.net")) return true

        return false
    }

    companion object {
        const val DOMAIN_URL = "https://tools.haiocloud.com/domains.txt"
    }
}
