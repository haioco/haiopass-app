package com.haio.bypass.ui.screens

import android.content.Intent
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haio.bypass.BuildConfig
import com.haio.bypass.config.InviteLinkParser
import com.haio.bypass.network.haiocloud.InvitePreview
import com.haio.bypass.network.haiocloud.PortalContainer
import com.haio.bypass.network.haiocloud.PortalFormat
import com.haio.bypass.network.haiocloud.inviteErrorMessage
import com.haio.bypass.proxy.ProxyManager
import com.haio.bypass.security.DeviceCrypto
import com.haio.bypass.service.HaioVpnService
import com.haio.bypass.ui.portal.PortalInfoRow
import com.haio.bypass.ui.theme.StatusGreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

private sealed interface JoinUiState {
    data object Idle : JoinUiState
    data object PreviewLoading : JoinUiState
    data class PreviewReady(val token: String, val preview: InvitePreview) : JoinUiState
    data object Enrolling : JoinUiState
    data class Success(val packageName: String) : JoinUiState
    data class Failed(val message: String, val token: String?) : JoinUiState
}

private data class EnrollAttempt(
    val token: String,
    val generated: DeviceCrypto.GeneratedKeyPair,
    val requestId: String
)

/**
 * Guest device enrollment.
 *
 * No login, OTP or Shahkar: the invite token is the authorization and the
 * generated P-256 key proves possession on every TLS handshake. The preview and
 * enroll calls go through the anonymous portal client (no bearer token, no
 * authenticator); no portal account/session is created for a guest.
 */
@Composable
fun JoinScreen(
    container: PortalContainer,
    initialToken: String?,
    requestVpnPermission: ((Boolean) -> Unit) -> Unit,
    onBack: () -> Unit,
    onConnected: () -> Unit,
    onShowSnackbar: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf(initialToken.orEmpty()) }
    var state by remember { mutableStateOf<JoinUiState>(JoinUiState.Idle) }
    var attempt by remember { mutableStateOf<EnrollAttempt?>(null) }
    var showReplaceConfirm by remember { mutableStateOf(false) }

    suspend fun loadPreview(token: String) {
        state = JoinUiState.PreviewLoading
        try {
            val preview = container.invites.preview(token)
            state = JoinUiState.PreviewReady(token, preview)
        } catch (throwable: Throwable) {
            state = JoinUiState.Failed(inviteErrorMessage(throwable), token)
        }
    }

    fun checkInput() {
        val token = InviteLinkParser.parse(input)
        if (token == null) {
            state = JoinUiState.Failed("لینک دعوت معتبر نیست.", null)
            return
        }
        scope.launch { loadPreview(token) }
    }

    fun enroll(token: String) {
        state = JoinUiState.Enrolling
        scope.launch {
            try {
                val existing = attempt?.takeIf { it.token == token }
                val current = existing ?: EnrollAttempt(
                    token = token,
                    generated = container.deviceKeys.generate(),
                    requestId = UUID.randomUUID().toString()
                )
                attempt = current
                val deviceName = deviceName()
                val result = container.invites.enroll(
                    token = token,
                    publicKeyPem = current.generated.publicKeyPem,
                    deviceName = deviceName,
                    appVersion = BuildConfig.VERSION_NAME,
                    requestId = current.requestId
                )
                val identity = container.deviceKeys.persistAndApply(
                    result = result,
                    generated = current.generated,
                    deviceName = deviceName
                )
                attempt = null
                state = JoinUiState.Success(identity.packageName ?: "هایو گیت")
            } catch (throwable: Throwable) {
                // Keep the same key/request id for a retry so the server's
                // lost-response replay path can re-sign instead of returning
                // invite_used.
                state = JoinUiState.Failed(inviteErrorMessage(throwable), token)
            }
        }
    }

    LaunchedEffect(initialToken) {
        val token = InviteLinkParser.parse(initialToken) ?: return@LaunchedEffect
        input = token
        loadPreview(token)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("بازگشت") }
            Spacer(Modifier.width(4.dp))
            Text(
                text = "ثبت دستگاه با لینک دعوت",
                fontSize = 19.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        Spacer(Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "بدون حساب، کد یکبار مصرف یا احراز هویت",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "با لینک دعوت، همین دستگاه را ثبت کنید. یک کلید امن روی دستگاه " +
                        "ساخته می‌شود و فقط همین دستگاه می‌تواند وصل شود.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        when (val current = state) {
            is JoinUiState.Idle, is JoinUiState.Failed -> {
                val failed = current as? JoinUiState.Failed
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text("لینک دعوت را جای‌گذاری کنید") },
                    placeholder = { Text("haio://join/hgi_...") },
                    singleLine = false,
                    minLines = 2,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth()
                )
                if (failed != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = failed.message,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { checkInput() },
                    enabled = input.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("بررسی لینک", fontSize = 14.sp)
                }

                val retryToken = failed?.token
                if (retryToken != null && attempt?.token == retryToken) {
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = { enroll(retryToken) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("تلاش دوباره برای ثبت دستگاه", fontSize = 13.sp) }
                }
            }

            is JoinUiState.PreviewLoading -> LoadingRow("در حال بررسی لینک…")

            is JoinUiState.PreviewReady -> {
                val preview = current.preview
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = preview.displayPackage,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(10.dp))
                        PortalInfoRow(
                            "جایگاه",
                            "${preview.slotNumber} از ${preview.slotTotal}"
                        )
                        PortalInfoRow("حجم پلن", PortalFormat.traffic(preview.trafficBytes))
                        PortalInfoRow("اعتبار لینک", PortalFormat.date(preview.expiresAt))
                        preview.accountNameMasked?.takeIf { it.isNotBlank() }?.let {
                            PortalInfoRow("صاحب کانفیگ", it)
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "این لینک برای پلن ${preview.displayPackage} و جایگاه " +
                                "${preview.slotNumber} از ${preview.slotTotal} است. با زدن دکمه زیر، " +
                                "یک کلید امن روی این دستگاه ساخته می‌شود و فقط همین دستگاه " +
                                "می‌تواند وصل شود.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                Button(
                    onClick = {
                        if (container.configManager.getConfig().trojanConfig != null) {
                            showReplaceConfirm = true
                        } else {
                            enroll(current.token)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("ثبت این دستگاه", fontSize = 14.sp) }

                Spacer(Modifier.height(6.dp))
                TextButton(
                    onClick = {
                        state = JoinUiState.Idle
                        input = ""
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("لینک دیگری دارم", fontSize = 12.sp) }
            }

            is JoinUiState.Enrolling -> LoadingRow("در حال ساخت کلید و ثبت دستگاه…")

            is JoinUiState.Success -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = StatusGreen.copy(alpha = 0.10f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = StatusGreen,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "دستگاه شما ثبت شد.",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "پلن ${current.packageName} روی این دستگاه فعال شد.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                Button(
                    onClick = {
                        requestVpnPermission { granted ->
                            if (granted) {
                                scope.launch {
                                    if (ProxyManager.proxyState.value !=
                                        ProxyManager.ProxyState.DISCONNECTED
                                    ) {
                                        context.startService(
                                            HaioVpnService.createStopIntent(context)
                                        )
                                        delay(500)
                                    }
                                    context.startForegroundService(
                                        Intent(context, HaioVpnService::class.java)
                                    )
                                    onConnected()
                                }
                            } else {
                                onShowSnackbar("برای اتصال، دسترسی VPN لازم است.")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("اتصال", fontSize = 14.sp) }

                Spacer(Modifier.height(6.dp))
                TextButton(onClick = onConnected, modifier = Modifier.fillMaxWidth()) {
                    Text("بعداً", fontSize = 12.sp)
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            text = "این لینک فقط یک بار و برای یک دستگاه کار می‌کند. آن را برای کسی که " +
                "لینک را از او گرفته‌اید دوباره ارسال نکنید.",
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }

    if (showReplaceConfirm) {
        val ready = state as? JoinUiState.PreviewReady
        AlertDialog(
            onDismissRequest = { showReplaceConfirm = false },
            title = { Text("جایگزینی کانفیگ فعلی؟") },
            text = {
                Text(
                    "کانفیگ فعلی شما جایگزین می‌شود. کانفیگ مهمان یا پلن قبلی دیگر در این برنامه " +
                        "فعال نخواهد بود؛ کلید دستگاه قبلی روی این گوشی باقی می‌ماند."
                )
            },
            confirmButton = {
                Button(onClick = {
                    showReplaceConfirm = false
                    ready?.let { enroll(it.token) }
                }) { Text("ثبت و جایگزینی") }
            },
            dismissButton = {
                TextButton(onClick = { showReplaceConfirm = false }) { Text("انصراف") }
            },
            shape = RoundedCornerShape(18.dp)
        )
    }
}

@Composable
private fun LoadingRow(message: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 28.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(10.dp))
        Text(message, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun deviceName(): String {
    val model = Build.MODEL.orEmpty()
        .replace(Regex("[\\p{Cntrl}]"), "")
        .trim()
    return model.ifBlank { "Android device" }.take(100)
}
