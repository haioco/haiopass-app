package com.haio.bypass.billing

import android.content.Context
// import android.util.Log
// import androidx.activity.result.ActivityResultRegistry
import com.haio.bypass.HaioPrefs
import com.haio.bypass.config.ConfigManager
// import com.haio.bypass.network.api.ApiClient
// import com.haio.bypass.network.api.CreatePaymentRequest
import com.haio.bypass.network.api.Plan
// import com.haio.bypass.network.api.VerifyPaymentRequest
// import ir.cafebazaar.poolakey.Connection
// import ir.cafebazaar.poolakey.Payment
// import ir.cafebazaar.poolakey.config.PaymentConfiguration
// import ir.cafebazaar.poolakey.config.SecurityCheck
// import ir.cafebazaar.poolakey.entity.PurchaseInfo
// import ir.cafebazaar.poolakey.entity.SkuDetails
// import ir.cafebazaar.poolakey.request.PurchaseRequest
// import kotlinx.coroutines.CoroutineScope
// import kotlinx.coroutines.Dispatchers
// import kotlinx.coroutines.launch

/**
 * DISABLED — Cafe Bazar (Poolakey) in-app billing.
 * All methods are stubs returning null/no-op.
 * Kept for reference in case Cafe Bazar is re-enabled.
 */
class BazaarPayHelper(
    private val context: Context,
    private val prefs: HaioPrefs,
    private val configManager: ConfigManager,
    private val onPurchaseSuccess: (planName: String) -> Unit,
    private val onPurchaseFailed: (message: String) -> Unit,
    private val onShowSnackbar: (String) -> Unit
) {
    fun connect() {}
    fun disconnect() {}

    fun launchPurchase(registry: Any? = null, plan: Plan?) {
        onShowSnackbar("Cafe Bazar payment is disabled. Use balance purchase instead.")
    }
}
