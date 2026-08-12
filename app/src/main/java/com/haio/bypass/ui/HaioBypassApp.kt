package com.haio.bypass.ui

import android.content.Intent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.flow.StateFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.haio.bypass.HaioPrefs
import com.haio.bypass.config.ConfigManager
import com.haio.bypass.config.SubscriptionInfo
import com.haio.bypass.network.api.ApiClient
import com.haio.bypass.network.api.DeviceStatusRequest
import com.haio.bypass.domain.DomainStore
import com.haio.bypass.proxy.ProxyManager
import com.haio.bypass.service.HaioVpnService
import com.haio.bypass.ui.screens.ConfigScreen
import com.haio.bypass.ui.screens.DomainsScreen
import com.haio.bypass.ui.screens.MainScreen
import com.haio.bypass.ui.screens.SettingsScreen
import com.haio.bypass.ui.screens.StoreScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

sealed class Screen(val route: String, val title: String) {
    data object Main : Screen("main", "خانه")
    data object Domains : Screen("domains", "دامنه‌ها")
    data object Store : Screen("store", "فروشگاه")
    data object Settings : Screen("settings", "تنظیمات")
    data object Config : Screen("config", "تنظیمات تروجان")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HaioBypassApp(
    prefs: HaioPrefs,
    requestVpnPermission: (onResult: (Boolean) -> Unit) -> Unit,
    externalSnackMessage: StateFlow<String?>,
    purchaseRefreshTrigger: Int = 0
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        val context = LocalContext.current
        val configManager = remember { ConfigManager(context) }
        val domainStore = remember { DomainStore(context) }
        val scope = rememberCoroutineScope()
        val navController = rememberNavController()

        val config by configManager.config.collectAsState()
        val domainCount by domainStore.domainCount.collectAsState()

        val proxyState by ProxyManager.proxyState.collectAsState()
        val proxyStatusMessage by ProxyManager.statusMessage.collectAsState()

        var subscriptionInfo by remember { mutableStateOf<SubscriptionInfo?>(null) }
        var isRefreshingSub by remember { mutableStateOf(false) }
        var subFetchError by remember { mutableStateOf<String?>(null) }
        var refreshTrigger by remember { mutableIntStateOf(0) }

        suspend fun fetchSubscription() {
            val uuid = prefs.subscriptionUuid ?: return
            isRefreshingSub = true
            subFetchError = null

            val stored = SubscriptionInfo(
                title = prefs.subscriptionTitle ?: "",
                planName = "",
                isPaid = false,
                trafficTotalMb = prefs.subscriptionTrafficMb,
                trafficUsedMb = prefs.subscriptionTrafficUsedMb,
                trafficPercent = if (prefs.subscriptionTrafficMb > 0)
                    (prefs.subscriptionTrafficUsedMb / prefs.subscriptionTrafficMb) * 100f
                else 0f,
                expiryDate = prefs.subscriptionExpiry ?: ""
            )
            if (subscriptionInfo == null) subscriptionInfo = stored

            try {
                val accessToken = prefs.jwtAccessToken
                val response = if (!accessToken.isNullOrBlank()) {
                    ApiClient.setAccessToken(accessToken)
                    ApiClient.getApiService().subscriptionDetail(uuid)
                } else {
                    val deviceId = prefs.deviceId
                    ApiClient.getApiService().subscriptionDeviceStatus(
                        uuid, DeviceStatusRequest(deviceId = deviceId)
                    )
                }
                if (response.isSuccessful) {
                    val sub = response.body()!!
                    val info = sub.toSubscriptionInfo()
                    subscriptionInfo = info
                    prefs.subscriptionTitle = sub.title
                    prefs.subscriptionTrafficMb = sub.trafficTotalMb.toFloat()
                    prefs.subscriptionTrafficUsedMb = sub.trafficUsageMb.toFloat()
                    prefs.subscriptionExpiry = sub.expiredAt
                    subFetchError = null
                } else if (response.code() == 401 && !accessToken.isNullOrBlank()) {
                    val refreshed = ApiClient.tryRefreshAccessToken(prefs)
                    if (refreshed != null) {
                        val retry = ApiClient.getApiService().subscriptionDetail(uuid)
                        if (retry.isSuccessful) {
                            val sub = retry.body()!!
                            val info = sub.toSubscriptionInfo()
                            subscriptionInfo = info
                            prefs.subscriptionTitle = sub.title
                            prefs.subscriptionTrafficMb = sub.trafficTotalMb.toFloat()
                            prefs.subscriptionTrafficUsedMb = sub.trafficUsageMb.toFloat()
                            prefs.subscriptionExpiry = sub.expiredAt
                        }
                    }
                }
            } catch (_: Exception) {
                subFetchError = "خطا در دریافت اطلاعات اشتراک"
            }
            isRefreshingSub = false
        }

        LaunchedEffect(prefs.subscriptionUuid) {
            fetchSubscription()
        }

        LaunchedEffect(refreshTrigger) {
            if (refreshTrigger > 0) fetchSubscription()
        }

        LaunchedEffect(purchaseRefreshTrigger) {
            if (purchaseRefreshTrigger > 0) fetchSubscription()
        }

        LaunchedEffect(proxyState) {
            if (proxyState == ProxyManager.ProxyState.CONNECTED) {
                while (true) {
                    delay(30_000L)
                    val stillConnected = ProxyManager.proxyState.value == ProxyManager.ProxyState.CONNECTED
                    if (stillConnected && prefs.subscriptionUuid != null) {
                        fetchSubscription()
                    } else {
                        break
                    }
                }
            }
        }

        val isVpnRunning by remember(proxyState) {
            derivedStateOf {
                proxyState == ProxyManager.ProxyState.CONNECTED || proxyState == ProxyManager.ProxyState.CONNECTING
            }
        }

        val statusMessage by remember(proxyStatusMessage, proxyState) {
            derivedStateOf {
                when (proxyState) {
                    ProxyManager.ProxyState.CONNECTING -> "در حال اتصال..."
                    ProxyManager.ProxyState.CONNECTED -> proxyStatusMessage.ifEmpty { "متصل" }
                    ProxyManager.ProxyState.DISCONNECTED -> "قطع شده"
                }
            }
        }

        val snackbarHostState = remember { SnackbarHostState() }
        val externalMsg by externalSnackMessage.collectAsState()

        LaunchedEffect(externalMsg) {
            val msg = externalMsg ?: return@LaunchedEffect
            snackbarHostState.showSnackbar(msg)
        }

        val isMainScreen = navController.currentBackStackEntryAsState().value
            ?.destination?.hierarchy?.any { it.route == Screen.Main.route } == true
        val showBottomBar = !navController.currentBackStackEntryAsState().value
            ?.destination?.route.isNullOrEmpty() &&
                navController.currentBackStackEntryAsState().value?.destination?.route != Screen.Config.route

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
                if (showBottomBar) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        tonalElevation = 3.dp
                    ) {
                        val navBackStackEntry by navController.currentBackStackEntryAsState()
                        val currentDestination = navBackStackEntry?.destination

                        val items = listOf(
                            Triple(Screen.Main, Icons.Default.Home, "خانه"),
                            Triple(Screen.Store, Icons.Default.ShoppingCart, "فروشگاه"),
                            Triple(Screen.Domains, Icons.Default.Language, "دامنه\u200Cها"),
                            Triple(Screen.Settings, Icons.Default.Settings, "تنظیمات")
                        )

                        items.forEach { (screen, icon, label) ->
                            val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
                            NavigationBarItem(
                                icon = {
                                    Icon(
                                        icon,
                                        contentDescription = label,
                                        modifier = Modifier.padding(bottom = 2.dp)
                                    )
                                },
                                label = {
                                    Text(
                                        label,
                                        fontSize = 11.sp,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                    )
                                },
                                selected = selected,
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
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                )
                            )
                        }
                    }
                }
            },
            snackbarHost = {
                SnackbarHost(snackbarHostState) { data ->
                    Snackbar(
                        snackbarData = data,
                        shape = MaterialTheme.shapes.medium,
                        containerColor = MaterialTheme.colorScheme.inverseSurface,
                        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                        actionColor = MaterialTheme.colorScheme.primary
                    )
                }
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = Screen.Main.route,
                modifier = Modifier.padding(innerPadding),
                enterTransition = {
                    slideInHorizontally(
                        initialOffsetX = { it / 4 },
                        animationSpec = slideTween(300)
                    )
                },
                exitTransition = {
                    slideOutHorizontally(
                        targetOffsetX = { -it / 4 },
                        animationSpec = slideTween(300)
                    )
                },
                popEnterTransition = {
                    slideInHorizontally(
                        initialOffsetX = { -it / 4 },
                        animationSpec = slideTween(300)
                    )
                },
                popExitTransition = {
                    slideOutHorizontally(
                        targetOffsetX = { it / 4 },
                        animationSpec = slideTween(300)
                    )
                }
            ) {
                composable(Screen.Main.route) {
                    MainScreen(
                        config = config,
                        isVpnRunning = isVpnRunning,
                        statusMessage = statusMessage,
                        domainCount = domainCount,
                        subscriptionInfo = subscriptionInfo,
                        isRefreshingSub = isRefreshingSub,
                        subFetchError = subFetchError,
                        onRefreshAll = { refreshTrigger++ },
                        onRefreshSub = { scope.launch { fetchSubscription() } },
                        onToggleVpn = { shouldStart ->
                            if (shouldStart) {
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
                            scope.launch {
                                val fetcher = com.haio.bypass.domain.DomainFetcher()
                                val domains = fetcher.fetch()
                                if (domains.isNotEmpty()) {
                                    domainStore.setDomains(domains)
                                    snackbarHostState.showSnackbar("دامنه\u200Cها به\u200Cروزرسانی شدند: ${domains.size}")
                                } else {
                                    snackbarHostState.showSnackbar("به\u200Cروزرسانی دامنه\u200Cها ناموفق بود")
                                }
                            }
                        },
                        onConfigureClick = {
                            navController.navigate(Screen.Config.route)
                        }
                    )
                }

                composable(Screen.Config.route) {
                    ConfigScreen(
                        configManager = configManager,
                        onSave = { navController.popBackStack() }
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

                composable(Screen.Store.route) {
                    StoreScreen(
                        prefs = prefs,
                        configManager = configManager,
                        subscriptionInfo = subscriptionInfo,
                        onRefreshSub = { scope.launch { fetchSubscription() } },
                        onShowSnackbar = { msg ->
                            scope.launch { snackbarHostState.showSnackbar(msg) }
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
}

private fun slideTween(durationMs: Int) = androidx.compose.animation.core.tween<androidx.compose.ui.unit.IntOffset>(
    durationMillis = durationMs,
    easing = androidx.compose.animation.core.EaseOutCubic
)