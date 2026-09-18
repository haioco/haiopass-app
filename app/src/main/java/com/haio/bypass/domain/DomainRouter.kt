package com.haio.bypass.domain

import java.util.concurrent.ConcurrentHashMap

class DomainRouter {

    private val domains = ConcurrentHashMap.newKeySet<String>()
    private val suffixIndex = ConcurrentHashMap<String, MutableSet<String>>()

    @Volatile
    private var domainList: List<String> = emptyList()

    fun setDomains(list: List<String>) {
        domains.clear()
        suffixIndex.clear()
        domainList = list

        list.forEach { domain ->
            domains.add(domain.lowercase())
            val suffix = extractTwoPartSuffix(domain.lowercase())
            suffixIndex.getOrPut(suffix) { mutableSetOf() }.add(domain.lowercase())
        }
    }

    fun shouldProxy(host: String): Boolean {
        val lowerHost = host.lowercase()

        if (domains.contains(lowerHost)) return true

        val suffix = extractTwoPartSuffix(lowerHost)
        suffixIndex[suffix]?.let { baseDomains ->
            for (baseDomain in baseDomains) {
                if (lowerHost == baseDomain || lowerHost.endsWith(".$baseDomain")) {
                    return true
                }
            }
        }

        return false
    }

    fun getDomains(): List<String> = domainList

    fun getDomainCount(): Int = domains.size

    private fun extractTwoPartSuffix(domain: String): String {
        val parts = domain.split(".")
        return if (parts.size >= 2) {
            parts.takeLast(2).joinToString(".")
        } else {
            domain
        }
    }
}
