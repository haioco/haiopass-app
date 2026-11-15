package com.haio.bypass.network.haiocloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingPurchaseTest {

    private val purchase = PendingPurchase(
        mode = PurchaseMode.RENEW,
        accountId = "acc-1",
        packageId = "pkg-1",
        planId = "plan-1",
        invoiceId = "inv-1",
        paymentId = null,
        createdAt = 1_000L,
        stage = PendingStage.CREATED
    )

    @Test
    fun `pending is valid inside the 24 hour window`() {
        assertFalse(purchase.isExpired(now = 1_000L + PendingPurchase.PENDING_TTL_MS - 1))
    }

    @Test
    fun `pending expires after 24 hours`() {
        assertTrue(purchase.isExpired(now = 1_000L + PendingPurchase.PENDING_TTL_MS + 1))
    }

    @Test
    fun `stage transitions keep purchase identity`() {
        val paid = purchase.copy(stage = PendingStage.PAID)
        assertEquals(purchase.invoiceId, paid.invoiceId)
        assertEquals(PurchaseMode.RENEW, paid.mode)
        assertEquals(PendingStage.PAID, paid.stage)
    }
}
