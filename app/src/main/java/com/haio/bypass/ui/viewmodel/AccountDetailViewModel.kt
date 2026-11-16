package com.haio.bypass.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.haio.bypass.BuildConfig
import com.haio.bypass.HaioPrefs
import com.haio.bypass.config.TrojanUrlParser
import com.haio.bypass.network.haiocloud.HaioGateAccountDetail
import com.haio.bypass.network.haiocloud.InviteToken
import com.haio.bypass.network.haiocloud.PendingInvite
import com.haio.bypass.network.haiocloud.PortalContainer
import com.haio.bypass.network.haiocloud.PortalError
import com.haio.bypass.network.haiocloud.PortalException
import com.haio.bypass.network.haiocloud.PortalMember
import com.haio.bypass.network.haiocloud.inviteErrorMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Account detail + owner device/invite management.
 *
 * Device-locked members never expose a password or trojan URL. The only
 * credential material shown is the single-use invite link returned by the
 * invite endpoints.
 */
class AccountDetailViewModel(private val container: PortalContainer) : ViewModel() {

    data class UiState(
        val detail: HaioGateAccountDetail? = null,
        val loading: Boolean = false,
        val syncing: Boolean = false,
        val applying: Boolean = false,
        val memberBusy: Boolean = false,
        val deviceBusy: Boolean = false,
        val enrollingDevice: Boolean = false,
        val revealPrimaryPassword: Boolean = false,
        val revealMemberId: String? = null,
        val error: String? = null,
        val notice: String? = null,
        val appliedConfig: Boolean = false,
        /** Legacy-only: reusable member credential returned once. */
        val newCredential: PortalMember? = null,
        /** Single-use invite returned by create/rotate; never a password. */
        val invite: InviteToken? = null,
        val pendingInvites: List<PendingInvite> = emptyList()
    ) {
        val isDeviceLocked: Boolean get() = detail?.isDeviceLocked == true
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun load(accountId: String) {
        if (_state.value.loading && _state.value.detail?.id == accountId) return
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val detail = container.haioGate.account(accountId)
                _state.update { it.copy(loading = false, detail = detail) }
                if (detail.isDeviceLocked) loadPendingInvites(accountId)
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
                    it.copy(syncing = false, detail = detail, notice = "مصرف به‌روزرسانی شد.")
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

    /**
     * Makes this account the active config.
     *
     * Device-locked accounts use the stored certificate identity and sing-box;
     * legacy accounts keep the password-only Xray path. A locked account with
     * no local identity must enroll the primary device first.
     */
    fun applyConfig(): Boolean {
        val detail = _state.value.detail ?: return false
        if (detail.isDeviceLocked) {
            val identity = container.deviceIdentity.findForMember(detail.id, null)
            if (identity == null) {
                _state.update {
                    it.copy(
                        error = "این اکانت نیاز به ثبت دستگاه دارد. ابتدا «ثبت این دستگاه» را بزنید."
                    )
                }
                return false
            }
            container.deviceKeys.apply(identity)
            _state.update {
                it.copy(
                    applying = false,
                    appliedConfig = true,
                    notice = "دستگاه ثبت‌شده هایو گیت روی اپ فعال شد. برای اتصال، دکمه اتصال را بزنید."
                )
            }
            return true
        }

        val url = detail.trojanUrl?.trim().orEmpty()
        val parsed = url.takeIf { it.isNotEmpty() }?.let { TrojanUrlParser.parse(it) }
        if (parsed == null) {
            _state.update {
                it.copy(error = "لینک اتصال این اکانت معتبر نیست. با پشتیبانی تماس بگیرید.")
            }
            return false
        }
        container.configManager.updateConfig {
            it.copy(
                trojanUrl = url,
                trojanConfig = parsed,
                core = com.haio.bypass.config.AppConfig.CORE_XRAY,
                deviceIdentityId = null
            )
        }
        container.prefs.activeConfigSource = HaioPrefs.SOURCE_HAIOGATE
        container.prefs.haiogateAccountId = detail.id
        container.prefs.haiogateMemberId = null
        container.prefs.deviceFingerprint = null
        _state.update {
            it.copy(
                applying = false,
                appliedConfig = true,
                notice = "کانفیگ هایو گیت روی اپ اعمال شد. برای اتصال، دکمه اتصال را بزنید."
            )
        }
        return true
    }

    /**
     * Owner primary enrollment: generates a device key, calls the authenticated
     * primary/enroll endpoint (which supersedes an older primary device) and
     * makes the result active.
     */
    fun enrollPrimaryDevice(accountId: String) {
        if (_state.value.enrollingDevice) return
        val detail = _state.value.detail
        if (detail != null && !detail.isDeviceLocked) {
            _state.update { it.copy(error = "این اکانت قدیمی است و به ثبت دستگاه نیاز ندارد.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(enrollingDevice = true, error = null) }
            try {
                val generated = container.deviceKeys.generate()
                val result = container.invites.enrollPrimary(
                    accountId = accountId,
                    publicKeyPem = generated.publicKeyPem,
                    deviceName = deviceName(),
                    appVersion = BuildConfig.VERSION_NAME,
                    requestId = UUID.randomUUID().toString()
                )
                container.deviceKeys.persistAndApply(result, generated, deviceName())
                _state.update {
                    it.copy(
                        enrollingDevice = false,
                        appliedConfig = true,
                        notice = "این دستگاه ثبت شد و کانفیگ هایو گیت فعال است."
                    )
                }
                reload(accountId)
            } catch (throwable: Throwable) {
                _state.update {
                    it.copy(enrollingDevice = false, error = inviteErrorMessage(throwable))
                }
            }
        }
    }

    fun togglePrimaryPassword() {
        _state.update { it.copy(revealPrimaryPassword = !it.revealPrimaryPassword) }
    }

    fun toggleMemberPassword(memberId: String) {
        _state.update {
            it.copy(revealMemberId = if (it.revealMemberId == memberId) null else memberId)
        }
    }

    /**
     * Adds a member. For a device-locked account it passes `issue_invite=true`
     * and surfaces the single-use link; legacy accounts still return the
     * reusable credential once.
     */
    fun addMember(displayName: String, mobile: String, email: String) {
        val detail = _state.value.detail ?: return
        val accountId = detail.id ?: return
        if (displayName.isBlank()) {
            _state.update { it.copy(error = "نام کاربر را وارد کنید.") }
            return
        }
        if (_state.value.memberBusy) return
        viewModelScope.launch {
            _state.update { it.copy(memberBusy = true, error = null) }
            try {
                val result = container.haioGate.addMember(
                    accountId = accountId,
                    displayName = displayName.trim(),
                    mobile = mobile.trim().ifBlank { null },
                    email = email.trim().ifBlank { null },
                    issueInvite = detail.isDeviceLocked
                )
                _state.update {
                    it.copy(
                        memberBusy = false,
                        notice = if (detail.isDeviceLocked) {
                            "کاربر «${result.member.name}» اضافه شد و لینک دعوت آماده است."
                        } else {
                            "کاربر «${result.member.name}» اضافه شد."
                        },
                        invite = result.invite,
                        newCredential = result.member.takeIf { !detail.isDeviceLocked }
                    )
                }
                reload(accountId)
            } catch (throwable: Throwable) {
                _state.update { it.copy(memberBusy = false, error = memberErrorMessage(throwable)) }
            }
        }
    }

    /** Re-issues a member invite (revokes the previous pending link server-side). */
    fun createMemberInvite(memberId: String) {
        val accountId = _state.value.detail?.id ?: return
        if (_state.value.memberBusy) return
        viewModelScope.launch {
            _state.update { it.copy(memberBusy = true, error = null) }
            try {
                val invite = container.invites.createMemberInvite(accountId, memberId)
                _state.update {
                    it.copy(
                        memberBusy = false,
                        invite = invite,
                        notice = "لینک دعوت جدید ساخته شد."
                    )
                }
                reload(accountId)
            } catch (throwable: Throwable) {
                _state.update { it.copy(memberBusy = false, error = memberErrorMessage(throwable)) }
            }
        }
    }

    fun createPrimaryInvite() {
        val accountId = _state.value.detail?.id ?: return
        if (_state.value.memberBusy) return
        viewModelScope.launch {
            _state.update { it.copy(memberBusy = true, error = null) }
            try {
                val invite = container.invites.createPrimaryInvite(accountId)
                _state.update {
                    it.copy(
                        memberBusy = false,
                        invite = invite,
                        notice = "لینک دعوت کاربر اصلی ساخته شد."
                    )
                }
                loadPendingInvites(accountId)
            } catch (throwable: Throwable) {
                _state.update { it.copy(memberBusy = false, error = memberErrorMessage(throwable)) }
            }
        }
    }

    /** Removes the device binding of a member (old device stops on next connect). */
    fun resetMemberDevice(memberId: String) {
        val accountId = _state.value.detail?.id ?: return
        if (_state.value.deviceBusy) return
        viewModelScope.launch {
            _state.update { it.copy(deviceBusy = true, error = null) }
            try {
                container.invites.resetMemberDevice(accountId, memberId)
                _state.update {
                    it.copy(
                        deviceBusy = false,
                        notice = "دستگاه این کاربر حذف شد. اکنون یک لینک دعوت جدید بسازید."
                    )
                }
                reload(accountId)
            } catch (throwable: Throwable) {
                _state.update { it.copy(deviceBusy = false, error = memberErrorMessage(throwable)) }
            }
        }
    }

    /** Removes the primary device binding and the local certificate identity. */
    fun resetPrimaryDevice() {
        val accountId = _state.value.detail?.id ?: return
        if (_state.value.deviceBusy) return
        viewModelScope.launch {
            _state.update { it.copy(deviceBusy = true, error = null) }
            try {
                container.invites.resetPrimaryDevice(accountId)
                container.deviceKeys.forgetPrimary(accountId)
                _state.update {
                    it.copy(
                        deviceBusy = false,
                        appliedConfig = false,
                        notice = "دستگاه اصلی حذف شد. برای اتصال دوباره «ثبت این دستگاه» را بزنید."
                    )
                }
                reload(accountId)
            } catch (throwable: Throwable) {
                _state.update { it.copy(deviceBusy = false, error = memberErrorMessage(throwable)) }
            }
        }
    }

    fun loadPendingInvites(accountId: String) {
        viewModelScope.launch {
            runCatching { container.invites.pendingInvites(accountId) }
                .onSuccess { invites ->
                    _state.update { it.copy(pendingInvites = invites) }
                }
        }
    }

    fun cancelInvite(inviteId: String) {
        val accountId = _state.value.detail?.id ?: return
        viewModelScope.launch {
            try {
                container.invites.cancelInvite(accountId, inviteId)
                _state.update { it.copy(notice = "لینک دعوت لغو شد.") }
                loadPendingInvites(accountId)
            } catch (throwable: Throwable) {
                _state.update { it.copy(error = memberErrorMessage(throwable)) }
            }
        }
    }

    /** Legacy mode only: rotates a reusable member password. */
    fun rotateMember(memberId: String) {
        val accountId = _state.value.detail?.id ?: return
        if (_state.value.memberBusy) return
        viewModelScope.launch {
            _state.update { it.copy(memberBusy = true, error = null) }
            try {
                val result = container.haioGate.rotateMember(accountId, memberId)
                _state.update {
                    it.copy(
                        memberBusy = false,
                        notice = if (result.invite != null) {
                            "دستگاه قبلی حذف شد و لینک دعوت جدید آماده است."
                        } else {
                            "لینک قبلی غیرفعال شد؛ لینک جدید آماده است."
                        },
                        newCredential = result.member.takeIf { result.invite == null },
                        invite = result.invite
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

    fun clearInvite() {
        _state.update { it.copy(invite = null) }
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
            if (detail.isDeviceLocked) loadPendingInvites(accountId)
        } catch (_: Throwable) {
            // Keep the previous detail; the mutation already succeeded.
        }
    }

    private fun memberErrorMessage(throwable: Throwable): String {
        val error = (throwable as? PortalException)?.error
        return when {
            error is PortalError.Http && error.code == 404 ->
                "مدیریت کاربران برای این اکانت فعال نیست. با پشتیبانی تماس بگیرید."
            else -> inviteErrorMessage(throwable)
        }
    }

    private fun deviceName(): String {
        val model = android.os.Build.MODEL.orEmpty()
            .replace(Regex("[\\p{Cntrl}]"), "")
            .trim()
        return model.ifBlank { "Android device" }.take(100)
    }
}
