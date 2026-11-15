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
import androidx.compose.material.icons.filled.Group
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.haio.bypass.network.haiocloud.AccountCategory
import com.haio.bypass.network.haiocloud.PortalContainer
import com.haio.bypass.network.haiocloud.PortalMember
import com.haio.bypass.ui.portal.PortalChip
import com.haio.bypass.ui.portal.PortalCopyRow
import com.haio.bypass.ui.portal.PortalErrorBanner
import com.haio.bypass.ui.portal.PortalHeader
import com.haio.bypass.ui.theme.StatusGreen
import com.haio.bypass.ui.theme.StatusRed
import com.haio.bypass.ui.theme.StatusYellow
import com.haio.bypass.ui.viewmodel.AccountDetailViewModel
import com.haio.bypass.ui.viewmodel.PortalViewModelFactory

/**
 * Multi-user member management for group packages (1/2/5 included users).
 * Legacy unlimited accounts keep working but expose no member actions.
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
            title = "کاربران این کانفیگ",
            subtitle = state.detail?.packageName.orEmpty().ifBlank { "مدیریت کاربران هایو گیت" },
            onBack = onBack
        )

        Spacer(Modifier.height(14.dp))

        state.error?.let { error ->
            PortalErrorBanner(message = error, onRetry = { viewModel.load(accountId) })
            Spacer(Modifier.height(10.dp))
        }

        val detail = state.detail
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
                    text = "این پلن برای $included کاربر است: یک کاربر اصلی (شما) و " +
                        "$memberSlots کاربر مهمان. هر کاربر لینک اتصال جداگانه دارد.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        if (detail.isLegacyUnlimitedAccount) {
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
                        text = "این اکانت قبل از چند کاربری ساخته شده و تا پایان دوره فعلی بدون محدودیت است. " +
                            "با تمدید، سقف کاربران اعمال می‌شود و لینک فعلی تغییر می‌کند.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { onRenew(accountId) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("تمدید و فعال‌سازی چند کاربری", fontSize = 13.sp) }
                }
            }
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
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "پس از فعال‌سازی چند کاربری در سرویس، جایگاه‌های کاربری اینجا نمایش داده می‌شود.",
                        fontSize = 11.sp,
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
                colors = CardDefaults.cardColors(
                    containerColor = StatusYellow.copy(alpha = 0.12f)
                )
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
                    PortalChip(text = "فعال", color = StatusGreen)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = detail.username.orEmpty(),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                PortalCopyRow(
                    label = "لینک اتصال کاربر اصلی",
                    value = detail.trojanUrl.orEmpty(),
                    masked = true,
                    onCopy = {
                        clipboard.setText(AnnotatedString(detail.trojanUrl.orEmpty()))
                        onShowSnackbar("لینک اتصال کپی شد.")
                    }
                )
            }
        }

        Spacer(Modifier.height(12.dp))

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
                            busy = state.memberBusy,
                            onCopy = {
                                val link = member.trojanUrl.orEmpty()
                                if (link.isBlank()) {
                                    onShowSnackbar("لینک این کاربر در دسترس نیست. رمز را چرخش دهید.")
                                } else {
                                    clipboard.setText(AnnotatedString(link))
                                    onShowSnackbar("لینک اتصال کاربر کپی شد.")
                                }
                            },
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
                    text = "این لینک را برای کاربر بفرستید. با لغو کاربر یا چرخش رمز، لینک قبلی از کار می‌افتد.",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(16.dp))
    }

    if (showAddDialog) {
        AddMemberDialog(
            busy = state.memberBusy,
            onDismiss = { showAddDialog = false },
            onSubmit = { name, mobile, email ->
                showAddDialog = false
                viewModel.addMember(name, mobile, email)
            }
        )
    }

    state.newCredential?.let { credential ->
        MemberCredentialDialog(
            member = credential,
            onCopy = { value, label ->
                clipboard.setText(AnnotatedString(value))
                onShowSnackbar("$label کپی شد.")
            },
            onDismiss = { viewModel.clearNewCredential() }
        )
    }

    memberToRevoke?.let { member ->
        AlertDialog(
            onDismissRequest = { memberToRevoke = null },
            title = { Text("حذف کاربر؟") },
            text = {
                Text(
                    "کاربر «${member.name}» حذف می‌شود و لینک اتصال او دیگر کار نمی‌کند. " +
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
            title = { Text("چرخش رمز کاربر؟") },
            text = {
                Text(
                    "رمز جدید ساخته می‌شود و لینک قبلی «${member.name}» غیرفعال می‌شود. " +
                        "لینک جدید را برای او بفرستید."
                )
            },
            confirmButton = {
                Button(onClick = {
                    val id = member.id
                    memberToRotate = null
                    if (id != null) viewModel.rotateMember(id)
                }) { Text("چرخش رمز") }
            },
            dismissButton = {
                TextButton(onClick = { memberToRotate = null }) { Text("انصراف") }
            },
            shape = RoundedCornerShape(18.dp)
        )
    }
}

@Composable
private fun MemberRow(
    member: PortalMember,
    busy: Boolean,
    onCopy: () -> Unit,
    onRotate: () -> Unit,
    onRevoke: () -> Unit
) {
    val statusColor = when (member.status) {
        "active" -> StatusGreen
        "pending" -> StatusYellow
        else -> StatusRed
    }
    val statusLabel = when (member.status) {
        "active" -> "فعال"
        "pending" -> "در انتظار"
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

        if (!member.isRevoked) {
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(onClick = onCopy, enabled = !busy) {
                    Text("کپی لینک", fontSize = 11.sp)
                }
                TextButton(onClick = onRotate, enabled = !busy) {
                    Text("چرخش رمز", fontSize = 11.sp)
                }
                TextButton(onClick = onRevoke, enabled = !busy) {
                    Text("حذف", fontSize = 11.sp, color = StatusRed)
                }
            }
        }
    }
}

@Composable
private fun AddMemberDialog(
    busy: Boolean,
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
                    text = "پس از ساخت کاربر، لینک اتصال را برای او بفرستید. او با حالت مهمان وصل می‌شود.",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(name, mobile, email) },
                enabled = name.isNotBlank() && !busy
            ) { Text("ساخت کاربر") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("انصراف") }
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
