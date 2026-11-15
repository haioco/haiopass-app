package com.haio.bypass.ui

import android.content.Intent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.flow.StateFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.haio.bypass.HaioPrefs
import com.haio.bypass.R
import com.haio.bypass.config.SubscriptionInfo
import com.haio.bypass.network.haiocloud.PortalContainer
import com.haio.bypass.network.haiocloud.PortalUser
import com.haio.bypass.network.haiocloud.PurchaseMode
import com.haio.bypass.network.haiocloud.SessionState
import com.haio.bypass.network.api.ApiClient
import com.haio.bypass.network.api.DeviceStatusRequest
import com.haio.bypass.network.api.toSubscriptionInfo
import com.haio.bypass.domain.DomainStore
import com.haio.bypass.proxy.ProxyManager
import com.haio.bypass.service.HaioVpnService
import com.haio.bypass.ui.screens.AccountScreen
import com.haio.bypass.ui.screens.ConfigScreen
import com.haio.bypass.ui.screens.DomainsScreen
import com.haio.bypass.ui.screens.HaioGateDetailScreen
import com.haio.bypass.ui.screens.LoginScreen
import com.haio.bypass.ui.screens.MainScreen
import com.haio.bypass.ui.screens.MembersScreen
import com.haio.bypass.ui.screens.PackagePickerScreen
import com.haio.bypass.ui.screens.SettingsScreen
import com.haio.bypass.ui.screens.ShahkarScreen
import com.haio.bypass.ui.screens.StoreHubScreen
import com.haio.bypass.ui.screens.WelcomeScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

sealed class Screen(val route: String, val title: String) {
    data object Main : Screen("main", "خانه")
    data object Domains : Screen("domains", "عبور مستقیم")
    data object Store : Screen("store", "فروشگاه")
    data object Settings : Screen("settings", "تنظیمات")
    data object Config : Screen("config", "تنظیمات روتر ابری")
    data object Welcome : Screen("welcome", "خوش آمدید")
    data object Login : Screen("login", "ورود به حساب")
    data object Account : Screen("account", "حساب هایو")
    data object Shahkar : Screen("shahkar", "احراز هویت شاهکار")
    data object HaioGateDetail : Screen("haiogate/detail/{accountId}", "جزئیات اکانت")
    data object PackagePicker : Screen(
        "haiogate/packages?mode={mode}&accountId={accountId}",
        "انتخاب پکیج"
    )
    data object Members : Screen("haiogate/members/{accountId}", "کاربران کانفیگ")
}

private val HIDDEN_BOTTOM_BAR_ROUTES = setOf(
    Screen.Config.route,
    Screen.Welcome.route,
    Screen.Login.route,
    Screen.Account.route,
    Screen.Shahkar.route,
    Screen.HaioGateDetail.route,
    Screen.PackagePicker.route,
    Screen.Members.route
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HaioBypassApp(
    prefs: HaioPrefs,
    requestVpnPermission: (onResult: (Boolean) -> Unit) -> Unit,
    externalSnackMessage: StateFlow<String?>,
    purchaseRefreshTrigger: Int = 0,
    themeMode: String = HaioPrefs.THEME_SYSTEM,
    onThemeModeChange: (String) -> Unit = {}
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        val context = LocalContext.current
        val container = remember { PortalContainer.get(context) }
        val configManager = container.configManager
        val domainStore = remember { DomainStore.getInstance(context) }
        val scope = rememberCoroutineScope()
        val navController = rememberNavController()

        val config by configManager.config.collectAsState()
        val domainCount by domainStore.domainCount.collectAsState()
        val excludedCount by domainStore.excludedDomains.collectAsState()
        val sessionState by container.session.state.collectAsState()

        val isLoggedIn = sessionState is SessionState.LoggedIn
        val user: PortalUser? = (sessionState as? SessionState.LoggedIn)?.user

        val proxyState by ProxyManager.proxyState.collectAsState()
        val proxyStatusMessage by ProxyManager.statusMessage.collectAsState()

        var subscriptionInfo by remember { mutableStateOf<SubscriptionInfo?>(null) }
        var isRefreshingSub by remember { mutableStateOf(false) }
        var subFetchError by remember { mutableStateOf<String?>(null) }
        var refreshTrigger by remember { mutableIntStateOf(0) }

        // A HaioGate config must not be overwritten by the legacy subscription
        // refresh (same rule guards the 30s loop below).
        suspend fun fetchSubscription() {
            if (prefs.isHaioGateConfigActive) return
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
                    if (stillConnected && prefs.subscriptionUuid != null && !prefs.isHaioGateConfigActive) {
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

        val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
        val showBottomBar = !currentRoute.isNullOrEmpty() && currentRoute !in HIDDEN_BOTTOM_BAR_ROUTES

        fun navigateSingleTop(route: String) {
            navController.navigate(route) {
                launchSingleTop = true
            }
        }

        fun openLogin() = navigateSingleTop(Screen.Login.route)

        fun openPackages(mode: PurchaseMode, accountId: String?) {
            val accountSuffix = if (accountId.isNullOrBlank()) "" else "&accountId=$accountId"
            navController.navigate("haiogate/packages?mode=${mode.name}$accountSuffix")
        }

        val startDestination = remember {
            if (!prefs.welcomeSeen && configManager.getConfig().trojanConfig == null) {
                Screen.Welcome.route
            } else {
                Screen.Main.route
            }
        }

        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        val appVersion = try {
                            context.packageManager
                                .getPackageInfo(context.packageName, 0).versionName
                        } catch (_: Exception) {
                            null
                        }
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "HaioBypass",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 20.sp
                            )
                            if (appVersion != null) {
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "v$appVersion",
                                    fontWeight = FontWeight.Normal,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f)
                                )
                            }
                        }
                    },
                    actions = {
                        if (currentRoute != Screen.Welcome.route &&
                            currentRoute != Screen.Login.route &&
                            sessionState !is SessionState.Restoring
                        ) {
                            Surface(
                                onClick = {
                                    navigateSingleTop(
                                        if (isLoggedIn) Screen.Account.route else Screen.Login.route
                                    )
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Person,
                                        contentDescription = "حساب هایو",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = if (isLoggedIn) {
                                            user?.mobile.orEmpty().ifBlank { "حساب" }
                                        } else {
                                            stringResource(R.string.guest_badge)
                                        },
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
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
                            Triple(Screen.Domains, Icons.Default.Language, "عبور مستقیم"),
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
                startDestination = startDestination,
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
                composable(Screen.Welcome.route) {
                    WelcomeScreen(
                        onLogin = {
                            prefs.markWelcomeSeen()
                            navController.navigate(Screen.Login.route)
                        },
                        onGuest = {
                            prefs.markWelcomeSeen()
                            navController.navigate(Screen.Config.route)
                        },
                        onLater = {
                            prefs.markWelcomeSeen()
                            navController.navigate(Screen.Main.route) {
                                popUpTo(Screen.Welcome.route) { inclusive = true }
                            }
                        }
                    )
                }

                composable(Screen.Main.route) {
                    MainScreen(
                        config = config,
                        isVpnRunning = isVpnRunning,
                        statusMessage = statusMessage,
                        domainCount = domainCount,
                        excludedCount = excludedCount.size,
                        subscriptionInfo = if (prefs.isHaioGateConfigActive) null else subscriptionInfo,
                        isRefreshingSub = isRefreshingSub,
                        subFetchError = subFetchError,
                        onRefreshAll = { refreshTrigger++ },
                        onRefreshSub = { scope.launch { fetchSubscription() } },
                        onToggleVpn = { shouldStart ->
                            if (shouldStart) {
                                if (container.configManager.getConfig().trojanConfig != null) {
                                    requestVpnPermission { granted ->
                                        if (granted) {
                                            val serviceIntent = Intent(context, HaioVpnService::class.java)
                                            context.startForegroundService(serviceIntent)
                                        }
                                    }
                                } else {
                                    scope.launch {
                                        snackbarHostState.showSnackbar("ابتدا روتر ابری را تنظیم کنید")
                                    }
                                    navController.navigate(Screen.Config.route)
                                }
                            } else {
                                val stopIntent = HaioVpnService.createStopIntent(context)
                                context.startService(stopIntent)
                            }
                        },
                        onRefreshDomains = {
                            scope.launch {
                                val fetcher = com.haio.bypass.domain.DomainFetcher(context)
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
                        },
                        onManageDomainsClick = {
                            navController.navigate(Screen.Domains.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        configSourceLabel = when {
                            config.trojanConfig == null -> null
                            prefs.activeConfigSource == HaioPrefs.SOURCE_HAIOGATE -> "هایو گیت"
                            prefs.activeConfigSource == HaioPrefs.SOURCE_LEGACY -> "پلن قبلی"
                            else -> if (isLoggedIn) "دستی" else "مهمان"
                        },
                        isGuest = !isLoggedIn,
                        onLoginClick = { openLogin() }
                    )
                }

                composable(Screen.Config.route) {
                    ConfigScreen(
                        configManager = configManager,
                        onSave = {
                            val previous = navController.previousBackStackEntry?.destination?.route
                            if (previous == Screen.Welcome.route) {
                                navController.navigate(Screen.Main.route) {
                                    popUpTo(Screen.Welcome.route) { inclusive = true }
                                }
                            } else {
                                navController.popBackStack()
                            }
                        },
                        onShowSnackbar = { msg ->
                            scope.launch { snackbarHostState.showSnackbar(msg) }
                        },
                        onConfigApplied = {
                            prefs.activeConfigSource = HaioPrefs.SOURCE_MANUAL
                            prefs.haiogateAccountId = null
                        }
                    )
                }

                composable(Screen.Domains.route) {
                    DomainsScreen(
                        domainStore = domainStore,
                        onRefresh = {
                            scope.launch {
                                val fetcher = com.haio.bypass.domain.DomainFetcher(context)
                                val domains = fetcher.fetch()
                                domainStore.setDomains(domains)
                            }
                        }
                    )
                }

                composable(Screen.Store.route) {
                    StoreHubScreen(
                        container = container,
                        prefs = prefs,
                        configManager = configManager,
                        subscriptionInfo = subscriptionInfo,
                        onRefreshSub = { scope.launch { fetchSubscription() } },
                        onLogin = { openLogin() },
                        onOpenConfig = { navController.navigate(Screen.Config.route) },
                        onOpenDetail = { accountId ->
                            navController.navigate("haiogate/detail/$accountId")
                        },
                        onBuy = { openPackages(PurchaseMode.BUY, null) },
                        onRenew = { accountId ->
                            openPackages(PurchaseMode.RENEW, accountId)
                        },
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
                            prefs.activeConfigSource = HaioPrefs.SOURCE_MANUAL
                            prefs.haiogateAccountId = null
                        },
                        themeMode = themeMode,
                        onThemeModeChange = onThemeModeChange,
                        isLoggedIn = isLoggedIn,
                        accountTitle = user?.title,
                        accountSubtitle = user?.mobile,
                        shahkarVerified = user?.shahkarVerified == true,
                        onAccountClick = { navigateSingleTop(Screen.Account.route) },
                        onLoginClick = { openLogin() }
                    )
                }

                composable(Screen.Login.route) {
                    LoginScreen(
                        container = container,
                        onLoggedIn = {
                            scope.launch {
                                snackbarHostState.showSnackbar("به هایو خوش آمدید")
                            }
                            val previous = navController.previousBackStackEntry?.destination?.route
                            if (previous == null || previous == Screen.Welcome.route) {
                                navController.navigate(Screen.Store.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        inclusive = true
                                    }
                                    launchSingleTop = true
                                }
                            } else {
                                // Return to the screen that requested login (store,
                                // account, or the package picker).
                                navController.popBackStack()
                            }
                        },
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(Screen.Account.route) {
                    AccountScreen(
                        container = container,
                        onLogin = { navigateSingleTop(Screen.Login.route) },
                        onShahkar = { navigateSingleTop(Screen.Shahkar.route) },
                        onLoggedOut = {
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    "از حساب خارج شدید. کانفیگ فعلی شما حفظ شد."
                                )
                            }
                            navController.popBackStack(Screen.Main.route, inclusive = false)
                        },
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(Screen.Shahkar.route) {
                    ShahkarScreen(
                        container = container,
                        onVerified = {
                            scope.launch {
                                snackbarHostState.showSnackbar("هویت شما با موفقیت تایید شد.")
                            }
                            navController.popBackStack()
                        },
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(
                    route = Screen.HaioGateDetail.route,
                    arguments = listOf(navArgument("accountId") { type = NavType.StringType })
                ) { entry ->
                    val accountId = entry.arguments?.getString("accountId").orEmpty()
                    HaioGateDetailScreen(
                        container = container,
                        accountId = accountId,
                        onBack = { navController.popBackStack() },
                        onRenew = { id -> openPackages(PurchaseMode.RENEW, id) },
                        onManageMembers = { id ->
                            navController.navigate("haiogate/members/$id")
                        },
                        onShowSnackbar = { msg ->
                            scope.launch { snackbarHostState.showSnackbar(msg) }
                        }
                    )
                }

                composable(
                    route = Screen.PackagePicker.route,
                    arguments = listOf(
                        navArgument("mode") {
                            type = NavType.StringType
                            defaultValue = PurchaseMode.BUY.name
                        },
                        navArgument("accountId") {
                            type = NavType.StringType
                            defaultValue = ""
                        }
                    )
                ) { entry ->
                    val mode = runCatching {
                        PurchaseMode.valueOf(entry.arguments?.getString("mode") ?: "")
                    }.getOrDefault(PurchaseMode.BUY)
                    val accountId = entry.arguments?.getString("accountId")
                        ?.takeIf { it.isNotBlank() }
                    PackagePickerScreen(
                        container = container,
                        mode = mode,
                        accountId = accountId,
                        onBack = { navController.popBackStack() },
                        onRequireLogin = { navigateSingleTop(Screen.Login.route) },
                        onRequireShahkar = { navigateSingleTop(Screen.Shahkar.route) },
                        onPaymentStarted = {
                            val popped = navController.popBackStack(Screen.Store.route, false)
                            if (!popped) {
                                navController.navigate(Screen.Store.route) { launchSingleTop = true }
                            }
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    "پس از پرداخت، به برنامه بازگردید تا وضعیت بررسی شود."
                                )
                            }
                        },
                        onSuccess = { renewedAccountId ->
                            if (renewedAccountId != null) {
                                // Immediate renewal: jump straight to member onboarding.
                                navController.navigate("haiogate/members/$renewedAccountId") {
                                    popUpTo(Screen.Store.route) { inclusive = false }
                                }
                            } else {
                                val popped = navController.popBackStack(Screen.Store.route, false)
                                if (!popped) {
                                    navController.navigate(Screen.Store.route) { launchSingleTop = true }
                                }
                            }
                            scope.launch {
                                snackbarHostState.showSnackbar("اکانت شما فعال شد.")
                            }
                        },
                        onShowSnackbar = { msg ->
                            scope.launch { snackbarHostState.showSnackbar(msg) }
                        }
                    )
                }

                composable(
                    route = Screen.Members.route,
                    arguments = listOf(navArgument("accountId") { type = NavType.StringType })
                ) { entry ->
                    val accountId = entry.arguments?.getString("accountId").orEmpty()
                    MembersScreen(
                        container = container,
                        accountId = accountId,
                        onBack = { navController.popBackStack() },
                        onRenew = { id ->
                            navController.navigate(
                                "haiogate/packages?mode=${PurchaseMode.RENEW.name}&accountId=$id"
                            )
                        },
                        onShowSnackbar = { msg ->
                            scope.launch { snackbarHostState.showSnackbar(msg) }
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
