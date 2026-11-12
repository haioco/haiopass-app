package com.haio.bypass.domain

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class DomainFetcher {

    constructor()
    constructor(@Suppress("UNUSED_PARAMETER") context: Context)

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    suspend fun fetch(): List<String> = withContext(Dispatchers.IO) {
        // Source of truth is the remote domains.txt. Everything else goes direct.
        val merged = LinkedHashSet<String>()

        for (url in REMOTE_URLS) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("Cache-Control", "no-cache")
                    .header("Pragma", "no-cache")
                    .build()

                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    response.close()
                    continue
                }

                val body = response.body?.string() ?: continue
                merged.addAll(parseDomainText(body))
            } catch (_: Exception) {
                continue
            }
        }

        merged.toList().also {
            Log.d(TAG, "fetch complete: ${it.size} domains from remote sources")
        }
    }

    /**
     * UDP destinations that may use the tunnel, in "host:ports" form:
     * `*:53`, `1.2.3.0/24:2000-2010`, `[2001:db8::/32]:3074`.
     * A missing file leaves the list empty; DNS stays allowed in the generator.
     */
    suspend fun fetchUdpAllow(): List<String> = withContext(Dispatchers.IO) {
        val merged = LinkedHashSet<String>()
        for (url in UDP_URLS) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("Cache-Control", "no-cache")
                    .header("Pragma", "no-cache")
                    .build()
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    response.close()
                    continue
                }
                val body = response.body?.string() ?: continue
                merged.addAll(parseUdpAllow(body))
            } catch (_: Exception) {
                continue
            }
        }
        merged.toList().also {
            Log.d(TAG, "udp allow fetch complete: ${it.size} entries")
        }
    }

    private fun parseUdpAllow(text: String): List<String> =
        text.lines().mapNotNull { line ->
            val value = line.trim()
            when {
                value.isEmpty() -> null
                value.startsWith("#") -> null
                value.contains(" ") -> null
                !value.contains(":") -> null
                else -> value
            }
        }.distinct()

    private fun parseDomainText(text: String): List<String> {        val seen = mutableSetOf<String>()
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

    private val dnsPrefixes = setOf("ns1.", "ns2.", "ns3.", "ns4.", "ns5.")
    private val dnsSuffixes = setOf("-hostmaster.", "dns-admin.", "hostmaster.", "dns1.")

    private fun isDnsInfrastructure(domain: String): Boolean {
        if (dnsPrefixes.any { domain.startsWith(it) }) return true
        if (dnsSuffixes.any { domain.contains(it) }) return true
        if (domain.endsWith("nsone.net")) return true
        return false
    }

    companion object {
        private const val TAG = "DomainFetcher"
        private val REMOTE_URLS = listOf(
            "https://tools.haiocloud.com/domains.txt",
            "https://raw.githubusercontent.com/haiocloud/bypass-domains/main/domains.txt"
        )
        private val UDP_URLS = listOf(
            "https://tools.haiocloud.com/udp.txt"
        )
    }
}
