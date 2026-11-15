package com.haio.bypass.ui.screens

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
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.haio.bypass.network.haiocloud.PortalContainer
import com.haio.bypass.network.haiocloud.SessionState
import com.haio.bypass.ui.portal.PortalErrorBanner
import com.haio.bypass.ui.viewmodel.PortalViewModelFactory
import com.haio.bypass.ui.viewmodel.ShahkarViewModel

/**
 * Shahkar national-code verification. The mobile number is locked to the
 * logged-in account because the backend writes the submitted mobile onto the
 * user. The national code is never persisted or logged.
 */
@Composable
fun ShahkarScreen(
    container: PortalContainer,
    onVerified: () -> Unit,
    onBack: () -> Unit
) {
    val factory = remember(container) { PortalViewModelFactory(container) }
    val viewModel: ShahkarViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsState()
    val sessionState by container.session.state.collectAsState()
    val user = (sessionState as? SessionState.LoggedIn)?.user
    var revealCode by remember { mutableStateOf(false) }

    LaunchedEffect(state.verified) {
        if (state.verified) onVerified()
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
                    text = "احراز هویت شاهکار",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "برای خرید و تمدید هایو گیت لازم است",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.PhoneAndroid,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "شماره موبایل حساب شما",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = user?.mobile.orEmpty(),
                    onValueChange = { },
                    modifier = Modifier.fillMaxWidth(),
                    readOnly = true,
                    enabled = false,
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    placeholder = { Text("شماره موبایل ثبت نشده") }
                )

                Spacer(Modifier.height(16.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Badge,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "کد ملی",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = state.nationalCode,
                    onValueChange = viewModel::onNationalCodeChange,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.loading,
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    placeholder = { Text("۱۰ رقم") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = if (revealCode) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        IconButton(onClick = { revealCode = !revealCode }) {
                            Icon(
                                if (revealCode) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (revealCode) "پنهان کردن" else "نمایش"
                            )
                        }
                    }
                )

                if (state.error != null) {
                    Spacer(Modifier.height(10.dp))
                    PortalErrorBanner(message = state.error!!)
                }

                if (state.lockoutIn > 0) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "برای تلاش دوباره، ${state.lockoutIn} ثانیه صبر کنید.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                if (state.notice != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = state.notice!!,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(Modifier.height(14.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "کد ملی فقط برای استعلام شاهکار ارسال می‌شود و در برنامه ذخیره نمی‌شود.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = { viewModel.submit(user) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.loading && state.lockoutIn == 0 && (user?.hasValidMobile != false),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (state.loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text("تایید هویت", fontSize = 14.sp)
                    }
                }

                if (user?.hasValidMobile == false) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "برای خرید، شماره موبایل معتبر روی حساب لازم است. با پشتیبانی تماس بگیرید.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
