package com.haio.bypass.ui.screens

import android.content.Context
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.haio.bypass.R
import com.haio.bypass.network.haiocloud.AccountCategory
import com.haio.bypass.network.haiocloud.HaioGateAccount
import com.haio.bypass.network.haiocloud.PendingStatus
import com.haio.bypass.network.haiocloud.PortalBrowser
import com.haio.bypass.network.haiocloud.PortalContainer
import com.haio.bypass.network.haiocloud.PortalFormat
import com.haio.bypass.network.haiocloud.PurchaseMode
import com.haio.bypass.ui.portal.PortalChip
import com.haio.bypass.ui.portal.PortalErrorBanner
import com.haio.bypass.ui.portal.PortalSectionTitle
import com.haio.bypass.ui.portal.PortalUsageBar
import com.haio.bypass.ui.theme.StatusGreen
import com.haio.bypass.ui.theme.StatusRed
import com.haio.bypass.ui.theme.StatusYellow
import com.haio.bypass.ui.viewmodel.HaioGateViewModel
import com.haio.bypass.ui.viewmodel.PortalViewModelFactory

/**
 * HaioGate tab: login card for guests, grouped accounts for logged-in users,
 * pending payment banner and buy action.
 */
@Composable
fun HaioGateScreen(
    container: PortalContainer,
    isLoggedIn: Boolean,
    onLogin: () -> Unit,
    onOpenConfig: () -> Unit,
    onOpenDetail: (String) -> Unit,
    onBuy: () -> Unit,
    onRenew: (String) -> Unit,
    onShowSnackbar: (String) -> Unit
) {
    val context = LocalContext.current
    val factory = remember(container) { PortalViewModelFactory(container) }
    val viewModel: HaioGateViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsState()

    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn) viewModel.refresh()
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    var resumedOnce by remember { mutableStateOf(false) }
    DisposableEffect(isLoggedIn, lifecycleOwner) {
        if (!isLoggedIn) {
            onDispose { }
        } else {
            val lifecycle = lifecycleOwner.lifecycle
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    viewModel.loadPending(checkNow = true)
                    // Returning from the payment browser/package picker: reload.
                    if (resumedOnce) viewModel.refresh() else resumedOnce = true
                }
            }
            lifecycle.addObserver(observer)
            onDispose { lifecycle.removeObserver(observer) }
        }
    }

    LaunchedEffect(state.paymentUrl) {
        val url = state.paymentUrl ?: return@LaunchedEffect
        val opened = PortalBrowser.open(context, url)
        viewModel.consumePaymentUrl()
        if (!opened) onShowSnackbar("باز کردن درگاه پرداخت ممکن نشد. دوباره تلاش کنید.")
    }

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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "هایو گیت",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = if (isLoggedIn) "اکانت‌ها، مصرف و تمدید" else "خرید و مدیریت اکانت‌های هایو گیت",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isLoggedIn) {
                IconButton(
                    onClick = { viewModel.refresh() },
                    enabled = !state.refreshing
                ) {
                    if (state.refreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = "به‌روزرسانی")
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        if (!isLoggedIn) {
            GuestLoginCard(onLogin = onLogin, onOpenConfig = onOpenConfig)
            Spacer(Modifier.height(12.dp))
            LegacyHintCard()
            return@Column
        }

        PendingBanner(
            pendingStatus = state.pendingStatus,
            pendingTitle = state.pending?.let { pending ->
                if (pending.mode == PurchaseMode.RENEW) "تمدید در انتظار پرداخت"
                else "خرید در انتظار پرداخت"
            },
            busy = state.pendingBusy,
            onCheck = { viewModel.checkPending() },
            onResume = { viewModel.resumePayment() }
        )

        if (state.pending != null && state.pendingStatus == null) {
            Spacer(Modifier.height(10.dp))
        }

        state.error?.let { error ->
            Spacer(Modifier.height(10.dp))
            PortalErrorBanner(message = error, onRetry = { viewModel.refresh() })
            Spacer(Modifier.height(6.dp))
        }

        if (state.loading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
            }
            return@Column
        }

        val active = state.activeAccounts
        val expired = state.expiredAccounts
        val support = state.supportAccounts

        if (state.accounts.isEmpty()) {
            EmptyAccountsCard(onBuy = onBuy, packagesAvailable = state.packages.isNotEmpty())
        } else {
            if (active.isNotEmpty()) {
                PortalSectionTitle("اکانت‌های فعال")
                active.forEach { account ->
                    AccountCard(
                        account = account,
                        onOpen = { onOpenDetail(account.id.orEmpty()) },
                        onRenew = { onRenew(account.id.orEmpty()) }
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }

            if (expired.isNotEmpty()) {
                PortalSectionTitle("اکانت‌های منقضی شده")
                expired.forEach { account ->
                    AccountCard(
                        account = account,
                        onOpen = { onOpenDetail(account.id.orEmpty()) },
                        onRenew = { onRenew(account.id.orEmpty()) }
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }

            if (support.isNotEmpty()) {
                PortalSectionTitle("نیازمند پشتیبانی")
                support.forEach { account ->
                    AccountCard(
                        account = account,
                        onOpen = { onOpenDetail(account.id.orEmpty()) },
                        onRenew = null
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        Button(
            onClick = onBuy,
            modifier = Modifier.fillMaxWidth(),
            enabled = state.packages.isNotEmpty(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("خرید پکیج جدید", fontSize = 14.sp)
        }

        if (state.packages.isEmpty() && state.accounts.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "در حال حاضر پکیج فعالی برای خرید وجود ندارد.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun GuestLoginCard(onLogin: () -> Unit, onOpenConfig: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        )
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.guest_haiogate_title),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.guest_haiogate_body),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = onLogin,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(stringResource(R.string.guest_haiogate_login))
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onOpenConfig,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.guest_haiogate_config), fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun LegacyHintCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Text(
            text = "پلن‌های قبلی در تب «پلن‌های قبلی» بدون تغییر در دسترس هستند.",
            modifier = Modifier.padding(14.dp),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PendingBanner(
    pendingStatus: PendingStatus?,
    pendingTitle: String?,
    busy: Boolean,
    onCheck: () -> Unit,
    onResume: () -> Unit
) {
    if (pendingStatus == null && pendingTitle == null) return
    if (pendingStatus is PendingStatus.Paid ||
        pendingStatus is PendingStatus.Failed ||
        pendingStatus is PendingStatus.Expired
    ) {
        return
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = StatusYellow.copy(alpha = 0.14f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Schedule,
                    contentDescription = null,
                    tint = StatusYellow,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = pendingTitle ?: "پرداخت در انتظار تایید",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "پس از پرداخت، به برنامه بازگردید تا وضعیت خرید بررسی شود.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onCheck,
                    enabled = !busy,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    if (busy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text("بررسی وضعیت", fontSize = 12.sp)
                    }
                }
                OutlinedButton(
                    onClick = onResume,
                    enabled = !busy,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("ادامه پرداخت", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun EmptyAccountsCard(onBuy: () -> Unit, packagesAvailable: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Cloud,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "هنوز اکانت هایو گیت ندارید.",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (packagesAvailable) {
                    "یک پکیج انتخاب کنید؛ پس از پرداخت، اکانت شما ساخته می‌شود."
                } else {
                    "در حال حاضر پکیج فعالی برای خرید وجود ندارد."
                },
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onBuy,
                enabled = packagesAvailable,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("خرید پکیج", fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun AccountCard(
    account: HaioGateAccount,
    onOpen: () -> Unit,
    onRenew: (() -> Unit)?
) {
    val category = account.category
    val statusColor = when (category) {
        AccountCategory.ACTIVE -> StatusGreen
        AccountCategory.EXPIRED -> StatusRed
        AccountCategory.SUPPORT -> StatusYellow
    }
    val usagePercent = run {
        val total = account.totalTrafficBytes ?: 0L
        if (total <= 0L) 0f else ((account.usageBytes ?: 0L).toDouble() / total * 100.0).toFloat()
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
                            fontSize = 15.sp,
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
                if (account.isLegacy == true) {
                    PortalChip(text = "قدیمی", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(Modifier.height(12.dp))

            PortalUsageBar(percent = usagePercent)
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = PortalFormat.usageDisplay(account),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "انقضا: ${PortalFormat.date(account.expiredAt)}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            val usersNote = if (account.isLegacyUnlimitedAccount) {
                "نامحدود (قدیمی)"
            } else {
                PortalFormat.membersLabel(account.includedUsers)
            }
            if (usersNote != null || (account.pendingReservesCount ?: 0) > 0) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (usersNote != null) {
                        PortalChip(text = usersNote, color = MaterialTheme.colorScheme.primary)
                    }
                    if ((account.pendingReservesCount ?: 0) > 0) {
                        PortalChip(
                            text = "${account.pendingReservesCount} تمدید رزرو شده",
                            color = StatusYellow
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onOpen) {
                    Text("جزئیات اکانت", fontSize = 12.sp)
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.Default.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                }
                if (onRenew != null) {
                    Button(
                        onClick = onRenew,
                        shape = RoundedCornerShape(10.dp)
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
                        Text(
                            text = "نیازمند بررسی پشتیبانی",
                            fontSize = 11.sp,
                            color = StatusYellow
                        )
                    }
                }
            }
        }
    }
}
