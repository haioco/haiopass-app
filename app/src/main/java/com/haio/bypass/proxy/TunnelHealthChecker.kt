package com.haio.bypass.proxy

import android.util.Log
import com.haio.bypass.config.TrojanConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/**
 * Verifies that the configured cloud-router server is actually reachable and
 * presents a certificate valid for its SNI. A local SOCKS port being open only
 * proves that Xray started, not that the tunnel can carry traffic.
 *
 * The probe mirrors the Xray outbound: direct TCP to server:port plus a TLS
 * handshake using the configured SNI. The Xray config does not set
 * allowInsecure, so this validates the same certificate chain and hostname.
 */
object TunnelHealthChecker {

    private const val TAG = "TunnelHealth"
    private const val CONNECT_TIMEOUT_MS = 3_000
    private const val READ_TIMEOUT_MS = 3_000
    private const val TOTAL_TIMEOUT_MS = 6_000L

    suspend fun check(config: TrojanConfig): Boolean = withContext(Dispatchers.IO) {
        withTimeoutOrNull(TOTAL_TIMEOUT_MS) {
            runCatching {
                val sni = config.sni.ifBlank { config.server }
                Socket().use { raw ->
                    raw.connect(
                        InetSocketAddress(config.server, config.port),
                        CONNECT_TIMEOUT_MS
                    )
                    raw.soTimeout = READ_TIMEOUT_MS

                    val sslSocket = (SSLSocketFactory.getDefault() as SSLSocketFactory)
                        .createSocket(raw, sni, config.port, true) as SSLSocket
                    sslSocket.use { tls ->
                        tls.soTimeout = READ_TIMEOUT_MS
                        val params = tls.sslParameters
                        params.endpointIdentificationAlgorithm = "HTTPS"
                        tls.sslParameters = params
                        tls.startHandshake()
                        true
                    }
                }
            }.onFailure {
                Log.w(TAG, "Tunnel health check failed: ${it.message}")
            }.getOrDefault(false)
        } ?: false
    }
}
