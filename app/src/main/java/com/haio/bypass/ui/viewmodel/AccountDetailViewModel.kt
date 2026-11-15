package com.haio.bypass.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.haio.bypass.HaioPrefs
import com.haio.bypass.config.TrojanUrlParser
import com.haio.bypass.network.haiocloud.HaioGateAccountDetail
import com.haio.bypass.network.haiocloud.PortalContainer
import com.haio.bypass.network.haiocloud.PortalError
import com.haio.bypass.network.haiocloud.PortalException
import com.haio.bypass.network.haiocloud.PortalMember
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AccountDetailViewModel(private val container: PortalContainer) : ViewModel() {

    data class UiState(
        val detail: HaioGateAccountDetail? = null,
        val loading: Boolean = false,
        val syncing: Boolean = false,
        val applying: Boolean = false,
        val memberBusy: Boolean = false,
        val revealPrimaryPassword: Boolean = false,
        val revealMemberId: String? = null,
        val error: String? = null,
        val notice: String? = null,
        val appliedConfig: Boolean = false,
        val newCredential: PortalMember? = null
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun load(accountId: String) {
        if (_state.value.loading && _state.value.detail?.id == accountId) return
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val detail = container.haioGate.account(accountId)
                _state.update { it.copy(loading = false, detail = detail) }
            } catch (throwable: Throwable) {
                _state.update { it.copy(loading = false, error = portalMessage(throwable)) }
            }
        }
    }

    fun syncQuota(accountId: String) {
        if (_state.value.syncing) return
        viewModelScope.launch {
            _state.update { it.copy(syncing = true, error = null) }
            try {
                val detail = container.haioGate.syncQuota(accountId)
                _state.update {
                    it.copy(
                        syncing = false,
                        detail = detail,
                        notice = "مصرف به‌روزرسانی شد."
                    )
                }
            } catch (exception: PortalException) {
                val message = if (exception.error is PortalError.Http) {
                    "به‌روزرسانی مصرف در دسترس نیست. دوباره تلاش کنید."
                } else {
                    portalMessage(exception)
                }
                _state.update { it.copy(syncing = false, error = message) }
            } catch (throwable: Throwable) {
                _state.update { it.copy(syncing = false, error = portalMessage(throwable)) }
            }
        }
    }

    /** Parses `trojan_url` and makes it the active config (source HAIOGATE). */
    fun applyConfig(): Boolean {
        val detail = _state.value.detail
        val url = detail?.trojanUrl?.trim().orEmpty()
        val parsed = url.takeIf { it.isNotEmpty() }?.let { TrojanUrlParser.parse(it) }
        if (detail == null || parsed == null) {
            _state.update {
                it.copy(error = "لینک اتصال این اکانت معتبر نیست. با پشتیبانی تماس بگیرید.")
            }
            return false
        }
        container.configManager.updateConfig {
            it.copy(trojanUrl = url, trojanConfig = parsed)
        }
        container.prefs.activeConfigSource = HaioPrefs.SOURCE_HAIOGATE
        container.prefs.haiogateAccountId = detail.id
        _state.update {
            it.copy(
                applying = false,
                appliedConfig = true,
                notice = "کانفیگ هایو گیت روی اپ اعمال شد. برای اتصال، دکمه اتصال را بزنید."
            )
        }
        return true
    }

    fun togglePrimaryPassword() {
        _state.update { it.copy(revealPrimaryPassword = !it.revealPrimaryPassword) }
    }

    fun toggleMemberPassword(memberId: String) {
        _state.update {
            it.copy(revealMemberId = if (it.revealMemberId == memberId) null else memberId)
        }
    }

    fun addMember(displayName: String, mobile: String, email: String) {
        val accountId = _state.value.detail?.id ?: return
        if (displayName.isBlank()) {
            _state.update { it.copy(error = "نام کاربر را وارد کنید.") }
            return
        }
        if (_state.value.memberBusy) return
        viewModelScope.launch {
            _state.update { it.copy(memberBusy = true, error = null) }
            try {
                val member = container.haioGate.addMember(
                    accountId = accountId,
                    displayName = displayName.trim(),
                    mobile = mobile.trim().ifBlank { null },
                    email = email.trim().ifBlank { null }
                )
                _state.update {
                    it.copy(
                        memberBusy = false,
                        notice = "کاربر «${member.name}» اضافه شد.",
                        newCredential = member
                    )
                }
                reload(accountId)
            } catch (throwable: Throwable) {
                _state.update { it.copy(memberBusy = false, error = memberErrorMessage(throwable)) }
            }
        }
    }

    fun rotateMember(memberId: String) {
        val accountId = _state.value.detail?.id ?: return
        if (_state.value.memberBusy) return
        viewModelScope.launch {
            _state.update { it.copy(memberBusy = true, error = null) }
            try {
                val member = container.haioGate.rotateMember(accountId, memberId)
                _state.update {
                    it.copy(
                        memberBusy = false,
                        notice = "لینک قبلی غیرفعال شد؛ لینک جدید آماده است.",
                        newCredential = member
                    )
                }
                reload(accountId)
            } catch (throwable: Throwable) {
                _state.update { it.copy(memberBusy = false, error = memberErrorMessage(throwable)) }
            }
        }
    }

    fun revokeMember(memberId: String) {
        val accountId = _state.value.detail?.id ?: return
        if (_state.value.memberBusy) return
        viewModelScope.launch {
            _state.update { it.copy(memberBusy = true, error = null) }
            try {
                container.haioGate.revokeMember(accountId, memberId)
                _state.update {
                    it.copy(
                        memberBusy = false,
                        notice = "کاربر حذف شد و لینک قبلی او غیرفعال است."
                    )
                }
                reload(accountId)
            } catch (throwable: Throwable) {
                _state.update { it.copy(memberBusy = false, error = memberErrorMessage(throwable)) }
            }
        }
    }

    fun clearNewCredential() {
        _state.update { it.copy(newCredential = null) }
    }

    fun consumeNotice() {
        _state.update { it.copy(notice = null) }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    private suspend fun reload(accountId: String) {
        try {
            val detail = container.haioGate.account(accountId)
            _state.update { it.copy(detail = detail) }
        } catch (_: Throwable) {
            // Keep the previous detail; the mutation already succeeded.
        }
    }

    private fun memberErrorMessage(throwable: Throwable): String {
        val error = (throwable as? PortalException)?.error
        return when {
            error is PortalError.Http && error.code == 404 ->
                "مدیریت کاربران برای این اکانت فعال نیست. با پشتیبانی تماس بگیرید."
            error is PortalError.Http && error.code == 409 ->
                error.detail ?: "ظرفیت این پلن تکمیل است."
            else -> portalMessage(throwable)
        }
    }
}
