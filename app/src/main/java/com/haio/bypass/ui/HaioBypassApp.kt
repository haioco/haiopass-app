package com.haio.bypass.ui

import android.content.Intent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.haio.bypass.config.ConfigManager
import com.haio.bypass.domain.DomainStore
import com.haio.bypass.proxy.ProxyManager
import com.haio.bypass.service.HaioVpnService
import com.haio.bypass.ui.screens.ConfigScreen
import com.haio.bypass.ui.screens.DomainsScreen
import com.haio.bypass.ui.screens.MainScreen
import com.haio.bypass.ui.screens.SettingsScreen
import kotlinx.coroutines.launch

sealed class Screen(val route: String, val title: String) {
    data object Main : Screen("main", "Main")
    data object Config : Screen("config", "Config")
    data object Domains : Screen("domains", "Domains")
    data object Settings : Screen("settings", "Settings")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HaioBypassApp(
    requestVpnPermission: (onResult: (Boolean) -> Unit) -> Unit
) {
    val context = LocalContext.current
    val configManager = remember { ConfigManager(context) }
    val domainStore = remember { DomainStore(context) }
    val scope = rememberCoroutineScope()
    val navController = rememberNavController()

    val config by configManager.config.collectAsState()
    val domainCount by domainStore.domainCount.collectAsState()

    val proxyState by ProxyManager.proxyState.collectAsState()
    val proxyStatusMessage by ProxyManager.statusMessage.collectAsState()

    val isVpnRunning by remember(proxyState) {
        derivedStateOf {
            proxyState == ProxyManager.ProxyState.CONNECTED || proxyState == ProxyManager.ProxyState.CONNECTING
        }
    }

    val statusMessage by remember(proxyStatusMessage, proxyState) {
        derivedStateOf {
            when (proxyState) {
                ProxyManager.ProxyState.CONNECTING -> "Connecting..."
                ProxyManager.ProxyState.CONNECTED -> proxyStatusMessage.ifEmpty { "Connected" }
                ProxyManager.ProxyState.DISCONNECTED -> "Disconnected"
            }
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "HaioBypass",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 20.sp
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                val items = listOf(
                    Triple(Screen.Main, Icons.Default.Home, "Main"),
                    Triple(Screen.Config, Icons.Default.VpnKey, "Config"),
                    Triple(Screen.Domains, Icons.Default.Language, "Domains"),
                    Triple(Screen.Settings, Icons.Default.Settings, "Settings")
                )

                items.forEach { (screen, icon, label) ->
                    NavigationBarItem(
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        )
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Main.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Main.route) {
                MainScreen(
                    config = config,
                    isVpnRunning = isVpnRunning,
                    statusMessage = statusMessage,
                    domainCount = domainCount,
                    onToggleVpn = { shouldStart ->
                        if (shouldStart) {
                            if (config.trojanConfig == null) {
                                scope.launch {
                                    snackbarHostState.showSnackbar("Configure Trojan URL first")
                                }
                                navController.navigate(Screen.Config.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                                return@MainScreen
                            }
                            requestVpnPermission { granted ->
                                if (granted) {
                                    val serviceIntent = Intent(context, HaioVpnService::class.java)
                                    context.startForegroundService(serviceIntent)
                                }
                            }
                        } else {
                            val stopIntent = HaioVpnService.createStopIntent(context)
                            context.startService(stopIntent)
                        }
                    },
                    onRefreshDomains = {
                        domainStore.setDomains(domainStore.getDomains())
                    },
                    onConfigureClick = {
                        navController.navigate(Screen.Config.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }

            composable(Screen.Config.route) {
                ConfigScreen(
                    configManager = configManager,
                    onSave = {
                        scope.launch {
                            snackbarHostState.showSnackbar("Config saved")
                        }
                    }
                )
            }

            composable(Screen.Domains.route) {
                DomainsScreen(
                    domainStore = domainStore,
                    onRefresh = {
                        scope.launch {
                            val fetcher = com.haio.bypass.domain.DomainFetcher()
                            val domains = fetcher.fetch()
                            domainStore.setDomains(domains)
                        }
                    }
                )
            }

            composable(Screen.Settings.route) {
                SettingsScreen(
                    configManager = configManager,
                    onClearConfig = {
                        configManager.clear()
                    }
                )
            }
        }
    }
}