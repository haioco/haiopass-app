package com.haio.bypass.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.haio.bypass.network.haiocloud.PortalContainer

/**
 * Store tab: HaioGate packages, the customer's configs, renewals and the
 * purchase entry point.
 *
 * The old legacy plan list was removed from this screen: it called the
 * retired `haiobypass.haiocloud.com` API on first composition and could leave
 * the whole tab spinning. Existing legacy customers still see their imported
 * accounts in the HaioGate list; new purchases use the portal billing flow.
 */
@Composable
fun StoreHubScreen(
    container: PortalContainer,
    onLogin: () -> Unit,
    onOpenConfig: () -> Unit,
    onOpenDetail: (String) -> Unit,
    onBuy: () -> Unit,
    onRenew: (String) -> Unit,
    onShowSnackbar: (String) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        HaioGateScreen(
            container = container,
            isLoggedIn = container.session.isLoggedIn,
            onLogin = onLogin,
            onOpenConfig = onOpenConfig,
            onOpenDetail = onOpenDetail,
            onBuy = onBuy,
            onRenew = onRenew,
            onShowSnackbar = onShowSnackbar
        )
    }
}
