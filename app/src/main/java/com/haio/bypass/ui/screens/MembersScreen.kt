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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.haio.bypass.network.haiocloud.AccountCategory
import com.haio.bypass.network.haiocloud.HaioGateAccountDetail
import com.haio.bypass.network.haiocloud.InviteToken
import com.haio.bypass.network.haiocloud.PortalContainer
import com.haio.bypass.network.haiocloud.PortalFormat
import com.haio.bypass.network.haiocloud.PortalMember
import com.haio.bypass.security.DeviceIdentity
import com.haio.bypass.ui.components.QrCode
import com.haio.bypass.ui.portal.PortalChip
import com.haio.bypass.ui.portal.PortalCopyRow
import com.haio.bypass.ui.portal.PortalErrorBanner
import com.haio.bypass.ui.portal.PortalHeader
import com.haio.bypass.ui.portal.PortalInfoRow
import com.haio.bypass.ui.theme.StatusGreen
import com.haio.bypass.ui.theme.StatusRed
import com.haio.bypass.ui.theme.StatusYellow
import com.haio.bypass.ui.viewmodel.AccountDetailViewModel
import com.haio.bypass.ui.viewmodel.PortalViewModelFactory

/**
 * Multi-user member + device management.
 *
 * Device-locked accounts (new purchases): members are created without a
 * password, the owner issues a single-use invite, and each row shows the bound
 * device. Legacy accounts keep the reusable-link flow and the explicit
 * legacy-unlimited notice; no invite action is shown for them.
 */
@Composable
fun MembersScreen(
    container: PortalContainer,
    accountId: String,
    onBack: () -> Unit,
    onRenew: (String) -> Unit,
    onShowSnackbar: (String) -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val factory = remember(container) { PortalViewModelFactory(container) }
    val viewModel: AccountDetailViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var memberToRevoke by remember { mutableStateOf<PortalMember?>(null) }
    var memberToRotate by remember { mutableStateOf<PortalMember?>(null) }
    var memberToResetDevice by remember { mutableStateOf<PortalMember?>(null) }
    var showPrimaryReset by remember { mutableStateOf(false) }
    var showPrimaryEnroll by remember { mutableStateOf(false) }

    LaunchedEffect(accountId) { viewModel.load(accountId) }

    LaunchedEffect(state.notice) {
        val notice = state.notice ?: return@LaunchedEffect
        onShowSnackbar(notice)
        viewModel.consumeNotice()
    }

    val detail = state.detail
    val deviceLocked = detail?.isDeviceLocked == true
    val localIdentity: DeviceIdentity? = remember(
        detail?.id,
        detail?.deviceBoundAt,
        state.appliedConfig
    ) {
        detail?.id?.let { container.deviceIdentity.findForMember(it, null) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        PortalHeader(
            title = "کاربران این کانفیگ",
            subtitle = detail?.packageName.orEmpty().ifBlank { "مدیریت کاربران هایو گیت" },
            onBack = onBack
        )

        Spacer(Modifier.height(14.dp))

        state.error?.let { error ->
            PortalErrorBanner(message = error, onRetry = { viewModel.load(accountId) })
            Spacer(Modifier.height(10.dp))
        }

        if (detail == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 40.dp),
                contentAlignment = Alignment.Center
            ) {
                if (state.loading) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                } else {
                    Text("اطلاعات اکانت در دسترس نیست.", fontSize = 13.sp)
                }
            }
            return@Column
        }

        val included = detail.includedUsers ?: 1
        val memberSlots = (included - 1).coerceAtLeast(0)
        val usedSlots = detail.memberList.count { !it.isRevoked }

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
                            Icons.Default.Group,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "جایگاه‌های کاربری",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    PortalChip(
                        text = "$usedSlots / $memberSlots",
                        color = if (usedSlots >= memberSlots) StatusYellow else StatusGreen
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (deviceLocked) {
                        "هر کاربر با یک لینک دعوت فقط روی یک دستگاه ثبت می‌شود؛ " +
                            "رمز قابل استفاده مجدد وجود ندارد."
                    } else {
                        "این پلن برای $included کاربر است: یک کاربر اصلی (شما) و " +
                            "$memberSlots کاربر مهمان. هر کاربر لینک اتصال جداگانه دارد."
                    },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        if (detail.isLegacyUnlimitedAccount) {
            LegacyUnlimitedCard(onRenew = { onRenew(accountId) })
            return@Column
        }

        if (!detail.hasMultiUserInfo) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "مدیریت کاربران برای این اکانت فعال نیست.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            return@Column
        }

        if (detail.category != AccountCategory.ACTIVE) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = StatusYellow.copy(alpha = 0.12f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "برای مدیریت کاربران ابتدا اکانت را تمدید کنید.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { onRenew(accountId) }) {
                        Text("تمدید اکانت", fontSize = 12.sp)
                    }
                }
            }
            return@Column
        }

        // ── Primary credential ────────────────────────────────────────────
        PrimaryCard(
            detail = detail,
            deviceLocked = deviceLocked,
            localIdentity = localIdentity,
            busy = state.memberBusy || state.deviceBusy || state.enrollingDevice,
            onApplyPrimaryConfig = { viewModel.applyConfig() },
            onEnrollPrimary = { showPrimaryEnroll = true },
            onCreatePrimaryInvite = { viewModel.createPrimaryInvite() },
            onResetPrimary = { showPrimaryReset = true },
            onCopy = { value, label ->
                clipboard.setText(AnnotatedString(value))
                onShowSnackbar("$label کپی شد.")
            }
        )

        Spacer(Modifier.height(12.dp))

        // ── Guest members ────────────────────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "کاربران مهمان",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(6.dp))

                val members = detail.memberList
                if (members.isEmpty()) {
                    Text(
                        text = "هنوز کاربری اضافه نشده است.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    members.forEachIndexed { index, member ->
                        if (index > 0) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 8.dp),
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                            )
                        }
                        MemberRow(
                            member = member,
                            deviceLocked = deviceLocked,
                            busy = state.memberBusy || state.deviceBusy,
                            onCopy = {
                                val link = member.trojanUrl.orEmpty()
                                if (link.isBlank()) {
                                    onShowSnackbar("این کاربر لینک قابل کپی ندارد؛ لینک دعوت بسازید.")
                                } else {
                                    clipboard.setText(AnnotatedString(link))
                                    onShowSnackbar("لینک اتصال کاربر کپی شد.")
                                }
                            },
                            onInvite = { viewModel.createMemberInvite(member.id.orEmpty()) },
                            onResetDevice = { memberToResetDevice = member },
                            onRotate = { memberToRotate = member },
                            onRevoke = { memberToRevoke = member }
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                Button(
                    onClick = { showAddDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = usedSlots < memberSlots && !state.memberBusy,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (usedSlots < memberSlots) "افزودن کاربر" else "ظرفیت این پلن تکمیل است",
                        fontSize = 13.sp
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (deviceLocked) {
                        "پس از ساخت کاربر، لینک دعوت را برای او بفرستید. این لینک فقط یک بار " +
                            "و برای یک دستگاه کار می‌کند."
                    } else {
                        "این لینک را برای کاربر بفرستید. با لغو کاربر یا چرخش رمز، لینک قبلی از کار می‌افتد."
                    },
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (deviceLocked && state.pendingInvites.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            PendingInvitesCard(
                invites = state.pendingInvites,
                onCancel = { viewModel.cancelInvite(it) }
            )
        }

        Spacer(Modifier.height(16.dp))
    }

    if (showAddDialog) {
        AddMemberDialog(
            busy = state.memberBusy,
            deviceLocked = deviceLocked,
            onDismiss = { showAddDialog = false },
            onSubmit = { name, mobile, email ->
                showAddDialog = false
                viewModel.addMember(name, mobile, email)
            }
        )
    }

    // Legacy reusable credential dialog (never shown for a locked member).
    state.newCredential?.let { credential ->
        if (!deviceLocked) {
            MemberCredentialDialog(
                member = credential,
                onCopy = { value, label ->
                    clipboard.setText(AnnotatedString(value))
                    onShowSnackbar("$label کپی شد.")
                },
                onDismiss = { viewModel.clearNewCredential() }
            )
        }
    }

    state.invite?.let { invite ->
        InviteLinkDialog(
            invite = invite,
            onCopy = {
                clipboard.setText(AnnotatedString(it))
                onShowSnackbar("لینک دعوت کپی شد.")
            },
            onDismiss = { viewModel.clearInvite() }
        )
    }

    memberToRevoke?.let { member ->
        AlertDialog(
            onDismissRequest = { memberToRevoke = null },
            title = { Text("حذف کاربر؟") },
            text = {
                Text(
                    "کاربر «${member.name}» حذف می‌شود و لینک/دستگاه او دیگر کار نمی‌کند. " +
                        "این عملیات قابل بازگشت نیست."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val id = member.id
                        memberToRevoke = null
                        if (id != null) viewModel.revokeMember(id)
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = StatusRed
                    )
                ) { Text("حذف کاربر") }
            },
            dismissButton = {
                TextButton(onClick = { memberToRevoke = null }) { Text("انصراف") }
            },
            shape = RoundedCornerShape(18.dp)
        )
    }

    memberToRotate?.let { member ->
        AlertDialog(
            onDismissRequest = { memberToRotate = null },
            title = { Text(if (deviceLocked) "حذف دستگاه و ساخت لینک جدید؟" else "چرخش رمز کاربر؟") },
            text = {
                Text(
                    if (deviceLocked) {
                        "دستگاه فعلی «${member.name}» حذف می‌شود و یک لینک دعوت جدید ساخته " +
                            "می‌شود. اتصال فعلی تا بسته شدن نشست ادامه دارد."
                    } else {
                        "رمز جدید ساخته می‌شود و لینک قبلی «${member.name}» غیرفعال می‌شود. " +
                            "لینک جدید را برای او بفرستید."
                    }
                )
            },
            confirmButton = {
                Button(onClick = {
                    val id = member.id
                    memberToRotate = null
                    if (id != null) viewModel.rotateMember(id)
                }) { Text(if (deviceLocked) "ساخت لینک جدید" else "چرخش رمز") }
            },
            dismissButton = {
                TextButton(onClick = { memberToRotate = null }) { Text("انصراف") }
            },
            shape = RoundedCornerShape(18.dp)
        )
    }

    memberToResetDevice?.let { member ->
        AlertDialog(
            onDismissRequest = { memberToResetDevice = null },
            title = { Text("حذف دستگاه کاربر؟") },
            text = {
                Text(
                    "با حذف دستگاه، اتصال فعلی قطع نمی‌شود اما اتصال بعدی مسدود است. " +
                        "سپس یک لینک دعوت جدید بسازید."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val id = member.id
                        memberToResetDevice = null
                        if (id != null) viewModel.resetMemberDevice(id)
                    }
                ) { Text("حذف دستگاه") }
            },
            dismissButton = {
                TextButton(onClick = { memberToResetDevice = null }) { Text("انصراف") }
            },
            shape = RoundedCornerShape(18.dp)
        )
    }

    if (showPrimaryEnroll) {
        AlertDialog(
            onDismissRequest = { showPrimaryEnroll = false },
            title = { Text("ثبت این دستگاه؟") },
            text = {
                Text(
                    if (detail?.hasBoundDevice == true) {
                        "دستگاه قبلی کاربر اصلی لغو می‌شود و این گوشی جای آن ثبت می‌شود. " +
                            "دستگاه قبلی در اتصال بعدی مسدود خواهد بود."
                    } else {
                        "یک کلید امن روی این گوشی ساخته می‌شود و فقط همین دستگاه می‌تواند " +
                            "با این اکانت وصل شود."
                    }
                )
            },
            confirmButton = {
                Button(onClick = {
                    showPrimaryEnroll = false
                    viewModel.enrollPrimaryDevice(accountId)
                }) { Text("ثبت این دستگاه") }
            },
            dismissButton = {
                TextButton(onClick = { showPrimaryEnroll = false }) { Text("انصراف") }
            },
            shape = RoundedCornerShape(18.dp)
        )
    }

    if (showPrimaryReset) {
        AlertDialog(
            onDismissRequest = { showPrimaryReset = false },
            title = { Text("حذف دستگاه اصلی؟") },
            text = {
                Text(
                    "با حذف دستگاه، اتصال فعلی قطع نمی‌شود اما اتصال بعدی مسدود است. " +
                        "سپس این گوشی را دوباره ثبت کنید."
                )
            },
            confirmButton = {
                Button(onClick = {
                    showPrimaryReset = false
                    viewModel.resetPrimaryDevice()
                }) { Text("حذف دستگاه") }
            },
            dismissButton = {
                TextButton(onClick = { showPrimaryReset = false }) { Text("انصراف") }
            },
            shape = RoundedCornerShape(18.dp)
        )
    }
}

@Composable
private fun LegacyUnlimitedCard(onRenew: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "نامحدود (قدیمی)",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "این اکانت قبل از قابلیت چند کاربری ساخته شده و تا پایان دوره فعلی " +
                    "بدون محدودیت است. با تمدید، سقف کاربران اعمال می‌شود و لینک فعلی تغییر می‌کند.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onRenew,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) { Text("تمدید و فعال‌سازی چند کاربری", fontSize = 13.sp) }
        }
    }
}

@Composable
private fun PrimaryCard(
    detail: HaioGateAccountDetail,
    deviceLocked: Boolean,
    localIdentity: DeviceIdentity?,
    busy: Boolean,
    onApplyPrimaryConfig: () -> Unit,
    onEnrollPrimary: () -> Unit,
    onCreatePrimaryInvite: () -> Unit,
    onResetPrimary: () -> Unit,
    onCopy: (String, String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "کاربر اصلی (شما)",
                    modifier = Modifier.weight(1f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                PortalChip(
                    text = if (deviceLocked) "قفل دستگاه" else "فعال",
                    color = if (deviceLocked) MaterialTheme.colorScheme.primary else StatusGreen
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = detail.username.orEmpty(),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (!deviceLocked) {
                Spacer(Modifier.height(10.dp))
                PortalCopyRow(
                    label = "لینک اتصال کاربر اصلی",
                    value = detail.trojanUrl.orEmpty(),
                    masked = true,
                    onCopy = {
                        onCopy(detail.trojanUrl.orEmpty(), "لینک اتصال")
                    }
                )
                return@Column
            }

            // Device-locked: never show a password or reusable trojan URL.
            Spacer(Modifier.height(10.dp))
            if (detail.hasBoundDevice) {
                Icon(
                    Icons.Default.Devices,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.height(4.dp))
                PortalInfoRow(
                    "دستگاه ثبت‌شده",
                    detail.deviceName.orEmpty().ifBlank { "دستگاه" }
                )
                detail.devicePlatform?.takeIf { it.isNotBlank() }?.let {
                    PortalInfoRow("سیستم‌عامل", it)
                }
                PortalInfoRow("آخرین ثبت", PortalFormat.date(detail.deviceBoundAt))
                PortalInfoRow("انقضای گواهی", PortalFormat.date(detail.deviceCertExpiresAt))
            } else {
                Text(
                    text = "هنوز دستگاهی برای کاربر اصلی ثبت نشده است.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (localIdentity != null) {
                Spacer(Modifier.height(6.dp))
                PortalChip(text = "این گوشی ثبت شده است", color = StatusGreen)
            }

            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onEnrollPrimary,
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = if (localIdentity == null) "ثبت این دستگاه" else "ثبت دوباره",
                        fontSize = 12.sp
                    )
                }
                OutlinedButton(
                    onClick = onApplyPrimaryConfig,
                    enabled = !busy && localIdentity != null,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) { Text("اعمال روی اپ", fontSize = 12.sp) }
            }

            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = onCreatePrimaryInvite,
                    enabled = !busy && !detail.hasBoundDevice
                ) {
                    Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("لینک دعوت جدید", fontSize = 11.sp)
                }
                TextButton(onClick = onResetPrimary, enabled = !busy && detail.hasBoundDevice) {
                    Text("حذف دستگاه", fontSize = 11.sp, color = StatusRed)
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "برای تغییر دستگاه، ابتدا دستگاه فعلی را حذف کنید؛ سپس یک لینک دعوت جدید بسازید.",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MemberRow(
    member: PortalMember,
    deviceLocked: Boolean,
    busy: Boolean,
    onCopy: () -> Unit,
    onInvite: () -> Unit,
    onResetDevice: () -> Unit,
    onRotate: () -> Unit,
    onRevoke: () -> Unit
) {
    val statusColor = when (member.status) {
        "active" -> StatusGreen
        "pending" -> StatusYellow
        else -> StatusRed
    }
    val statusLabel = when (member.status) {
        "active" -> if (deviceLocked) "فعال" else "فعال"
        "pending" -> if (deviceLocked) "در انتظار ثبت دستگاه" else "در انتظار"
        else -> "حذف‌شده"
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = member.name.ifBlank { "کاربر" },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = member.username.orEmpty(),
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            PortalChip(text = statusLabel, color = statusColor)
        }

        if (deviceLocked) {
            val device = member.device
            if (device != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "دستگاه: ${device.displayName}" +
                        (device.platform?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "آخرین اتصال: ${PortalFormat.date(device.lastSeenAt)}" +
                        " · انقضای گواهی: ${PortalFormat.date(device.certExpiresAt)}",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (member.isPending) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "هنوز دستگاهی ثبت نشده است.",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (!member.isRevoked) {
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (deviceLocked) {
                    TextButton(onClick = onInvite, enabled = !busy) {
                        Text("لینک دعوت جدید", fontSize = 11.sp)
                    }
                    TextButton(onClick = onResetDevice, enabled = !busy) {
                        Text("حذف دستگاه", fontSize = 11.sp)
                    }
                    TextButton(onClick = onRotate, enabled = !busy) {
                        Text("تعویض دستگاه", fontSize = 11.sp)
                    }
                } else {
                    TextButton(onClick = onCopy, enabled = !busy) {
                        Text("کپی لینک", fontSize = 11.sp)
                    }
                    TextButton(onClick = onRotate, enabled = !busy) {
                        Text("چرخش رمز", fontSize = 11.sp)
                    }
                }
                TextButton(onClick = onRevoke, enabled = !busy) {
                    Text("حذف", fontSize = 11.sp, color = StatusRed)
                }
            }
        }
    }
}

@Composable
private fun PendingInvitesCard(
    invites: List<com.haio.bypass.network.haiocloud.PendingInvite>,
    onCancel: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "دعوت‌های در انتظار",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(6.dp))
            invites.forEach { invite ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = invite.memberName.orEmpty().ifBlank { "کاربر اصلی" },
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "انقضا: ${PortalFormat.date(invite.expiresAt)}",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = { invite.id?.let(onCancel) }) {
                        Text("لغو", fontSize = 11.sp, color = StatusRed)
                    }
                }
            }
        }
    }
}

@Composable
private fun AddMemberDialog(
    busy: Boolean,
    deviceLocked: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (name: String, mobile: String, email: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var mobile by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("افزودن کاربر") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("نام کاربر") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = mobile,
                    onValueChange = { mobile = it },
                    label = { Text("موبایل (اختیاری)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("ایمیل (اختیاری)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (deviceLocked) {
                        "پس از ساخت کاربر، لینک دعوت ساخته می‌شود. این لینک فقط یک بار و " +
                            "برای یک دستگاه کار می‌کند."
                    } else {
                        "پس از ساخت کاربر، لینک اتصال را برای او بفرستید. او با حالت مهمان وصل می‌شود."
                    },
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(name, mobile, email) },
                enabled = name.isNotBlank() && !busy
            ) { Text(if (deviceLocked) "ساخت لینک دعوت" else "ساخت کاربر") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("انصراف") }
        },
        shape = RoundedCornerShape(18.dp)
    )
}

@Composable
private fun InviteLinkDialog(
    invite: InviteToken,
    onCopy: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val link = invite.shareLink
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("لینک دعوت ساخته شد") },
        text = {
            Column {
                Text(
                    text = "این لینک فقط یک بار و برای یک دستگاه کار می‌کند. آن را فقط به " +
                        "فرد مورد نظر بدهید.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                invite.packageName?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(2.dp))
                }
                Text(
                    text = "اعتبار لینک: ${PortalFormat.date(invite.expiresAt)}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                QrCode(
                    content = link,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = link,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onCopy(link) }) { Text("کپی لینک") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("بستن") }
        },
        shape = RoundedCornerShape(18.dp)
    )
}

@Composable
private fun MemberCredentialDialog(
    member: PortalMember,
    onCopy: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("لینک اتصال کاربر") },
        text = {
            Column {
                Text(
                    text = "این اطلاعات فقط یک بار نمایش داده می‌شود. لینک را برای کاربر بفرستید.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                PortalCopyRow(
                    label = "نام کاربری",
                    value = member.username.orEmpty(),
                    onCopy = { onCopy(member.username.orEmpty(), "نام کاربری") }
                )
                Spacer(Modifier.height(6.dp))
                PortalCopyRow(
                    label = "رمز عبور",
                    value = member.password.orEmpty(),
                    onCopy = { onCopy(member.password.orEmpty(), "رمز عبور") }
                )
                if (!member.trojanUrl.isNullOrBlank()) {
                    Spacer(Modifier.height(6.dp))
                    PortalCopyRow(
                        label = "لینک اتصال",
                        value = member.trojanUrl.orEmpty(),
                        onCopy = { onCopy(member.trojanUrl.orEmpty(), "لینک اتصال") }
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "هر کسی این لینک را داشته باشد می‌تواند تا زمان لغو، به این کانفیگ وصل شود.",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("متوجه شدم") }
        },
        shape = RoundedCornerShape(18.dp)
    )
}
