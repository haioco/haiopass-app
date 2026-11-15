package com.haio.bypass.network.haiocloud

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent

/**
 * Opens the Zibal gateway in the system browser (Chrome Custom Tabs).
 *
 * The app never renders payment in a WebView and never persists the URL.
 * Only HTTPS URLs on the Zibal host are accepted, so a compromised/incorrect
 * API response cannot send the user to an arbitrary site.
 */
object PortalBrowser {

    fun open(context: Context, url: String): Boolean {
        val uri = runCatching { Uri.parse(url.trim()) }.getOrNull() ?: return false
        if (!isTrustedPaymentUrl(uri)) return false

        val appContext = context.applicationContext
        return try {
            val customTabsIntent = CustomTabsIntent.Builder()
                .setShowTitle(true)
                .build()
            customTabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            customTabsIntent.launchUrl(appContext, uri)
            true
        } catch (_: Exception) {
            // No Custom Tabs provider: fall back to the system browser.
            try {
                val fallback = Intent(Intent.ACTION_VIEW, uri).apply {
                    addCategory(Intent.CATEGORY_BROWSABLE)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                appContext.startActivity(fallback)
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    fun isTrustedPaymentUrl(uri: Uri): Boolean {
        if (!uri.scheme.equals("https", ignoreCase = true)) return false
        val host = uri.host?.lowercase() ?: return false
        return isTrustedPaymentHost(host)
    }

    fun isTrustedPaymentHost(host: String): Boolean {
        val normalized = host.lowercase().trim('.')
        return normalized == "zibal.ir" || normalized.endsWith(".zibal.ir")
    }
}
