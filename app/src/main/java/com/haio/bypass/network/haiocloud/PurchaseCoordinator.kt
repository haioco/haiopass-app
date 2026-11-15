package com.haio.bypass.network.haiocloud

sealed class PurchaseStart {
    /** Free or fully covered purchase: nothing to pay, refresh accounts. */
    data class Active(val invoiceId: String?) : PurchaseStart()

    /** Zibal checkout created and persisted; open [paymentUrl] in the browser. */
    data class Payment(val purchase: PendingPurchase, val paymentUrl: String) : PurchaseStart()
}

sealed class PendingStatus {
    data object None : PendingStatus()
    data class Pending(val purchase: PendingPurchase) : PendingStatus()
    data class Paid(val purchase: PendingPurchase) : PendingStatus()
    data class Failed(val purchase: PendingPurchase, val invoiceStatus: String) : PendingStatus()
    data class Expired(val purchase: PendingPurchase) : PendingStatus()
}

/**
 * Buy/renew orchestration, pending-purchase persistence and invoice polling.
 *
 * Flow: package intent → plan buy → (free?) done : persist pending → browser.
 * Invoice status is polled; `billing/payments/verify/` is never called.
 */
class PurchaseCoordinator(
    private val haioGate: HaioGateRepository,
    private val billing: BillingRepository,
    private val pendingStore: PendingPurchaseStore
) {

    fun currentPending(): PendingPurchase? = pendingStore.get()

    fun markBrowserOpened(): PendingPurchase? = pendingStore.updateStage(PendingStage.BROWSER_OPENED)

    fun clearPending() = pendingStore.clear()

    /** 24-hour expiry sweep; returns the expired purchase when it cleared one. */
    fun expireIfStale(now: Long = System.currentTimeMillis()): PendingPurchase? {
        val pending = pendingStore.get() ?: return null
        if (!pending.isExpired(now)) return null
        pendingStore.clear()
        return pending
    }

    suspend fun startBuy(packageId: String, organizationId: String?): PurchaseStart =
        start(mode = PurchaseMode.BUY, accountId = null, packageId = packageId, organizationId = organizationId)

    suspend fun startRenew(
        accountId: String,
        packageId: String?,
        organizationId: String?,
        includedUsers: Int? = null
    ): PurchaseStart = start(
        mode = PurchaseMode.RENEW,
        accountId = accountId,
        packageId = packageId,
        organizationId = organizationId,
        includedUsers = includedUsers
    )

    private suspend fun start(
        mode: PurchaseMode,
        accountId: String?,
        packageId: String?,
        organizationId: String?,
        includedUsers: Int? = null
    ): PurchaseStart {
        if (packageId.isNullOrBlank()) {
            throw PortalException(PortalError.Unknown("پکیج انتخاب نشده است."))
        }

        // One live pending purchase at a time: resume instead of duplicating.
        currentPending()?.let { existing ->
            if (!existing.isExpired()) {
                throw PortalException(PortalError.Http(409, "یک پرداخت در انتظار تایید دارید. ابتدا وضعیت آن را بررسی کنید."))
            }
            pendingStore.clear()
        }

        val intent = when (mode) {
            PurchaseMode.BUY -> haioGate.startBuy(packageId)
            PurchaseMode.RENEW -> haioGate.startRenew(
                accountId = accountId ?: throw PortalException(PortalError.Unknown("اکانت انتخاب نشده است.")),
                packageId = packageId,
                includedUsers = includedUsers
            )
        }
        val planId = intent.planId?.takeIf { it.isNotBlank() }
            ?: throw PortalException(PortalError.Unknown("برنامه پرداخت برای این پکیج پیدا نشد."))

        val buy = billing.buy(planId = planId, organizationId = organizationId)
        val status = buy.status.orEmpty()
        if (status == "active") {
            return PurchaseStart.Active(buy.invoiceId)
        }
        val invoiceId = buy.invoiceId?.takeIf { it.isNotBlank() }
            ?: throw PortalException(PortalError.Unknown("شناسه پرداخت از سرور دریافت نشد."))
        val paymentUrl = buy.paymentUrl?.takeIf { it.isNotBlank() }
            ?: throw PortalException(PortalError.Unknown("لینک درگاه پرداخت دریافت نشد."))

        val purchase = PendingPurchase(
            mode = mode,
            accountId = accountId,
            packageId = packageId,
            planId = planId,
            invoiceId = invoiceId,
            paymentId = buy.paymentId,
            createdAt = System.currentTimeMillis(),
            stage = PendingStage.CREATED
        )
        // Persist before the browser opens so process death cannot lose it.
        pendingStore.save(purchase)
        return PurchaseStart.Payment(purchase, paymentUrl)
    }

    /** Checks the pending invoice. Never calls the gateway verify callback. */
    suspend fun checkPending(): PendingStatus {
        val pending = pendingStore.get() ?: return PendingStatus.None
        if (pending.isExpired()) {
            pendingStore.clear()
            return PendingStatus.Expired(pending)
        }
        val invoice = billing.invoice(pending.invoiceId)
        return when (invoice.status) {
            "paid" -> {
                // Payment confirmed: clear the pending row so it cannot block a
                // later purchase while the account provisions.
                pendingStore.clear()
                PendingStatus.Paid(pending.copy(stage = PendingStage.PAID))
            }
            "canceled", "refunded" -> {
                pendingStore.clear()
                PendingStatus.Failed(pending, invoice.status.orEmpty())
            }
            else -> PendingStatus.Pending(pending)
        }
    }

    /** Fresh gateway URL for the same pending invoice; no duplicate invoice. */
    suspend fun resumePayment(): String {
        val pending = pendingStore.get()
            ?: throw PortalException(PortalError.Http(409, "پرداخت در انتظاری وجود ندارد."))
        if (pending.isExpired()) {
            pendingStore.clear()
            throw PortalException(PortalError.Http(409, "پرداخت قبلی منقضی شده است."))
        }
        val result = billing.payInvoice(pending.invoiceId)
        val paymentUrl = result.paymentUrl?.takeIf { it.isNotBlank() }
        if (paymentUrl != null) {
            return paymentUrl
        }
        if (result.status == "active") {
            pendingStore.updateStage(PendingStage.PAID)
            throw PortalException(PortalError.Http(202, "این پرداخت قبلاً تکمیل شده است."))
        }
        throw PortalException(PortalError.Unknown("لینک درگاه پرداخت دریافت نشد."))
    }
}
