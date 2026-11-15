package com.haio.bypass.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.haio.bypass.network.haiocloud.AccountClassifier
import com.haio.bypass.network.haiocloud.AccountCategory
import com.haio.bypass.network.haiocloud.HaioGateAccount
import com.haio.bypass.network.haiocloud.HaioGatePackage
import com.haio.bypass.network.haiocloud.PendingPurchase
import com.haio.bypass.network.haiocloud.PendingStatus
import com.haio.bypass.network.haiocloud.PortalContainer
import com.haio.bypass.network.haiocloud.PortalException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HaioGateViewModel(private val container: PortalContainer) : ViewModel() {

    data class UiState(
        val loading: Boolean = false,
        val refreshing: Boolean = false,
        val accounts: List<HaioGateAccount> = emptyList(),
        val packages: List<HaioGatePackage> = emptyList(),
        val error: String? = null,
        val notice: String? = null,
        val pending: PendingPurchase? = null,
        val pendingStatus: PendingStatus? = null,
        val pendingBusy: Boolean = false,
        val paymentUrl: String? = null
    ) {
        val activeAccounts: List<HaioGateAccount>
            get() = AccountClassifier.sortForDisplay(
                accounts.filter { it.category == AccountCategory.ACTIVE },
                AccountCategory.ACTIVE
            )

        val expiredAccounts: List<HaioGateAccount>
            get() = AccountClassifier.sortForDisplay(
                accounts.filter { it.category == AccountCategory.EXPIRED },
                AccountCategory.EXPIRED
            )

        val supportAccounts: List<HaioGateAccount>
            get() = AccountClassifier.sortForDisplay(
                accounts.filter { it.category == AccountCategory.SUPPORT },
                AccountCategory.SUPPORT
            )
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun refresh() {
        if (!container.session.isLoggedIn) {
            _state.update {
                it.copy(
                    loading = false,
                    refreshing = false,
                    accounts = emptyList(),
                    packages = emptyList()
                )
            }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(loading = it.accounts.isEmpty(), refreshing = it.accounts.isNotEmpty(), error = null) }
            try {
                coroutineScope {
                    val accountsDeferred = async { container.haioGate.accounts() }
                    val packagesDeferred = async { container.haioGate.packages() }
                    val accounts = accountsDeferred.await()
                    val packages = packagesDeferred.await()
                    _state.update {
                        it.copy(
                            loading = false,
                            refreshing = false,
                            accounts = accounts,
                            packages = packages,
                            error = null
                        )
                    }
                }
            } catch (throwable: Throwable) {
                _state.update {
                    it.copy(
                        loading = false,
                        refreshing = false,
                        error = portalMessage(throwable)
                    )
                }
            }
            loadPendingInternal(checkNow = false)
        }
    }

    /** Called on every resume: shows the pending banner and expires stale rows. */
    fun loadPending(checkNow: Boolean = false) {
        viewModelScope.launch { loadPendingInternal(checkNow) }
    }

    private suspend fun loadPendingInternal(checkNow: Boolean) {
        val stale = container.purchase.expireIfStale()
        if (stale != null) {
            _state.update {
                it.copy(
                    pending = null,
                    pendingStatus = PendingStatus.Expired(stale),
                    notice = "پرداخت قبلی منقضی شده است."
                )
            }
            return
        }
        val pending = container.purchase.currentPending()
        _state.update { it.copy(pending = pending) }
        if (pending != null && checkNow && container.session.isLoggedIn) {
            checkPending()
        }
    }

    fun checkPending() {
        val pending = container.purchase.currentPending()
        if (pending == null) {
            _state.update { it.copy(pending = null, pendingStatus = null) }
            return
        }
        if (_state.value.pendingBusy) return
        _state.update { it.copy(pendingBusy = true) }
        viewModelScope.launch {
            try {
                val result = container.purchase.checkPending()
                _state.update {
                    it.copy(
                        pendingBusy = false,
                        pending = container.purchase.currentPending(),
                        pendingStatus = result,
                        notice = when (result) {
                            is PendingStatus.Paid -> "پرداخت با موفقیت انجام شد."
                            is PendingStatus.Failed -> "پرداخت لغو شد."
                            is PendingStatus.Expired -> "پرداخت قبلی منقضی شده است."
                            else -> it.notice
                        }
                    )
                }
                if (result is PendingStatus.Paid) refresh()
            } catch (throwable: Throwable) {
                _state.update {
                    it.copy(pendingBusy = false, error = portalMessage(throwable))
                }
            }
        }
    }

    fun resumePayment() {
        if (_state.value.pendingBusy) return
        _state.update { it.copy(pendingBusy = true, error = null) }
        viewModelScope.launch {
            try {
                val url = container.purchase.resumePayment()
                container.purchase.markBrowserOpened()
                _state.update {
                    it.copy(pendingBusy = false, pending = container.purchase.currentPending(), paymentUrl = url)
                }
            } catch (exception: PortalException) {
                _state.update {
                    it.copy(pendingBusy = false, error = portalMessage(exception))
                }
            } catch (throwable: Throwable) {
                _state.update { it.copy(pendingBusy = false, error = portalMessage(throwable)) }
            }
        }
    }

    fun consumePaymentUrl() {
        _state.update { it.copy(paymentUrl = null) }
    }

    fun consumeNotice() {
        _state.update { it.copy(notice = null) }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}
