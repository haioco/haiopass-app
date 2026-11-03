package com.haio.bypass.domain

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Owns three separate domain collections:
 *
 *  - downloaded: the remote bypass list, replaced on every refresh
 *  - custom:     domains the user added manually for the tunnel
 *  - excluded:   domains the user explicitly wants to keep off the tunnel
 *
 * Keeping them separate means a remote refresh can never delete user choices.
 * The effective tunnel list is (downloaded + custom) - excluded.
 */
class DomainStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("haio_domains", Context.MODE_PRIVATE)

    private val router = DomainRouter()

    private val _domainCount = MutableStateFlow(0)
    val domainCount: StateFlow<Int> = _domainCount.asStateFlow()

    private val _lastFetchTime = MutableStateFlow(0L)
    val lastFetchTime: StateFlow<Long> = _lastFetchTime.asStateFlow()

    private val _domains = MutableStateFlow<List<String>>(emptyList())
    val domains: StateFlow<List<String>> = _domains.asStateFlow()

    private val _excludedDomains = MutableStateFlow<List<String>>(emptyList())
    val excludedDomains: StateFlow<List<String>> = _excludedDomains.asStateFlow()

    private val _customDomains = MutableStateFlow<List<String>>(emptyList())
    val customDomains: StateFlow<List<String>> = _customDomains.asStateFlow()

    private var downloadedDomains: List<String> = emptyList()
    private var excludedSet: Set<String> = emptySet()
    private var customDomainsList: List<String> = emptyList()

    init {
        downloadedDomains = loadList(KEY_DOWNLOADED_DOMAINS)
            ?: loadList(KEY_LEGACY_DOMAINS).orEmpty()
        customDomainsList = loadList(KEY_CUSTOM_DOMAINS).orEmpty()
        excludedSet = loadList(KEY_EXCLUDED_DOMAINS).orEmpty().toSet()
        rebuild()
        _lastFetchTime.value = prefs.getLong(KEY_LAST_FETCH, 0)
    }

    /** Replace the downloaded list without touching user-managed entries. */
    fun setDomains(domains: List<String>) {
        downloadedDomains = normalizeList(domains)
        persistList(KEY_DOWNLOADED_DOMAINS, downloadedDomains)
        // Clean up the legacy merged key once the split keys exist.
        prefs.edit().remove(KEY_LEGACY_DOMAINS).apply()
        rebuild()
        _lastFetchTime.value = System.currentTimeMillis()
        prefs.edit().putLong(KEY_LAST_FETCH, _lastFetchTime.value).apply()
    }

    /** Add a domain to the tunnel list, overriding a previous exclusion. */
    fun addDomain(domain: String) {
        val normalized = normalizeDomain(domain) ?: return
        if (excludedSet.contains(normalized)) {
            excludedSet = excludedSet - normalized
            persistList(KEY_EXCLUDED_DOMAINS, excludedSet.toList())
            _excludedDomains.value = excludedSet.sorted()
        }
        if (!downloadedDomains.contains(normalized) && !customDomainsList.contains(normalized)) {
            customDomainsList = (customDomainsList + normalized).sorted()
            persistList(KEY_CUSTOM_DOMAINS, customDomainsList)
            _customDomains.value = customDomainsList
        }
        rebuild()
    }

    /**
     * Remove a user-added domain, or exclude a downloaded one. Downloaded
     * domains are intentionally not deleted forever because the next refresh
     * would add them back.
     */
    fun removeDomain(domain: String) {
        val normalized = normalizeDomain(domain) ?: return
        if (customDomainsList.contains(normalized)) {
            customDomainsList = customDomainsList - normalized
            persistList(KEY_CUSTOM_DOMAINS, customDomainsList)
            _customDomains.value = customDomainsList
            if (excludedSet.contains(normalized)) {
                excludedSet = excludedSet - normalized
                persistList(KEY_EXCLUDED_DOMAINS, excludedSet.toList())
                _excludedDomains.value = excludedSet.sorted()
            }
            rebuild()
        } else {
            excludeDomain(normalized)
        }
    }

    /**
     * Force a domain (and its subdomains) to use the direct connection,
     * even if it exists in the downloaded bypass list.
     */
    fun excludeDomain(domain: String) {
        val normalized = normalizeDomain(domain) ?: return
        if (excludedSet.contains(normalized)) return
        excludedSet = excludedSet + normalized
        persistList(KEY_EXCLUDED_DOMAINS, excludedSet.toList())
        _excludedDomains.value = excludedSet.sorted()
        rebuild()
    }

    /** Put an excluded domain back on the tunnel list. */
    fun includeDomain(domain: String) {
        val normalized = normalizeDomain(domain) ?: return
        if (!excludedSet.contains(normalized)) return
        excludedSet = excludedSet - normalized
        persistList(KEY_EXCLUDED_DOMAINS, excludedSet.toList())
        _excludedDomains.value = excludedSet.sorted()
        rebuild()
    }

    fun isExcluded(domain: String): Boolean {
        val normalized = normalizeDomain(domain) ?: return false
        return excludedSet.contains(normalized)
    }

    fun shouldProxy(host: String): Boolean = router.shouldProxy(host)

    /** Effective list of domains that are routed through the cloud router. */
    fun getDomains(): List<String> = _domains.value

    /** Domains explicitly kept off the tunnel. */
    fun getExcludedDomains(): List<String> = _excludedDomains.value

    fun getDomainCount(): Int = _domains.value.size

    private fun rebuild() {
        val merged = LinkedHashSet<String>(downloadedDomains.size + customDomainsList.size)
        merged.addAll(downloadedDomains)
        merged.addAll(customDomainsList)
        merged.removeAll(excludedSet)

        val effective = merged.sorted()
        router.setDomains(effective)
        _domains.value = effective
        _domainCount.value = effective.size
    }

    private fun loadList(key: String): List<String>? {
        val raw = prefs.getString(key, null) ?: return null
        return try {
            Json.decodeFromString<List<String>>(raw)
        } catch (_: Exception) {
            null
        }
    }

    private fun persistList(key: String, domains: List<String>) {
        prefs.edit().putString(key, Json.encodeToString(domains)).apply()
    }

    private fun normalizeList(domains: List<String>): List<String> =
        domains.mapNotNull(::normalizeDomain).distinct().sorted()

    private fun normalizeDomain(raw: String): String? {
        var value = raw.trim().lowercase()
        if (value.isEmpty()) return null
        value = value.removePrefix("http://").removePrefix("https://")
        value = value.removePrefix("*.").removePrefix(".")
        value = value.substringBefore('/').substringBefore(':').substringBefore('?')
        value = value.trimEnd('.')
        if (!value.contains('.') || value.any { it.isWhitespace() }) return null
        return value
    }

    companion object {
        private const val KEY_LEGACY_DOMAINS = "cached_domains"
        private const val KEY_DOWNLOADED_DOMAINS = "downloaded_domains"
        private const val KEY_CUSTOM_DOMAINS = "custom_domains"
        private const val KEY_EXCLUDED_DOMAINS = "excluded_domains"
        private const val KEY_LAST_FETCH = "last_fetch_time"
    }
}
