package com.haio.bypass.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.haio.bypass.HaioPrefs
import com.haio.bypass.config.ConfigManager
import com.haio.bypass.config.SubscriptionInfo
import com.haio.bypass.network.haiocloud.PortalContainer

/**
 * Store tab with two inner tabs: HaioGate (default) and the unchanged legacy
 * plan list. Guests see a login card in the HaioGate tab and never trigger
 * package/account/payment calls.
 */
@Composable
fun StoreHubScreen(
    container: PortalContainer,
    prefs: HaioPrefs,
    configManager: ConfigManager,
    subscriptionInfo: SubscriptionInfo?,
    onRefreshSub: () -> Unit,
    onLogin: () -> Unit,
    onOpenConfig: () -> Unit,
    onOpenDetail: (String) -> Unit,
    onBuy: () -> Unit,
    onRenew: (String) -> Unit,
    onShowSnackbar: (String) -> Unit
) {
    var selectedTab by rememberSaveable { mutableStateOf(0) }
    val isLoggedIn = container.session.isLoggedIn

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("هایو گیت") }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("پلن‌های قبلی") }
            )
        }

        Box(modifier = Modifier.weight(1f)) {
            if (selectedTab == 0) {
                HaioGateScreen(
                    container = container,
                    isLoggedIn = isLoggedIn,
                    onLogin = onLogin,
                    onOpenConfig = onOpenConfig,
                    onOpenDetail = onOpenDetail,
                    onBuy = onBuy,
                    onRenew = onRenew,
                    onShowSnackbar = onShowSnackbar
                )
            } else {
                LegacyStoreScreen(
                    prefs = prefs,
                    configManager = configManager,
                    subscriptionInfo = subscriptionInfo,
                    onRefreshSub = onRefreshSub,
                    onShowSnackbar = onShowSnackbar
                )
            }
        }
    }
}
