package com.haio.bypass.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.haio.bypass.network.haiocloud.PortalContainer
import com.haio.bypass.network.haiocloud.PortalUser
import com.haio.bypass.network.haiocloud.SessionState
import kotlinx.coroutines.flow.StateFlow

class AccountViewModel(private val container: PortalContainer) : ViewModel() {

    val sessionState: StateFlow<SessionState> = container.session.state

    val user: PortalUser? get() = container.session.currentUser

    val isLoggedIn: Boolean get() = container.session.isLoggedIn

    fun logout() {
        // Clears portal tokens/cache and the pending-purchase record only;
        // the active VPN config and its source are preserved. Storage failures
        // must never crash the app during logout.
        runCatching { container.purchase.clearPending() }
        runCatching { container.auth.logout() }
    }
}
