package com.haio.bypass.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.haio.bypass.network.haiocloud.PortalContainer
import com.haio.bypass.network.haiocloud.SessionState
import com.haio.bypass.ui.portal.PortalErrorBanner
import com.haio.bypass.ui.viewmodel.AuthViewModel
import com.haio.bypass.ui.viewmodel.PortalViewModelFactory

/**
 * Mobile → SMS OTP login against portal.haio.ir.
 * The session is persisted before `LoggedIn` is emitted by the repository.
 */
@Composable
fun LoginScreen(
    container: PortalContainer,
    onLoggedIn: () -> Unit,
    onBack: () -> Unit
) {
    val factory = remember(container) { PortalViewModelFactory(container) }
    val viewModel: AuthViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsState()
    val sessionState by container.session.state.collectAsState()

    LaunchedEffect(sessionState) {
        if (sessionState is SessionState.LoggedIn) onLoggedIn()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "بازگشت")
            }
            Spacer(Modifier.width(4.dp))
            Column {
                Text(
                    text = "ورود به حساب هایو",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "با شماره موبایل خود وارد شوید",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                when (state.step) {
                    AuthViewModel.Step.MOBILE -> MobileStep(
                        mobile = state.mobile,
                        loading = state.loading,
                        error = state.error,
                        lockoutSeconds = state.lockoutIn,
                        onMobileChange = viewModel::onMobileChange,
                        onSubmit = viewModel::requestOtp
                    )
                    AuthViewModel.Step.OTP -> OtpStep(
                        otp = state.otp,
                        info = state.info,
                        error = state.error,
                        loading = state.loading,
                        resendIn = state.resendIn,
                        otpLockedOut = state.otpLockedOut,
                        onOtpChange = viewModel::onOtpChange,
                        onSubmit = viewModel::login,
                        onResend = viewModel::requestOtp,
                        onChangeMobile = viewModel::backToMobile
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = "حساب هایو گیت، خرید و تمدید به همین حساب متصل می‌شود. " +
                "ورود به برنامه برای استفاده از کانفیگ دستی یا مهمان لازم نیست.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun MobileStep(
    mobile: String,
    loading: Boolean,
    error: String?,
    lockoutSeconds: Int,
    onMobileChange: (String) -> Unit,
    onSubmit: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Default.PhoneAndroid,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "شماره موبایل",
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }

    Spacer(Modifier.height(12.dp))

    OutlinedTextField(
        value = mobile,
        onValueChange = onMobileChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("09123456789", fontSize = 14.sp) },
        isError = error != null,
        singleLine = true,
        enabled = !loading,
        shape = RoundedCornerShape(12.dp),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
    )

    if (error != null) {
        Spacer(Modifier.height(10.dp))
        PortalErrorBanner(message = error)
    }

    if (lockoutSeconds > 0) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = "برای ارسال دوباره، $lockoutSeconds ثانیه صبر کنید.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.error
        )
    }

    Spacer(Modifier.height(16.dp))

    Button(
        onClick = onSubmit,
        modifier = Modifier.fillMaxWidth(),
        enabled = !loading && lockoutSeconds == 0,
        shape = RoundedCornerShape(12.dp)
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary
            )
        } else {
            Icon(Icons.Default.Sms, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("دریافت کد", fontSize = 14.sp)
        }
    }
}

@Composable
private fun OtpStep(
    otp: String,
    info: String?,
    error: String?,
    loading: Boolean,
    resendIn: Int,
    otpLockedOut: Boolean,
    onOtpChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onResend: () -> Unit,
    onChangeMobile: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Default.Sms,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "کد تایید ۶ رقمی",
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }

    if (info != null) {
        Spacer(Modifier.height(6.dp))
        Text(text = info, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
    }

    Spacer(Modifier.height(12.dp))

    OutlinedTextField(
        value = otp,
        onValueChange = onOtpChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("------", fontSize = 18.sp) },
        isError = error != null,
        singleLine = true,
        enabled = !loading && !otpLockedOut,
        shape = RoundedCornerShape(12.dp),
        textStyle = androidx.compose.ui.text.TextStyle(
            fontSize = 20.sp,
            letterSpacing = 8.sp,
            textAlign = TextAlign.Center
        ),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )

    if (error != null) {
        Spacer(Modifier.height(10.dp))
        PortalErrorBanner(message = error)
    }

    Spacer(Modifier.height(16.dp))

    Button(
        onClick = onSubmit,
        modifier = Modifier.fillMaxWidth(),
        enabled = !loading && !otpLockedOut,
        shape = RoundedCornerShape(12.dp)
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary
            )
        } else {
            Text("تایید و ورود", fontSize = 14.sp)
        }
    }

    Spacer(Modifier.height(10.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = onChangeMobile, enabled = !loading) {
            Text("تغییر شماره", fontSize = 12.sp)
        }
        OutlinedButton(
            onClick = onResend,
            enabled = !loading && resendIn == 0 && !otpLockedOut,
            shape = RoundedCornerShape(10.dp)
        ) {
            Text(
                text = if (resendIn > 0) "ارسال دوباره کد ($resendIn)" else "ارسال دوباره کد",
                fontSize = 12.sp
            )
        }
    }
}
