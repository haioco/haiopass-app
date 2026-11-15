package com.haio.bypass.network.haiocloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PortalValidatorsTest {

    @Test
    fun `persian digits are normalised`() {
        assertEquals("09123456789", PortalValidators.normalizeMobile("۰۹۱۲۳۴۵۶۷۸۹"))
    }

    @Test
    fun `arabic digits are normalised`() {
        assertEquals("09123456789", PortalValidators.normalizeMobile("٠٩١٢٣٤٥٦٧٨٩"))
    }

    @Test
    fun `spaces dashes and country code are accepted`() {
        assertEquals("09123456789", PortalValidators.normalizeMobile("+98 912-345 6789"))
        assertEquals("09123456789", PortalValidators.normalizeMobile("0098 912 345 6789"))
        assertEquals("09123456789", PortalValidators.normalizeMobile("9123456789"))
    }

    @Test
    fun `invalid mobiles are rejected`() {
        assertFalse(PortalValidators.isValidMobile(""))
        assertFalse(PortalValidators.isValidMobile("0912345678"))
        assertFalse(PortalValidators.isValidMobile("08123456789"))
        assertTrue(PortalValidators.isValidMobile("۰۹۱۲۳۴۵۶۷۸۹"))
    }

    @Test
    fun `national code keeps ten digits`() {
        assertEquals("0012345678", PortalValidators.normalizeNationalCode("۰۰۱۲۳۴۵۶۷۸۹"))
        assertEquals("12345", PortalValidators.normalizeNationalCode("12345"))
        assertTrue(PortalValidators.isValidNationalCode("۰۰۱۲۳۴۵۶۷۸"))
        assertFalse(PortalValidators.isValidNationalCode("123"))
        assertFalse(PortalValidators.isValidNationalCode("1111111111"))
    }

    @Test
    fun `otp is six digits`() {
        assertEquals("123456", PortalValidators.normalizeOtp("۱۲۳۴۵۶۷"))
        assertTrue(PortalValidators.isValidOtp("۱۲۳۴۵۶"))
        assertFalse(PortalValidators.isValidOtp("12345"))
    }
}
