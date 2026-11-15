package com.haio.bypass.ui.screens

import androidx.compose.runtime.Composable
import com.haio.bypass.HaioPrefs
import com.haio.bypass.config.ConfigManager
import com.haio.bypass.config.SubscriptionInfo

/**
 * The pre-HaioGate plan list, kept unchanged as the second store tab.
 * Behaviour is identical to the old `StoreScreen`; only the config source is
 * stamped as `LEGACY` when it applies a purchased config.
 */
@Composable
fun LegacyStoreScreen(
    prefs: HaioPrefs,
    configManager: ConfigManager,
    subscriptionInfo: SubscriptionInfo? = null,
    onRefreshSub: () -> Unit = {},
    onShowSnackbar: (String) -> Unit
) {
    StoreScreen(
        prefs = prefs,
        configManager = configManager,
        subscriptionInfo = subscriptionInfo,
        onRefreshSub = onRefreshSub,
        onShowSnackbar = onShowSnackbar
    )
}
