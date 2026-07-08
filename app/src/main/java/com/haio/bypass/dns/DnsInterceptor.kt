package com.haio.bypass.dns

import android.net.VpnService
import android.util.Log
import com.haio.bypass.domain.DomainStore
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class DnsInterceptor(
    private val fakeIpMap: FakeIpMap,
    private val domainStore: DomainStore,
    private val vpnService: VpnService
) {
    fun handleDnsQuery(queryData: ByteArray, queryLength: Int): ByteArray? {
        if (queryLength < 12) return null

        try {
            val domain = parseDnsQuery(queryData, queryLength) ?: return null
            val isBypass = isBypassDomain(domain)

            if (!isBypass) {
                return forwardToRealDns(queryData, queryLength)
            }

            val fakeIp = fakeIpMap.getOrCreate(domain)
            Log.d(TAG, "DNS bypass: $domain -> $fakeIp")
            return buildDnsResponse(queryData, queryLength, fakeIp)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to handle DNS query", e)
            return null
        }
    }

    private fun forwardToRealDns(queryData: ByteArray, queryLength: Int): ByteArray? {
        return try {
            val ipHeaderLen = (queryData[0].toInt() and 0xF) * 4
            val dnsQuery = ByteArray(queryLength - ipHeaderLen - 8)
            System.arraycopy(queryData, ipHeaderLen + 8, dnsQuery, 0, dnsQuery.size)

            val socket = DatagramSocket()
            vpnService.protect(socket)
            socket.soTimeout = 5000

            val request = DatagramPacket(dnsQuery, dnsQuery.size, InetAddress.getByName("8.8.8.8"), 53)
            socket.send(request)

            val responseBuffer = ByteArray(1024)
            val response = DatagramPacket(responseBuffer, responseBuffer.size)
            socket.receive(response)
            socket.close()

            buildDnsResponseFromReal(queryData, queryLength, response.data, response.length)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to forward DNS to real server", e)
            null
        }
    }

    private fun buildDnsResponseFromReal(queryData: ByteArray, queryLength: Int, dnsResponse: ByteArray, dnsResponseLen: Int): ByteArray {
        val ipHeaderLen = (queryData[0].toInt() and 0xF) * 4
        val udpLen = 8 + dnsResponseLen
        val totalLen = ipHeaderLen + udpLen
        val response = ByteArray(totalLen)

        System.arraycopy(queryData, 0, response, 0, ipHeaderLen)

        val totalLength = totalLen
        response[2] = ((totalLength shr 8) and 0xFF).toByte()
        response[3] = (totalLength and 0xFF).toByte()

        response[8] = 64
        response[9] = 17

        System.arraycopy(queryData, ipHeaderLen + 2, response, ipHeaderLen, 2)
        System.arraycopy(queryData, ipHeaderLen, response, ipHeaderLen + 2, 2)

        val udpTotalLen = udpLen
        response[ipHeaderLen + 4] = ((udpTotalLen shr 8) and 0xFF).toByte()
        response[ipHeaderLen + 5] = (udpTotalLen and 0xFF).toByte()
        response[ipHeaderLen + 6] = 0
        response[ipHeaderLen + 7] = 0

        System.arraycopy(dnsResponse, 0, response, ipHeaderLen + 8, dnsResponseLen)

        response[10] = 0
        response[11] = 0
        var sum = 0
        for (i in 0 until ipHeaderLen step 2) {
            if (i == 10) continue
            sum += ((response[i].toInt() and 0xFF) shl 8) or (response[i + 1].toInt() and 0xFF)
        }
        while (sum > 0xFFFF) sum = (sum and 0xFFFF) + (sum shr 16)
        response[10] = ((sum shr 8) and 0xFF).toByte()
        response[11] = (sum and 0xFF).toByte()

        return response
    }

    private fun parseDnsQuery(data: ByteArray, length: Int): String? {
        if (length < 12) return null

        val qdCount = ((data[4].toInt() and 0xFF) shl 8) or (data[5].toInt() and 0xFF)
        if (qdCount != 1) return null

        var pos = 12
        val domain = StringBuilder()

        while (pos < length) {
            val labelLen = data[pos].toInt() and 0xFF
            if (labelLen == 0) {
                pos++
                break
            }
            if (labelLen > 63) return null
            if (pos + 1 + labelLen > length) return null

            if (domain.isNotEmpty()) domain.append('.')
            for (i in 1..labelLen) {
                domain.append((data[pos + i].toInt() and 0xFF).toChar())
            }
            pos += 1 + labelLen
        }

        if (domain.isEmpty()) return null
        return domain.toString().lowercase()
    }

    private fun isBypassDomain(domain: String): Boolean {
        val domains = domainStore.getDomains()
        for (bypassDomain in domains) {
            if (domain == bypassDomain || domain.endsWith(".$bypassDomain")) {
                return true
            }
        }
        return false
    }

    private fun buildDnsResponse(queryData: ByteArray, queryLength: Int, fakeIp: String): ByteArray {
        val response = ByteArray(queryLength + 16)
        System.arraycopy(queryData, 0, response, 0, queryLength)

        val ipHeaderLen = (queryData[0].toInt() and 0xF) * 4

        val newIpLen = queryLength + 16
        response[2] = ((newIpLen shr 8) and 0xFF).toByte()
        response[3] = (newIpLen and 0xFF).toByte()

        response[ipHeaderLen + 4] = (((16 + queryLength - ipHeaderLen) shr 8) and 0xFF).toByte()
        response[ipHeaderLen + 5] = ((16 + queryLength - ipHeaderLen) and 0xFF).toByte()
        response[ipHeaderLen + 6] = 0
        response[ipHeaderLen + 7] = 0

        val dnsOffset = ipHeaderLen + 8
        response[dnsOffset + 2] = (response[dnsOffset + 2].toInt() or 0x80).toByte()
        response[dnsOffset + 3] = (response[dnsOffset + 3].toInt() or 0x80).toByte()
        response[dnsOffset + 7] = 1

        val parts = fakeIp.split(".")
        val ipBytes = byteArrayOf(
            parts[0].toInt().toByte(),
            parts[1].toInt().toByte(),
            parts[2].toInt().toByte(),
            parts[3].toInt().toByte()
        )

        var pos = queryLength
        response[pos++] = 0xC0.toByte()
        response[pos++] = 0x0C.toByte()
        response[pos++] = 0x00
        response[pos++] = 0x01
        response[pos++] = 0x00
        response[pos++] = 0x01
        response[pos++] = 0x00
        response[pos++] = 0x00
        response[pos++] = 0x00
        response[pos++] = 0x3C
        response[pos++] = 0x00
        response[pos++] = 0x04
        System.arraycopy(ipBytes, 0, response, pos, 4)

        recalcIpChecksum(response, ipHeaderLen)

        return response
    }

    private fun recalcIpChecksum(data: ByteArray, ipHeaderLen: Int) {
        data[10] = 0
        data[11] = 0
        var sum = 0
        for (i in 0 until ipHeaderLen step 2) {
            if (i == 10) continue
            sum += ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
        }
        while (sum > 0xFFFF) sum = (sum and 0xFFFF) + (sum shr 16)
        data[10] = ((sum shr 8) and 0xFF).toByte()
        data[11] = (sum and 0xFF).toByte()
    }

    companion object {
        private const val TAG = "DnsInterceptor"
    }
}