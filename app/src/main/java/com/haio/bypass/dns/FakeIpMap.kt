package com.haio.bypass.dns

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class FakeIpMap {
    private val ipToDomain = ConcurrentHashMap<String, String>()
    private val domainToIp = ConcurrentHashMap<String, String>()
    private val counter = AtomicInteger(2)

    fun getOrCreate(domain: String): String {
        return domainToIp.getOrPut(domain.lowercase()) {
            val idx = counter.getAndIncrement()
            val ip = "198.18.${idx / 256}.${idx % 256}"
            ipToDomain[ip] = domain.lowercase()
            ip
        }
    }

    fun lookup(ip: String): String? = ipToDomain[ip]

    fun contains(ip: String): Boolean = ipToDomain.containsKey(ip)
}
