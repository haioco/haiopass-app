package com.haio.bypass.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InviteLinkParserTest {

    private val token = "hgi_" + "a".repeat(43)

    @Test
    fun `raw token is accepted`() {
        assertEquals(token, InviteLinkParser.parse(token))
        assertTrue(InviteLinkParser.isValidToken(token))
    }

    @Test
    fun `custom scheme link is accepted`() {
        assertEquals(token, InviteLinkParser.parse("haio://join/$token"))
    }

    @Test
    fun `https app link and trailing slash are accepted`() {
        assertEquals(token, InviteLinkParser.parse("https://my.haio.ir/join/$token"))
        assertEquals(token, InviteLinkParser.parse("https://my.haio.ir/join/$token/"))
    }

    @Test
    fun `quotes and whitespace are tolerated`() {
        assertEquals(token, InviteLinkParser.parse("  \"haio://join/$token\"  "))
    }

    @Test
    fun `wrong scheme host or path is rejected`() {
        assertNull(InviteLinkParser.parse("https://evil.example/join/$token"))
        assertNull(InviteLinkParser.parse("haio://other/$token"))
        assertNull(InviteLinkParser.parse("https://my.haio.ir/other/$token"))
        assertNull(InviteLinkParser.parse("https://my.haio.ir/join/$token/extra"))
        assertNull(InviteLinkParser.parse("haio://join/$token/extra"))
    }

    @Test
    fun `token length and alphabet are strict`() {
        assertFalse(InviteLinkParser.isValidToken("hgi_" + "a".repeat(42)))
        assertFalse(InviteLinkParser.isValidToken("hgi_" + "a".repeat(44)))
        assertFalse(InviteLinkParser.isValidToken("hgi_" + "a".repeat(42) + "!"))
        assertFalse(InviteLinkParser.isValidToken("HGI_" + "a".repeat(43)))
        assertFalse(InviteLinkParser.isValidToken(""))
        assertFalse(InviteLinkParser.isValidToken(null))
    }

    @Test
    fun `canonical links are built from a valid token only`() {
        assertEquals(
            "https://my.haio.ir/join/$token",
            InviteLinkParser.buildWebLink(token)
        )
        assertEquals(
            "haio://join/$token",
            InviteLinkParser.buildDeepLink(token)
        )
        assertNull(InviteLinkParser.buildWebLink("bad"))
    }
}
