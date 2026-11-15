package com.haio.bypass.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.haio.bypass.network.haiocloud.HaioGateAccount
import com.haio.bypass.network.haiocloud.HaioGateAccountDetail
import com.haio.bypass.network.haiocloud.HaioGatePackage
import com.haio.bypass.network.haiocloud.OrganizationOption
import com.haio.bypass.network.haiocloud.PortalContainer
import com.haio.bypass.network.haiocloud.PortalError
import com.haio.bypass.network.haiocloud.PortalException
import com.haio.bypass.network.haiocloud.PurchaseMode
import com.haio.bypass.network.haiocloud.PurchaseStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PurchaseViewModel(private val container: PortalContainer) : ViewModel() {

    data class UiState(
        val mode: PurchaseMode = PurchaseMode.BUY,
        val accountId: String? = null,
        val account: HaioGateAccountDetail? = null,
        val accounts: List<HaioGateAccount> = emptyList(),
        val packages: List<HaioGatePackage> = emptyList(),
        val loading: Boolean = false,
        val submitting: Boolean = false,
        val error: String? = null,
        val selectedPackageId: String? = null,
        val organizationOptions: List<OrganizationOption> = emptyList(),
        val showOrganizationPicker: Boolean = false,
        val openPaymentUrl: String? = null,
        val success: Boolean = false,
        val requiresShahkar: Boolean = false,
        val requiresLogin: Boolean = false,
        val paymentResumed: Boolean = false
    ) {
        val selectedPackage: HaioGatePackage?
            get() = packages.firstOrNull { it.id == selectedPackageId }

        /** A package the user already owns: buying again creates a separate config. */
        val existingAccountForSelection: HaioGateAccount?
            get() = accounts.firstOrNull { it.packageId != null && it.packageId == selectedPackageId }
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun load(mode: PurchaseMode, accountId: String?, force: Boolean = false) {
        if (!force &&
            _state.value.mode == mode &&
            _state.value.accountId == accountId &&
            _state.value.packages.isNotEmpty()
        ) {
            return
        }
        _state.update {
            it.copy(mode = mode, accountId = accountId, loading = true, error = null)
        }
        viewModelScope.launch {
            try {
                val packages = container.haioGate.packages()
                val account = accountId?.takeIf { it.isNotBlank() }?.let {
                    container.haioGate.account(it)
                }
                val accounts = if (mode == PurchaseMode.BUY) {
                    runCatching { container.haioGate.accounts() }.getOrDefault(emptyList())
                } else {
                    emptyList()
                }
                val preselected = when {
                    account?.packageId != null &&
                        packages.any { pkg -> pkg.id == account.packageId } -> account.packageId
                    packages.size == 1 -> packages.first().id
                    else -> null
                }
                _state.update {
                    it.copy(
                        loading = false,
                        packages = packages,
                        account = account,
                        accounts = accounts,
                        selectedPackageId = it.selectedPackageId ?: preselected,
                        error = null
                    )
                }
            } catch (throwable: Throwable) {
                _state.update { it.copy(loading = false, error = portalMessage(throwable)) }
            }
        }
    }

    fun selectPackage(packageId: String) {
        _state.update { it.copy(selectedPackageId = packageId, error = null) }
    }

    fun confirm(organizationId: String? = null) {
        val state = _state.value
        if (state.submitting) return
        val packageId = state.selectedPackageId
        if (packageId == null) {
            _state.update { it.copy(error = "یک پکیج انتخاب کنید.") }
            return
        }
        if (!container.session.isLoggedIn) {
            _state.update { it.copy(requiresLogin = true) }
            return
        }
        val user = container.session.currentUser
        if (user == null) {
            _state.update { it.copy(error = "اطلاعات حساب در حال بارگذاری است. دوباره تلاش کنید.") }
            return
        }
        if (!user.shahkarVerified) {
            _state.update { it.copy(requiresShahkar = true) }
            return
        }

        _state.update {
            it.copy(
                submitting = true,
                error = null,
                showOrganizationPicker = false,
                requiresShahkar = false,
                requiresLogin = false
            )
        }
        viewModelScope.launch {
            try {
                val result = when (state.mode) {
                    PurchaseMode.BUY -> container.purchase.startBuy(
                        packageId = packageId,
                        organizationId = organizationId
                    )
                    PurchaseMode.RENEW -> container.purchase.startRenew(
                        accountId = state.accountId ?: "",
                        packageId = packageId,
                        organizationId = organizationId,
                        includedUsers = state.selectedPackage?.usersCount
                    )
                }
                when (result) {
                    is PurchaseStart.Active -> _state.update {
                        it.copy(submitting = false, success = true)
                    }
                    is PurchaseStart.Payment -> {
                        container.purchase.markBrowserOpened()
                        _state.update {
                            it.copy(
                                submitting = false,
                                openPaymentUrl = result.paymentUrl
                            )
                        }
                    }
                }
            } catch (exception: PortalException) {
                val error = exception.error
                if (error is PortalError.OrganizationRequired) {
                    val options = if (error.organizations.isNotEmpty()) {
                        error.organizations
                    } else {
                        loadOrganizations()
                    }
                    _state.update {
                        it.copy(
                            submitting = false,
                            organizationOptions = options,
                            showOrganizationPicker = true,
                            error = if (options.isEmpty()) "حساب سازمانی پیدا نشد." else null
                        )
                    }
                } else if (error is PortalError.ShahkarRequired) {
                    _state.update { it.copy(submitting = false, requiresShahkar = true) }
                } else if (error is PortalError.Unauthorized) {
                    _state.update { it.copy(submitting = false, requiresLogin = true) }
                } else {
                    _state.update { it.copy(submitting = false, error = portalMessage(exception)) }
                }
            } catch (throwable: Throwable) {
                _state.update { it.copy(submitting = false, error = portalMessage(throwable)) }
            }
        }
    }

    private suspend fun loadOrganizations(): List<OrganizationOption> = try {
        container.orgs.organizations().mapNotNull { org ->
            val id = org.id?.toString() ?: return@mapNotNull null
            OrganizationOption(id = id, name = org.name.orEmpty())
        }
    } catch (_: Throwable) {
        emptyList()
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    fun consumePaymentUrl() {
        _state.update { it.copy(openPaymentUrl = null) }
    }

    fun consumeSuccess() {
        _state.update { it.copy(success = false) }
    }

    fun consumeRequiresLogin() {
        _state.update { it.copy(requiresLogin = false) }
    }

    fun consumeRequiresShahkar() {
        _state.update { it.copy(requiresShahkar = false) }
    }

    fun dismissOrganizationPicker() {
        _state.update { it.copy(showOrganizationPicker = false) }
    }
}
