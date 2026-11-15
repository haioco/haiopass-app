package com.haio.bypass.network.haiocloud

import android.content.Context
import com.haio.bypass.BuildConfig
import com.haio.bypass.HaioPrefs
import com.haio.bypass.config.ConfigManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Manual dependency graph for the portal client — one instance per process.
 *
 * Construction is cheap and never touches the network: a guest (no tokens in
 * the encrypted store) stays fully local, and only a cached session triggers
 * `auth/me/` validation.
 */
class PortalContainer private constructor(context: Context) {

    private val appContext = context.applicationContext

    val prefs: HaioPrefs = HaioPrefs(appContext)
    val configManager: ConfigManager = ConfigManager(appContext)

    val sessionStore: PortalSessionStore = PortalSessionStore(appContext)
    val session: PortalSession = PortalSession(sessionStore)

    val client: PortalClient = PortalClient(
        baseUrl = BuildConfig.HAIO_PORTAL_BASE_URL,
        store = sessionStore,
        onSessionExpired = { session.expire() }
    )

    val auth: AuthRepository = AuthRepository(client, sessionStore, session)
    val haioGate: HaioGateRepository = HaioGateRepository(client)
    val billing: BillingRepository = BillingRepository(client)
    val orgs: OrgRepository = OrgRepository(client)
    val pendingStore: PendingPurchaseStore = PendingPurchaseStore(appContext)
    val purchase: PurchaseCoordinator = PurchaseCoordinator(haioGate, billing, pendingStore)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        session.restoreFromCache()
        scope.launch {
            auth.restoreSession()
            // Reconcile a purchase that survived process death, once a session exists.
            if (session.isLoggedIn && purchase.currentPending() != null) {
                runCatching { purchase.checkPending() }
            }
        }
    }

    companion object {
        @Volatile
        private var instance: PortalContainer? = null

        fun get(context: Context): PortalContainer {
            return instance ?: synchronized(this) {
                instance ?: PortalContainer(context).also { instance = it }
            }
        }
    }
}
