package com.haio.bypass.network.haiocloud

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PortalBrowserTest {

    @Test
    fun `zibal hosts are trusted`() {
        assertTrue(PortalBrowser.isTrustedPaymentHost("zibal.ir"))
        assertTrue(PortalBrowser.isTrustedPaymentHost("gateway.zibal.ir"))
        assertTrue(PortalBrowser.isTrustedPaymentHost("GATEWAY.ZIBAL.IR"))
    }

    @Test
    fun `lookalike and foreign hosts are rejected`() {
        assertFalse(PortalBrowser.isTrustedPaymentHost("evil-zibal.ir"))
        assertFalse(PortalBrowser.isTrustedPaymentHost("zibal.ir.example.com"))
        assertFalse(PortalBrowser.isTrustedPaymentHost("example.com"))
        assertFalse(PortalBrowser.isTrustedPaymentHost(""))
    }
}
