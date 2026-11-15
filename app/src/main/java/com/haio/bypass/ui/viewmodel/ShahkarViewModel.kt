package com.haio.bypass.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.haio.bypass.network.haiocloud.PortalContainer
import com.haio.bypass.network.haiocloud.PortalError
import com.haio.bypass.network.haiocloud.PortalException
import com.haio.bypass.network.haiocloud.PortalUser
import com.haio.bypass.network.haiocloud.PortalValidators
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ShahkarViewModel(private val container: PortalContainer) : ViewModel() {

    data class UiState(
        val nationalCode: String = "",
        val loading: Boolean = false,
        val error: String? = null,
        val verified: Boolean = false,
        val notice: String? = null,
        val lockoutIn: Int = 0
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var countdownJob: Job? = null

    fun onNationalCodeChange(value: String) {
        val normalized = PortalValidators.normalizeNationalCode(value)
        _state.update { it.copy(nationalCode = normalized, error = null) }
    }

    fun submit(user: PortalUser?) {
        val state = _state.value
        if (state.loading || state.lockoutIn > 0) return
        val mobile = PortalValidators.normalizeMobile(user?.mobile.orEmpty())
        if (!PortalValidators.isValidMobile(mobile)) {
            _state.update {
                it.copy(error = "برای خرید، شماره موبایل معتبر روی حساب لازم است.")
            }
            return
        }
        if (!PortalValidators.isValidNationalCode(state.nationalCode)) {
            _state.update { it.copy(error = "کد ملی باید ۱۰ رقم باشد.") }
            return
        }

        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val result = container.auth.verifyShahkar(mobile, state.nationalCode)
                when {
                    result.verified == true || result.shahkarStatus == 1 -> {
                        // Never keep the national code after a successful check.
                        _state.update {
                            it.copy(
                                loading = false,
                                nationalCode = "",
                                verified = true,
                                notice = "هویت شما با موفقیت تایید شد."
                            )
                        }
                    }
                    result.shahkarStatus == 2 -> _state.update {
                        it.copy(
                            loading = false,
                            error = "اطلاعات هویتی با شماره موبایل مطابقت ندارد."
                        )
                    }
                    result.shahkarStatus == 3 -> _state.update {
                        it.copy(
                            loading = false,
                            error = "سرویس شاهکار در دسترس نیست. کمی بعد دوباره تلاش کنید."
                        )
                    }
                    else -> _state.update {
                        it.copy(
                            loading = false,
                            error = result.reason?.takeIf { reason -> reason.isNotBlank() }
                                ?: "تایید هویت انجام نشد. دوباره تلاش کنید."
                        )
                    }
                }
            } catch (exception: PortalException) {
                val error = exception.error
                val message = when {
                    error is PortalError.RateLimited -> {
                        val seconds = error.retryAfterSeconds?.takeIf { it > 0 } ?: 300
                        _state.update { it.copy(lockoutIn = seconds) }
                        startCountdown()
                        "تعداد تلاش‌ها بیش از حد مجاز است. لطفاً $seconds ثانیه صبر کنید."
                    }
                    error is PortalError.Validation && isDuplicateMobile(error) ->
                        "این شماره موبایل به حساب دیگری متصل است. با پشتیبانی تماس بگیرید."
                    error is PortalError.ShahkarRequired ->
                        "برای خرید و تمدید هایو گیت، احراز هویت شاهکار لازم است."
                    error is PortalError.MobileRequired ->
                        "برای خرید، شماره موبایل معتبر روی حساب لازم است."
                    else -> portalMessage(exception)
                }
                _state.update { it.copy(loading = false, error = message) }
            } catch (throwable: Throwable) {
                _state.update {
                    it.copy(loading = false, error = portalMessage(throwable))
                }
            }
        }
    }

    fun consumeNotice() {
        _state.update { it.copy(notice = null) }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    private fun startCountdown() {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            while (_state.value.lockoutIn > 0) {
                delay(1_000L)
                _state.update { it.copy(lockoutIn = (it.lockoutIn - 1).coerceAtLeast(0)) }
            }
        }
    }

    private fun isDuplicateMobile(error: PortalError.Validation): Boolean {
        val text = buildString {
            append(error.detail.orEmpty())
            error.fieldErrors.values.forEach { append(' ').append(it) }
        }.lowercase()
        return text.contains("another") || text.contains("already") ||
            text.contains("دیگری") || text.contains("متصل")
    }
}
