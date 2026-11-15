package com.haio.bypass.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.haio.bypass.network.haiocloud.PortalContainer
import com.haio.bypass.network.haiocloud.PortalError
import com.haio.bypass.network.haiocloud.PortalException
import com.haio.bypass.network.haiocloud.userMessage

/** Shares the portal container between the HaioGate screens' ViewModels. */
class PortalViewModelFactory(
    private val container: PortalContainer
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val viewModel: ViewModel = when {
            modelClass.isAssignableFrom(AuthViewModel::class.java) -> AuthViewModel(container)
            modelClass.isAssignableFrom(HaioGateViewModel::class.java) -> HaioGateViewModel(container)
            modelClass.isAssignableFrom(AccountDetailViewModel::class.java) ->
                AccountDetailViewModel(container)
            modelClass.isAssignableFrom(ShahkarViewModel::class.java) -> ShahkarViewModel(container)
            modelClass.isAssignableFrom(PurchaseViewModel::class.java) -> PurchaseViewModel(container)
            modelClass.isAssignableFrom(AccountViewModel::class.java) -> AccountViewModel(container)
            else -> error("Unknown ViewModel class: ${modelClass.name}")
        }
        return viewModel as T
    }
}

internal fun portalMessage(throwable: Throwable): String =
    when (throwable) {
        is PortalException -> throwable.error.userMessage()
        else -> PortalError.Unknown(throwable.message).userMessage()
    }
