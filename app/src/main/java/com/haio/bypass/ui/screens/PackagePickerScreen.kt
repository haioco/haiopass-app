package com.haio.bypass.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.haio.bypass.network.haiocloud.AccountCategory
import com.haio.bypass.network.haiocloud.HaioGatePackage
import com.haio.bypass.network.haiocloud.PortalBrowser
import com.haio.bypass.network.haiocloud.PortalContainer
import com.haio.bypass.network.haiocloud.PortalFormat
import com.haio.bypass.network.haiocloud.PurchaseMode
import com.haio.bypass.ui.portal.PortalChip
import com.haio.bypass.ui.portal.PortalErrorBanner
import com.haio.bypass.ui.portal.PortalHeader
import com.haio.bypass.ui.theme.StatusYellow
import com.haio.bypass.ui.viewmodel.PortalViewModelFactory
import com.haio.bypass.ui.viewmodel.PurchaseViewModel

/**
 * Package picker + confirmation for buy and renew.
 *
 * Handles Shahkar/login gates, organization selection and the Zibal round-trip.
 * The purchase is persisted before the browser opens, so returning or a process
 * death can be reconciled by the HaioGate tab.
 */
@Composable
fun PackagePickerScreen(
    container: PortalContainer,
    mode: PurchaseMode,
    accountId: String?,
    onBack: () -> Unit,
    onRequireLogin: () -> Unit,
    onRequireShahkar: () -> Unit,
    onPaymentStarted: () -> Unit,
    onSuccess: (String?) -> Unit,
    onShowSnackbar: (String) -> Unit
) {
    val context = LocalContext.current
    val factory = remember(container) { PortalViewModelFactory(container) }
    val viewModel: PurchaseViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsState()

    var showConfirm by rememberSaveable { mutableStateOf(false) }
    var showOrgPicker by remember { mutableStateOf(false) }

    LaunchedEffect(mode, accountId) { viewModel.load(mode, accountId) }

    LaunchedEffect(state.requiresLogin) {
        if (state.requiresLogin) {
            viewModel.consumeRequiresLogin()
            onRequireLogin()
        }
    }

    LaunchedEffect(state.requiresShahkar) {
        if (state.requiresShahkar) {
            viewModel.consumeRequiresShahkar()
            onRequireShahkar()
        }
    }

    LaunchedEffect(state.openPaymentUrl) {
        val url = state.openPaymentUrl ?: return@LaunchedEffect
        val opened = PortalBrowser.open(context, url)
        viewModel.consumePaymentUrl()
        if (opened) {
            onPaymentStarted()
        } else {
            onShowSnackbar("باز کردن درگاه پرداخت ممکن نشد. دوباره تلاش کنید.")
        }
    }

    LaunchedEffect(state.success) {
        if (state.success) {
            viewModel.consumeSuccess()
            onSuccess(state.accountId)
        }
    }

    LaunchedEffect(state.showOrganizationPicker) {
        showOrgPicker = state.showOrganizationPicker
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        PortalHeader(
            title = if (mode == PurchaseMode.RENEW) "تمدید اکانت" else "خرید پکیج جدید",
            subtitle = if (mode == PurchaseMode.RENEW) {
                "پکیج فعلی یا پکیج دیگری را انتخاب کنید"
            } else {
                "پس از پرداخت، اکانت جدید ساخته می‌شود"
            },
            onBack = onBack
        )

        Spacer(Modifier.height(14.dp))

        state.error?.let { error ->
            PortalErrorBanner(
                message = error,
                onRetry = { viewModel.load(mode, accountId, force = true) }
            )
            Spacer(Modifier.height(10.dp))
        }

        val account = state.account
        if (mode == PurchaseMode.RENEW && account != null) {
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
                        Column {
                            Text(
                                text = account.packageName.orEmpty().ifBlank { "اکانت فعلی" },
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "انقضا: ${PortalFormat.date(account.expiredAt)}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        PortalChip(
                            text = if (account.category == AccountCategory.ACTIVE) "فعال" else "منقضی",
                            color = if (account.category == AccountCategory.ACTIVE) {
                                com.haio.bypass.ui.theme.StatusGreen
                            } else {
                                com.haio.bypass.ui.theme.StatusRed
                            }
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        if (state.loading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 40.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
            }
            return@Column
        }

        if (state.packages.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Text(
                    text = "در حال حاضر پکیج فعالی برای خرید وجود ندارد.",
                    modifier = Modifier.padding(18.dp),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Column
        }

        if (mode == PurchaseMode.RENEW && account != null) {
            val currentPackageActive = account.packageId != null &&
                state.packages.any { it.id == account.packageId }
            if (!currentPackageActive) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = StatusYellow.copy(alpha = 0.12f)
                    )
                ) {
                    Text(
                        text = "پکیج فعلی شما غیرفعال شده است؛ یک پکیج فعال انتخاب کنید. " +
                            "اکانت فعلی با پکیج جدید تمدید می‌شود.",
                        modifier = Modifier.padding(14.dp),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(Modifier.height(10.dp))
            }
        }

        state.packages.forEach { pkg ->
            PackageCard(
                pkg = pkg,
                selected = state.selectedPackageId == pkg.id,
                isCurrentPackage = account?.packageId != null && account.packageId == pkg.id,
                onClick = { pkg.id?.let(viewModel::selectPackage) }
            )
            Spacer(Modifier.height(10.dp))
        }

        val selected = state.selectedPackage
        if (mode == PurchaseMode.BUY && selected != null && state.existingAccountForSelection != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = StatusYellow.copy(alpha = 0.12f)
                )
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = StatusYellow,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "شما از این پکیج اکانت دارید",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "خرید جدید یک کانفیگ جدا می‌سازد؛ برای افزودن کاربر یا تمدید، " +
                            "از دکمه تمدید همان اکانت استفاده کنید.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        if (mode == PurchaseMode.RENEW && account != null && account.isLegacyUnlimitedAccount) {
            ConversionWarning(
                text = if (account.category == AccountCategory.ACTIVE) {
                    "دوره فعلی بدون تغییر ادامه دارد. در شروع دوره جدید، سقف " +
                        "${selected?.usersCount ?: 1} کاربر اعمال می‌شود و لینک فعلی تغییر می‌کند."
                } else {
                    "این اکانت قدیمی است و فعلاً محدودیت کاربر ندارد. با تمدید، سقف " +
                        "${selected?.usersCount ?: 1} کاربر اعمال می‌شود و لینک اتصال فعلی تغییر می‌کند. " +
                        "کاربران جدید را پس از پرداخت اضافه می‌کنید."
                }
            )
            Spacer(Modifier.height(10.dp))
        }

        if (mode == PurchaseMode.RENEW && account != null && !account.isLegacyUnlimitedAccount) {
            val newUsers = selected?.usersCount
            val currentUsers = account.includedUsers
            if (newUsers != null && currentUsers != null && newUsers != currentUsers) {
                ConversionWarning(
                    text = "با تمدید این پلن، تعداد کاربران از $currentUsers به $newUsers تغییر می‌کند. " +
                        "در شروع دوره جدید لینک فعلی تغییر می‌کند و کاربران را دوباره اضافه می‌کنید."
                )
                Spacer(Modifier.height(10.dp))
            }
        }

        Spacer(Modifier.height(6.dp))

        Button(
            onClick = { showConfirm = true },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.submitting && selected != null,
            shape = RoundedCornerShape(12.dp)
        ) {
            if (state.submitting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text(
                    text = if (mode == PurchaseMode.RENEW) "ادامه تمدید" else "ادامه خرید",
                    fontSize = 14.sp
                )
            }
        }

        Spacer(Modifier.height(16.dp))
    }

    val selectedPackage = state.selectedPackage
    if (showConfirm && selectedPackage != null) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = {
                Text(if (mode == PurchaseMode.RENEW) "تایید تمدید" else "تایید خرید")
            },
            text = {
                Column {
                    Text(
                        text = selectedPackage.displayName,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "حجم: ${PortalFormat.traffic(selectedPackage.trafficBytes)}" +
                            (selectedPackage.usersCount?.let { " • $it کاربر" } ?: ""),
                        fontSize = 12.sp
                    )
                    Text(
                        text = "مبلغ: ${PortalFormat.price(selectedPackage.price)}",
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = when {
                            mode == PurchaseMode.BUY ->
                                "پس از پرداخت، اکانت هایو گیت ساخته می‌شود و می‌توانید آن را روی اپ اعمال کنید."
                            state.account?.category == AccountCategory.ACTIVE ->
                                "تمدید شما رزرو می‌شود و پس از پایان دوره فعلی فعال می‌شود. " +
                                    "تا آن زمان اکانت فعلی بدون تغییر باقی می‌ماند." +
                                    if (state.account?.isLegacyUnlimitedAccount == true) {
                                        " در شروع دوره جدید لینک فعلی تغییر می‌کند."
                                    } else ""
                            else ->
                                "پس از پرداخت، همین اکانت فعال می‌شود و اعتبار قبلی حفظ می‌شود."
                        },
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    showConfirm = false
                    viewModel.confirm()
                }) {
                    Text(if (selectedPackage.isFreePlan) "فعال‌سازی" else "پرداخت")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) { Text("انصراف") }
            },
            shape = RoundedCornerShape(18.dp)
        )
    }

    if (showOrgPicker && state.organizationOptions.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = {
                showOrgPicker = false
                viewModel.dismissOrganizationPicker()
            },
            title = { Text("انتخاب حساب سازمانی") },
            text = {
                Column {
                    Text(
                        text = "این خرید روی کدام حساب سازمانی ثبت شود؟",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    state.organizationOptions.forEach { org ->
                        OutlinedButton(
                            onClick = {
                                showOrgPicker = false
                                viewModel.dismissOrganizationPicker()
                                viewModel.confirm(organizationId = org.id)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(org.name.orEmpty().ifBlank { "سازمان ${org.id}" })
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = {
                    showOrgPicker = false
                    viewModel.dismissOrganizationPicker()
                }) { Text("انصراف") }
            },
            shape = RoundedCornerShape(18.dp)
        )
    }
}

@Composable
private fun PackageCard(
    pkg: HaioGatePackage,
    selected: Boolean,
    isCurrentPackage: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Icon(
                        if (selected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = pkg.displayName,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = PortalFormat.price(pkg.price),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PortalChip(
                    text = PortalFormat.traffic(pkg.trafficBytes),
                    color = MaterialTheme.colorScheme.primary
                )
                pkg.usersCount?.let { users ->
                    PortalChip(text = "$users کاربر", color = StatusYellow)
                }
                if (isCurrentPackage) {
                    PortalChip(text = "پکیج فعلی", color = com.haio.bypass.ui.theme.StatusGreen)
                }
            }

            if (!pkg.description.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = pkg.description.orEmpty(),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ConversionWarning(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = StatusYellow.copy(alpha = 0.12f))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            androidx.compose.material3.Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = StatusYellow,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = text,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
