package com.haio.bypass.ui.screens

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.haio.bypass.network.haiocloud.AccountCategory
import com.haio.bypass.network.haiocloud.PortalContainer
import com.haio.bypass.network.haiocloud.PortalFormat
import com.haio.bypass.ui.portal.PortalChip
import com.haio.bypass.ui.portal.PortalCopyRow
import com.haio.bypass.ui.portal.PortalErrorBanner
import com.haio.bypass.ui.portal.PortalHeader
import com.haio.bypass.ui.portal.PortalInfoRow
import com.haio.bypass.ui.portal.PortalSectionTitle
import com.haio.bypass.ui.portal.PortalUsageBar
import com.haio.bypass.ui.theme.StatusGreen
import com.haio.bypass.ui.theme.StatusRed
import com.haio.bypass.ui.theme.StatusYellow
import com.haio.bypass.ui.viewmodel.AccountDetailViewModel
import com.haio.bypass.ui.viewmodel.PortalViewModelFactory

@Composable
fun HaioGateDetailScreen(
    container: PortalContainer,
    accountId: String,
    onBack: () -> Unit,
    onRenew: (String) -> Unit,
    onManageMembers: (String) -> Unit,
    onShowSnackbar: (String) -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val factory = remember(container) { PortalViewModelFactory(container) }
    val viewModel: AccountDetailViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsState()
    val detail = state.detail
    var showApplyConfirm by remember { mutableStateOf(false) }
    var showEnrollConfirm by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }
    val localIdentity = remember(
        state.detail?.id,
        state.detail?.deviceBoundAt,
        state.appliedConfig
    ) {
        state.detail?.id?.let { container.deviceIdentity.findForMember(it, null) }
    }

    LaunchedEffect(accountId) { viewModel.load(accountId) }

    LaunchedEffect(state.notice) {
        val notice = state.notice ?: return@LaunchedEffect
        onShowSnackbar(notice)
        viewModel.consumeNotice()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        PortalHeader(
            title = state.detail?.packageName.orEmpty().ifBlank { "جزئیات اکانت" },
            subtitle = "جزئیات اکانت هایو گیت",
            onBack = onBack
        )

        Spacer(Modifier.height(14.dp))

        state.error?.let { error ->
            PortalErrorBanner(message = error, onRetry = { viewModel.load(accountId) })
            Spacer(Modifier.height(10.dp))
        }

        if (detail == null) {            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 40.dp),
                contentAlignment = Alignment.Center
            ) {
                if (state.loading) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                } else {
                    Text(
                        text = "اطلاعات اکانت در دسترس نیست.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            return@Column
        }

        val statusColor = when (detail.category) {
            AccountCategory.ACTIVE -> StatusGreen
            AccountCategory.EXPIRED -> StatusRed
            AccountCategory.SUPPORT -> StatusYellow
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Cloud,
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = detail.packageName.orEmpty().ifBlank { "پکیج هایو گیت" },
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    PortalChip(
                        text = PortalFormat.statusLabel(detail),
                        color = statusColor
                    )
                }

                Spacer(Modifier.height(10.dp))

                PortalInfoRow("نام کاربری", detail.username.orEmpty().ifBlank { "—" })

                if (detail.isLegacy == true) {
                    Spacer(Modifier.height(6.dp))
                    PortalChip(text = "اکانت قدیمی", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        PortalSectionTitle("مصرف")
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = PortalFormat.usageDisplay(detail),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${detail.progressPercent.toInt()}٪",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.height(8.dp))
                PortalUsageBar(percent = detail.progressPercent)
            }
        }

        Spacer(Modifier.height(12.dp))

        PortalSectionTitle("زمان‌ها")
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                PortalInfoRow("انقضای فعلی", PortalFormat.date(detail.expiredAt))
                PortalFormat.projectedDate(detail.projectedExpiredAt)?.let { projected ->
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                    )
                    PortalInfoRow("انقضا پس از تمدیدهای رزرو", projected)
                }
                PortalInfoRow("آخرین همگام‌سازی", PortalFormat.date(detail.lastSync))
            }
        }

        if (detail.reserves.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            PortalSectionTitle("تمدیدهای رزرو شده")
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    detail.reserves.forEachIndexed { index, reserve ->
                        if (index > 0) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 6.dp),
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = reserve.packageName.orEmpty().ifBlank { "تمدید" },
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = PortalFormat.traffic(reserve.trafficBytes),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = PortalFormat.date(reserve.createdAt),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        if (detail.isDeviceLocked) {
            PortalSectionTitle("دستگاه ثبت‌شده")
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "این اکانت با گواهی دستگاه محافظت می‌شود؛ رمز قابل استفاده مجدد ندارد.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    if (detail.hasBoundDevice) {
                        PortalInfoRow("دستگاه", detail.deviceName.orEmpty().ifBlank { "دستگاه" })
                        detail.devicePlatform?.takeIf { it.isNotBlank() }?.let {
                            PortalInfoRow("سیستم‌عامل", it)
                        }
                        PortalInfoRow("تاریخ ثبت", PortalFormat.date(detail.deviceBoundAt))
                        PortalInfoRow("انقضای گواهی", PortalFormat.date(detail.deviceCertExpiresAt))
                    } else {
                        Text(
                            text = "هنوز دستگاهی ثبت نشده است. برای اتصال، این گوشی را ثبت کنید.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { showEnrollConfirm = true },
                            enabled = !state.enrollingDevice,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            if (state.enrollingDevice) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("ثبت این دستگاه", fontSize = 12.sp)
                            }
                        }
                        OutlinedButton(
                            onClick = { showResetConfirm = true },
                            enabled = detail.hasBoundDevice && !state.deviceBusy,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) { Text("حذف دستگاه", fontSize = 12.sp) }
                    }
                }
            }
        } else {
        PortalSectionTitle("اطلاعات اتصال")
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                PortalCopyRow(
                    label = "نام کاربری",
                    value = detail.username.orEmpty(),
                    onCopy = {
                        clipboard.setText(AnnotatedString(detail.username.orEmpty()))
                        onShowSnackbar("نام کاربری کپی شد.")
                    }
                )
                Spacer(Modifier.height(8.dp))
                PortalCopyRow(
                    label = "رمز عبور",
                    value = detail.password.orEmpty(),
                    masked = true,
                    revealed = state.revealPrimaryPassword,
                    onToggleReveal = { viewModel.togglePrimaryPassword() },
                    onCopy = {
                        clipboard.setText(AnnotatedString(detail.password.orEmpty()))
                        onShowSnackbar("رمز عبور کپی شد.")
                    }
                )
                Spacer(Modifier.height(8.dp))
                PortalCopyRow(
                    label = "لینک اتصال (مخفی)",
                    value = detail.trojanUrl.orEmpty(),
                    masked = true,
                    onCopy = {
                        clipboard.setText(AnnotatedString(detail.trojanUrl.orEmpty()))
                        onShowSnackbar("لینک اتصال کپی شد.")
                    }
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = "هر کسی این لینک را داشته باشد می‌تواند تا زمان لغو، به این کانفیگ وصل شود.",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        }

        Spacer(Modifier.height(12.dp))

        PortalSectionTitle("کاربران این کانفیگ")
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                if (detail.isLegacyUnlimitedAccount) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Group,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "نامحدود (قدیمی)",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "این اکانت قبل از قابلیت چند کاربری ساخته شده و تا زمان تمدید بدون محدودیت است. " +
                            "با تمدید، لینک فعلی تغییر می‌کند و سقف کاربران پکیج جدید اعمال می‌شود.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (!detail.hasMultiUserInfo) {
                    Text(
                        text = "مدیریت کاربران برای این اکانت در دسترس نیست.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    PortalInfoRow(
                        "تعداد کاربران پکیج",
                        PortalFormat.membersLabel(detail.includedUsers) ?: "۱ کاربر"
                    )
                    PortalInfoRow(
                        "جایگاه‌های استفاده‌شده",
                        "${detail.activeMemberCount} از ${((detail.includedUsers ?: 1) - 1).coerceAtLeast(0)}"
                    )
                    PortalInfoRow("جایگاه‌های خالی", detail.availableSlotCount.toString())

                    Spacer(Modifier.height(10.dp))

                    if (detail.category == AccountCategory.ACTIVE) {
                        OutlinedButton(
                            onClick = { onManageMembers(accountId) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                Icons.Default.Group,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("مدیریت کاربران", fontSize = 13.sp)
                        }
                    } else {
                        Text(
                            text = "برای مدیریت کاربران ابتدا اکانت را تمدید کنید.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = {
                if (detail.isDeviceLocked && localIdentity == null) {
                    showEnrollConfirm = true
                } else if (container.configManager.getConfig().trojanConfig != null) {
                    showApplyConfirm = true
                } else {
                    viewModel.applyConfig()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("اعمال روی اپ", fontSize = 14.sp)
        }

        Spacer(Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { onRenew(accountId) },
                modifier = Modifier.weight(1f),
                enabled = detail.category != AccountCategory.SUPPORT,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("تمدید", fontSize = 13.sp)
            }
            OutlinedButton(
                onClick = { viewModel.syncQuota(accountId) },
                modifier = Modifier.weight(1f),
                enabled = !state.syncing,
                shape = RoundedCornerShape(12.dp)
            ) {
                if (state.syncing) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                } else {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text("به‌روزرسانی مصرف", fontSize = 12.sp)
                }
            }
        }

        if (detail.category == AccountCategory.SUPPORT) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = StatusYellow,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    text = "این اکانت نیازمند بررسی پشتیبانی است.",
                    fontSize = 11.sp,
                    color = StatusYellow
                )
            }
        }

        Spacer(Modifier.height(16.dp))
    }

    if (showApplyConfirm) {
        AlertDialog(
            onDismissRequest = { showApplyConfirm = false },
            title = { Text("جایگزینی کانفیگ فعلی؟") },
            text = {
                Text(
                    "کانفیگ فعلی شما جایگزین می‌شود. کانفیگ مهمان یا پلن قبلی حذف خواهد شد."
                )
            },
            confirmButton = {
                Button(onClick = {
                    showApplyConfirm = false
                    viewModel.applyConfig()
                }) { Text("اعمال کن") }
            },
            dismissButton = {
                TextButton(onClick = { showApplyConfirm = false }) { Text("انصراف") }
            },
            shape = RoundedCornerShape(18.dp)
        )
    }

    if (showEnrollConfirm) {
        AlertDialog(
            onDismissRequest = { showEnrollConfirm = false },
            title = { Text("ثبت این دستگاه؟") },
            text = {
                Text(
                    if (detail?.hasBoundDevice == true) {
                        "دستگاه قبلی این اکانت لغو می‌شود و این گوشی جای آن ثبت می‌شود. " +
                            "دستگاه قبلی در اتصال بعدی مسدود خواهد بود."
                    } else {
                        "یک کلید امن روی این گوشی ساخته می‌شود و فقط همین دستگاه می‌تواند " +
                            "با این اکانت وصل شود."
                    }
                )
            },
            confirmButton = {
                Button(onClick = {
                    showEnrollConfirm = false
                    viewModel.enrollPrimaryDevice(accountId)
                }) { Text("ثبت این دستگاه") }
            },
            dismissButton = {
                TextButton(onClick = { showEnrollConfirm = false }) { Text("انصراف") }
            },
            shape = RoundedCornerShape(18.dp)
        )
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("حذف دستگاه؟") },
            text = {
                Text(
                    "با حذف دستگاه، اتصال فعلی قطع نمی‌شود اما اتصال بعدی مسدود است. " +
                        "سپس این گوشی را دوباره ثبت کنید."
                )
            },
            confirmButton = {
                Button(onClick = {
                    showResetConfirm = false
                    viewModel.resetPrimaryDevice()
                }) { Text("حذف دستگاه") }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) { Text("انصراف") }
            },
            shape = RoundedCornerShape(18.dp)
        )
    }
}
