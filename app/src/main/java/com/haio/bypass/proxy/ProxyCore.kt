package com.haio.bypass.proxy

/**
 * Minimal local proxy core contract used by [ProxyManager].
 *
 * Both cores expose a local SOCKS inbound; `HaioVpnService` keeps owning the
 * TUN device and tun2socks, so switching Xray <-> sing-box does not change the
 * VPN plumbing.
 */
interface ProxyCore {
    suspend fun start(configJson: String, socksPort: Int): Boolean

    suspend fun reload(configJson: String, socksPort: Int): Boolean

    suspend fun startTun2Socks(tunFd: Int, socksPort: Int): Boolean

    fun stop()

    fun isRunning(): Boolean
}
