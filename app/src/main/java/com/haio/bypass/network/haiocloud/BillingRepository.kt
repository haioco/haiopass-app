package com.haio.bypass.network.haiocloud

/**
 * Billing: buy a plan, poll invoices, resume an existing invoice.
 *
 * `billing/payments/verify/` is the Zibal callback and must never be called
 * from the client.
 */
class BillingRepository(private val client: PortalClient) {

    suspend fun buy(planId: String, organizationId: String? = null): BuyPlanResult = portalCall {
        client.api.buyPlan(
            BuyPlanBody(
                planId = planId,
                organizationId = organizationId?.takeIf { it.isNotBlank() }
            )
        )
    }

    suspend fun invoice(invoiceId: String): Invoice = portalCall {
        client.api.invoice(invoiceId)
    }

    suspend fun invoices(pageSize: Int = 20): List<Invoice> = portalCall {
        client.api.invoices(pageSize = pageSize)
    }.items()

    suspend fun payInvoice(invoiceId: String): InvoicePayResult = portalCall {
        client.api.payInvoice(invoiceId, InvoicePayBody())
    }
}
