package com.haio.bypass.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.haio.bypass.network.haiocloud.PortalContainer
import com.haio.bypass.network.haiocloud.PortalError
import com.haio.bypass.network.haiocloud.PortalException
import com.haio.bypass.network.haiocloud.PortalValidators
import com.haio.bypass.network.haiocloud.userMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AuthViewModel(private val container: PortalContainer) : ViewModel() {

    enum class Step { MOBILE, OTP }

    data class UiState(
        val step: Step = Step.MOBILE,
        val mobile: String = "",
        val otp: String = "",
        val loading: Boolean = false,
        val error: String? = null,
        val info: String? = null,
        val resendIn: Int = 0,
        val lockoutIn: Int = 0,
        val otpLockedOut: Boolean = false
    ) {
        val canSubmit: Boolean
            get() = !loading && !otpLockedOut && lockoutIn == 0
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var countdownJob: Job? = null

    fun onMobileChange(value: String) {
        val normalized = PortalValidators.normalizeMobile(value)
        _state.update { it.copy(mobile = normalized, error = null) }
    }

    fun onOtpChange(value: String) {
        val otp = PortalValidators.normalizeOtp(value)
        _state.update { it.copy(otp = otp, error = null) }
    }

    fun requestOtp() {
        val state = _state.value
        val mobile = PortalValidators.normalizeMobile(state.mobile)
        if (!PortalValidators.isValidMobile(mobile)) {
            _state.update { it.copy(error = "شماره موبایل معتبر نیست. نمونه: ۰۹۱۲۳۴۵۶۷۸۹") }
            return
        }
        if (state.loading) return

        _state.update { it.copy(loading = true, error = null, info = null, mobile = mobile) }
        viewModelScope.launch {
            try {
                val result = container.auth.requestOtp(mobile)
                val resend = result.retryAfter?.takeIf { it > 0 } ?: 30
                _state.update {
                    it.copy(
                        loading = false,
                        step = Step.OTP,
                        otp = "",
                        info = "کد تایید ۶ رقمی ارسال شد",
                        resendIn = resend
                    )
                }
            } catch (exception: PortalException) {
                when (val error = exception.error) {
                    is PortalError.RateLimited -> {
                        val seconds = error.retryAfterSeconds?.takeIf { it > 0 } ?: 300
                        _state.update {
                            it.copy(
                                loading = false,
                                lockoutIn = seconds,
                                resendIn = seconds,
                                error = "برای ارسال دوباره، $seconds ثانیه صبر کنید."
                            )
                        }
                    }
                    is PortalError.ProviderUnavailable, is PortalError.Server -> _state.update {
                        it.copy(
                            loading = false,
                            error = "ارسال پیامک ناموفق بود. دوباره تلاش کنید."
                        )
                    }
                    else -> _state.update {
                        it.copy(loading = false, error = error.userMessage())
                    }
                }
            } catch (throwable: Throwable) {
                _state.update { it.copy(loading = false, error = portalMessage(throwable)) }
            }
            startCountdown()
        }
    }

    fun login() {
        val state = _state.value
        if (!PortalValidators.isValidOtp(state.otp)) {
            _state.update { it.copy(error = "کد تایید باید ۶ رقم باشد.") }
            return
        }
        if (state.loading) return

        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                container.auth.login(state.mobile, state.otp)
                _state.update { it.copy(loading = false, otp = "", info = null) }
            } catch (exception: PortalException) {
                val error = exception.error
                when (error) {
                    is PortalError.RateLimited -> {
                        val seconds = error.retryAfterSeconds?.takeIf { it > 0 } ?: 300
                        _state.update {
                            it.copy(
                                loading = false,
                                otp = "",
                                otpLockedOut = true,
                                lockoutIn = seconds,
                                error = "تعداد تلاش‌ها بیش از حد مجاز است. لطفاً $seconds ثانیه دیگر تلاش کنید."
                            )
                        }
                        startCountdown()
                    }
                    is PortalError.Validation -> _state.update {
                        it.copy(
                            loading = false,
                            otp = "",
                            error = "کد وارد شده نامعتبر یا منقضی شده است."
                        )
                    }
                    else -> _state.update {
                        it.copy(loading = false, otp = "", error = error.userMessage())
                    }
                }
            } catch (throwable: Throwable) {
                _state.update {
                    it.copy(loading = false, otp = "", error = portalMessage(throwable))
                }
            }
        }
    }

    fun backToMobile() {
        countdownJob?.cancel()
        _state.update {
            it.copy(
                step = Step.MOBILE,
                otp = "",
                error = null,
                info = null,
                resendIn = 0,
                lockoutIn = 0,
                otpLockedOut = false
            )
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    fun clearInfo() {
        _state.update { it.copy(info = null) }
    }

    private fun startCountdown() {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            while (_state.value.resendIn > 0 || _state.value.lockoutIn > 0) {
                delay(1_000L)
                _state.update {
                    val nextLockout = (it.lockoutIn - 1).coerceAtLeast(0)
                    it.copy(
                        resendIn = (it.resendIn - 1).coerceAtLeast(0),
                        lockoutIn = nextLockout,
                        otpLockedOut = if (nextLockout == 0) false else it.otpLockedOut
                    )
                }
            }
        }
    }
}
