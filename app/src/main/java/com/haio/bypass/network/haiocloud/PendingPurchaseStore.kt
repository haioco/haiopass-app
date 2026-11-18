package com.haio.bypass.network.haiocloud

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson

enum class PurchaseMode { BUY, RENEW }

enum class PendingStage { CREATED, BROWSER_OPENED, PAID, PROVISIONING, DONE, FAILED }

/**
 * Non-sensitive pending purchase. Never contains tokens, mobile numbers,
 * national codes, payment URLs or credentials.
 */
data class PendingPurchase(
    val mode: PurchaseMode,
    val accountId: String?,
    val packageId: String,
    val planId: String,
    val invoiceId: String,
    val paymentId: String?,
    val createdAt: Long,
    val stage: PendingStage
) {
    fun isExpired(now: Long = System.currentTimeMillis()): Boolean =
        now - createdAt > PENDING_TTL_MS

    companion object {
        const val PENDING_TTL_MS: Long = 24L * 60L * 60L * 1000L
    }
}

class PendingPurchaseStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("haio_portal_pending", Context.MODE_PRIVATE)

    private val gson = Gson()

    fun get(): PendingPurchase? {
        val raw = prefs.getString(KEY_PENDING, null) ?: return null
        return runCatching { gson.fromJson(raw, PendingPurchase::class.java) }.getOrNull()
    }

    fun save(purchase: PendingPurchase) {
        // commit(): the browser opens right after this call.
        prefs.edit().putString(KEY_PENDING, gson.toJson(purchase)).commit()
    }

    fun updateStage(stage: PendingStage): PendingPurchase? {
        val current = get() ?: return null
        val updated = current.copy(stage = stage)
        save(updated)
        return updated
    }

    fun clear() {
        runCatching { prefs.edit().remove(KEY_PENDING).commit() }
    }

    private companion object {
        const val KEY_PENDING = "pending_purchase"
    }
}
