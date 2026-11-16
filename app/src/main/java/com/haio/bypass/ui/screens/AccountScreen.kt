package com.haio.bypass.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.haio.bypass.network.haiocloud.AccountCategory
import com.haio.bypass.network.haiocloud.HaioGateAccount
import com.haio.bypass.network.haiocloud.PortalContainer
import com.haio.bypass.network.haiocloud.PortalFormat
import com.haio.bypass.network.haiocloud.SessionState
import com.haio.bypass.ui.portal.PortalChip
import com.haio.bypass.ui.portal.PortalUsageBar
import com.haio.bypass.ui.theme.StatusGreen
import com.haio.bypass.ui.theme.StatusRed
import com.haio.bypass.ui.theme.StatusYellow
import com.haio.bypass.ui.viewmodel.AccountViewModel
import com.haio.bypass.ui.viewmodel.HaioGateViewModel
import com.haio.bypass.ui.viewmodel.PortalViewModelFactory

@Composable
fun AccountScreen(
    container: PortalContainer,
    onLogin: () -> Unit,
    onShahkar: () -> Unit,
    onLoggedOut: () -> Unit,
    onBack: () -> Unit,
    onOpenStore: () -> Unit,
    onOpenDetail: (String) -> Unit,
    onRenew: (String) -> Unit,
    onShowSnackbar: (String) -> Unit
) {
    val sessionState by container.session.state.collectAsState()
    var showLogoutDialog by remember { mutableStateOf(false) }
    val user = (sessionState as? SessionState.LoggedIn)?.user
    val factory = remember(container) { PortalViewModelFactory(container) }
    val accountViewModel: AccountViewModel = viewModel(factory = factory)
    val gateViewModel: HaioGateViewModel = viewModel(factory = factory)
    val gateState by gateViewModel.state.collectAsState()

    LaunchedEffect(user?.id) {
        if (user != null) gateViewModel.refresh()
    }
    LaunchedEffect(gateState.notice) {
        gateState.notice?.let {
            onShowSnackbar(it)
            gateViewModel.consumeNotice()
        }
    }
    LaunchedEffect(gateState.error) {
        gateState.error?.let {
            onShowSnackbar(it)
            gateViewModel.clearError()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "بازگشت")
            }
            Spacer(Modifier.width(4.dp))
            Text(
                text = "حساب هایو",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        Spacer(Modifier.height(16.dp))

        if (user == null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "شما در حالت مهمان هستید.",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "برای خرید و مدیریت هایو گیت وارد حساب هایو شوید. " +
                            "کانفیگ فعلی شما حفظ می‌شود.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = onLogin,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("ورود به حساب")
                    }
                }
            }
            return@Column
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = user.title,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = user.mobile.orEmpty().ifBlank { "شماره موبایل ثبت نشده" },
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Badge,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "احراز هویت شاهکار",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (user.shahkarVerified) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.VerifiedUser,
                                contentDescription = null,
                                tint = StatusGreen,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "تایید شده",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = StatusGreen
                            )
                        }
                    } else {
                        TextButton(onClick = onShahkar) {
                            Text(
                                text = "تایید هویت",
                                fontSize = 12.sp,
                                color = StatusYellow
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        MyConfigsSection(
            state = gateState,
            onManage = onOpenStore,
            onOpenDetail = onOpenDetail,
            onRenew = onRenew,
            onApply = { gateViewModel.applyConfig(it) }
        )

        Spacer(Modifier.height(14.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            )
        ) {
            Text(
                text = "با خروج از حساب، تنظیمات اتصال فعلی حذف نمی‌شود. " +
                    "کانفیگ هایو گیت اعمال‌شده تا زمانی که آن را در تنظیمات پاک نکنید باقی می‌ماند.",
                modifier = Modifier.padding(14.dp),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(16.dp))

        OutlinedButton(
            onClick = { showLogoutDialog = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            )
        ) {
            Icon(Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("خروج از حساب", fontSize = 14.sp)
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("خروج از حساب؟") },
            text = {
                Text("با خروج، نشست هایو پاک می‌شود اما کانفیگ اتصال فعلی حفظ می‌شود.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutDialog = false
                        accountViewModel.logout()
                        onLoggedOut()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("خروج") }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) { Text("انصراف") }
            },
            shape = RoundedCornerShape(18.dp)
        )
    }
}

@Composable
private fun MyConfigsSection(
    state: HaioGateViewModel.UiState,
    onManage: () -> Unit,
    onOpenDetail: (String) -> Unit,
    onRenew: (String) -> Unit,
    onApply: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "کانفیگ‌های من",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "جابه‌جایی بین اکانت‌ها، مشاهده مصرف و تمدید",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(
                onClick = onManage,
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text("مدیریت همه", fontSize = 12.sp)
            }
        }

        Spacer(Modifier.height(8.dp))

        when {
            state.loading -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "در حال دریافت کانفیگ‌ها…",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            state.accounts.isEmpty() -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = "هنوز کانفیگ هایو گیت ندارید.",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "با خرید پکیج، اولین کانفیگ اختصاصی شما ساخته می‌شود.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = onManage,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("مشاهده پکیج‌ها")
                        }
                    }
                }
            }

            else -> {
                val visible = (
                    state.activeAccounts +
                        state.expiredAccounts +
                        state.supportAccounts
                    ).take(5)
                visible.forEach { account ->
                    ConfigRow(
                        account = account,
                        isActive = state.activeAccountId == account.id,
                        applying = state.applyingAccountId == account.id,
                        onOpen = { account.id?.let(onOpenDetail) },
                        onApply = { account.id?.let(onApply) },
                        onRenew = { account.id?.let(onRenew) }
                    )
                    Spacer(Modifier.height(10.dp))
                }
                if (state.accounts.size > visible.size) {
                    TextButton(
                        onClick = onManage,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("مشاهده ${state.accounts.size - visible.size} کانفیگ دیگر", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfigRow(
    account: HaioGateAccount,
    isActive: Boolean,
    applying: Boolean,
    onOpen: () -> Unit,
    onApply: () -> Unit,
    onRenew: () -> Unit
) {
    val category = account.category
    val canApply = category == AccountCategory.ACTIVE
    val statusColor = when (category) {
        AccountCategory.ACTIVE -> StatusGreen
        AccountCategory.EXPIRED -> StatusRed
        AccountCategory.SUPPORT -> StatusYellow
    }
    val total = account.totalTrafficBytes ?: 0L
    val usagePercent = if (total <= 0L) {
        0f
    } else {
        ((account.usageBytes ?: 0L).toDouble() / total * 100.0).toFloat()
    }
    val usersNote = if (account.isLegacyUnlimitedAccount) {
        "نامحدود (قدیمی)"
    } else {
        PortalFormat.membersLabel(account.includedUsers)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = if (isActive) BorderStroke(1.dp, StatusGreen.copy(alpha = 0.55f)) else null,
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) {
                StatusGreen.copy(alpha = 0.045f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(statusColor.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Cloud,
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = account.packageName.orEmpty().ifBlank { "پکیج هایو گیت" },
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = PortalFormat.statusLabel(account),
                            fontSize = 11.sp,
                            color = statusColor
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (isActive) {
                        PortalChip(text = "فعال", color = StatusGreen)
                    }
                    if (account.isDeviceLocked) {
                        PortalChip(text = "قفل دستگاه", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "مصرف: ${PortalFormat.usageDisplay(account)}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "انقضا: ${PortalFormat.date(account.expiredAt)}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(6.dp))
            PortalUsageBar(percent = usagePercent)

            if (usersNote != null) {
                Spacer(Modifier.height(8.dp))
                PortalChip(text = usersNote, color = MaterialTheme.colorScheme.primary)
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
            Spacer(Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onOpen,
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp)
                ) {
                    Text("جزئیات", fontSize = 12.sp)
                    Spacer(Modifier.width(2.dp))
                    Icon(
                        Icons.Default.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isActive) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = StatusGreen,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(3.dp))
                            Text(
                                text = "در حال استفاده",
                                fontSize = 11.sp,
                                color = StatusGreen
                            )
                        }
                    } else if (canApply) {
                        OutlinedButton(
                            onClick = onApply,
                            enabled = !applying,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            if (applying) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    Icons.Default.Link,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text("اعمال", fontSize = 12.sp)
                            }
                        }
                    }

                    if (category != AccountCategory.SUPPORT) {
                        Button(
                            onClick = onRenew,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text("تمدید", fontSize = 12.sp)
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = StatusYellow,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("پشتیبانی", fontSize = 11.sp, color = StatusYellow)
                        }
                    }
                }
            }
        }
    }
}
