package com.haio.bypass.network.haiocloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class AccountClassifierTest {

    private val now = Instant.parse("2026-11-15T12:00:00Z")

    @Test
    fun `active account with future expiry is active`() {
        val category = AccountClassifier.categoryOf(
            status = "active",
            expiredAt = "2026-12-01T12:00:00Z",
            trafficIsOver = false,
            now = now
        )
        assertEquals(AccountCategory.ACTIVE, category)
    }

    @Test
    fun `expired status wins over future date`() {
        val category = AccountClassifier.categoryOf(
            status = "expired",
            expiredAt = "2026-12-01T12:00:00Z",
            trafficIsOver = false,
            now = now
        )
        assertEquals(AccountCategory.EXPIRED, category)
    }

    @Test
    fun `quota exhausted is expired`() {
        assertEquals(
            AccountCategory.EXPIRED,
            AccountClassifier.categoryOf("quota_exhausted", "2026-12-01T00:00:00Z", false, now)
        )
    }

    @Test
    fun `traffic over is expired even with future date`() {
        assertEquals(
            AccountCategory.EXPIRED,
            AccountClassifier.categoryOf("active", "2026-12-01T00:00:00Z", true, now)
        )
    }

    @Test
    fun `past expiry is expired`() {
        assertEquals(
            AccountCategory.EXPIRED,
            AccountClassifier.categoryOf("active", "2026-11-01T00:00:00Z", false, now)
        )
    }

    @Test
    fun `offset timestamps are parsed`() {
        assertEquals(
            AccountCategory.ACTIVE,
            AccountClassifier.categoryOf("active", "2026-11-20T15:00:00+03:30", false, now)
        )
    }

    @Test
    fun `suspended is support`() {
        assertEquals(
            AccountCategory.SUPPORT,
            AccountClassifier.categoryOf("suspended", "2026-12-01T00:00:00Z", false, now)
        )
    }

    @Test
    fun `null expiry is support and never active`() {
        assertEquals(
            AccountCategory.SUPPORT,
            AccountClassifier.categoryOf("active", null, false, now)
        )
        assertNull(AccountClassifier.parseTimestamp("not-a-date"))
    }

    @Test
    fun `active accounts sort by soonest expiry`() {
        val later = HaioGateAccount(id = "b", status = "active", expiredAt = "2026-12-10T00:00:00Z")
        val sooner = HaioGateAccount(id = "a", status = "active", expiredAt = "2026-11-20T00:00:00Z")
        val sorted = AccountClassifier.sortForDisplay(listOf(later, sooner), AccountCategory.ACTIVE)
        assertEquals(listOf("a", "b"), sorted.map { it.id })
    }

    @Test
    fun `expired accounts sort by most recently ended`() {
        val older = HaioGateAccount(id = "old", status = "expired", expiredAt = "2026-09-01T00:00:00Z")
        val newer = HaioGateAccount(id = "new", status = "expired", expiredAt = "2026-11-01T00:00:00Z")
        val sorted = AccountClassifier.sortForDisplay(listOf(older, newer), AccountCategory.EXPIRED)
        assertEquals(listOf("new", "old"), sorted.map { it.id })
    }
}
