package com.haio.bypass.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haio.bypass.HaioPrefs
import com.haio.bypass.config.ConfigManager
import com.haio.bypass.config.SubscriptionInfo
import com.haio.bypass.config.TrojanUrlParser
import com.haio.bypass.network.api.ApiClient
import com.haio.bypass.network.api.CreatePaymentRequest
import com.haio.bypass.network.api.CreateSubscriptionRequest
import com.haio.bypass.network.api.Plan
import com.haio.bypass.ui.theme.*
import com.haio.bypass.ui.util.*
import kotlinx.coroutines.launch

@Composable
fun StoreScreen(
    prefs: HaioPrefs,
    configManager: ConfigManager,
    subscriptionInfo: SubscriptionInfo? = null,
    onRefreshSub: () -> Unit = {},
    onShowSnackbar: (String) -> Unit
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        var plans by remember { mutableStateOf<List<Plan>>(emptyList()) }
        var isLoading by remember { mutableStateOf(true) }
        var errorMessage by remember { mutableStateOf<String?>(null) }
        val scope = rememberCoroutineScope()

        LaunchedEffect(Unit) {
            isLoading = true
            try {
                val response = ApiClient.getApiService().listPlans()
                if (response.isSuccessful) {
                    plans = response.body()?.results ?: emptyList()
                } else {
                    errorMessage = "خطا در دریافت پلن\u200Cها"
                }
            } catch (e: Exception) {
                errorMessage = "خطای شبکه: ${e.message}"
            }
            isLoading = false
        }

        val scrollState = rememberScrollState()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.ShoppingCart,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "فروشگاه",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            subscriptionInfo?.let { sub ->
                com.haio.bypass.ui.components.TrafficInfoCard(
                    subscription = sub,
                    compact = true
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (subscriptionInfo != null) {
                Text(
                    text = "پلن\u200Cهای موجود",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }

            if (isLoading) {
                repeat(3) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        ShimmerCard()
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
            } else if (errorMessage != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = errorMessage!!,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 14.sp
                        )
                    }
                }
            } else if (plans.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "پلنی برای نمایش وجود ندارد",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                }
            } else {
                plans.forEach { plan ->
                    EnhancedPlanCard(
                        plan = plan,
                        isActive = subscriptionInfo?.title == plan.name,
                        onBuy = { method ->
                            scope.launch {
                                handlePurchase(prefs, configManager, plan, method, onShowSnackbar)
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

enum class PurchaseMethod(val label: String) {
    CAFEBAZAR("پرداخت با کافه\u200Cبازار"),
    BALANCE("پرداخت با کیف پول")
}

@Composable
private fun EnhancedPlanCard(
    plan: Plan,
    isActive: Boolean = false,
    onBuy: (PurchaseMethod) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = if (isActive) 4.dp else 1.dp,
                shape = RoundedCornerShape(20.dp)
            ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = plan.name,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (isActive) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = StatusGreen.copy(alpha = 0.15f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = StatusGreen,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "فعال",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = StatusGreen
                                )
                            }
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                ) {
                    Text(
                        text = "${plan.priceToman} تومان",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Cloud,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(
                                text = formatTrafficGbMb(plan.trafficGb, plan.trafficMb),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "حجم ترافیک",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Schedule,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(
                                text = "${plan.durationDays} روز",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "مدت اعتبار",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (!isActive) {
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { onBuy(PurchaseMethod.CAFEBAZAR) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            Icons.Default.ShoppingCart,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("کافه\u200Cبازار", fontSize = 14.sp)
                    }

                    OutlinedButton(
                        onClick = { onBuy(PurchaseMethod.BALANCE) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            brush = androidx.compose.ui.graphics.SolidColor(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            )
                        )
                    ) {
                        Icon(
                            Icons.Default.AccountBalanceWallet,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("کیف پول", fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

private fun formatTrafficGbMb(gb: Double, mb: Double): String {
    return when {
        gb >= 1.0 -> "${String.format("%.1f", gb)} GB"
        else -> "${String.format("%.0f", mb)} MB"
    }
}

private suspend fun handlePurchase(
    prefs: HaioPrefs,
    configManager: ConfigManager,
    plan: Plan,
    method: PurchaseMethod,
    onShowSnackbar: (String) -> Unit
) {
    try {
        val api = ApiClient.getApiService()
        val tokenSet = ensureAccessToken(prefs)
        if (!tokenSet) {
            onShowSnackbar("ابتدا حساب کاربری خود را فعال کنید")
            return
        }

        when (method) {
            PurchaseMethod.CAFEBAZAR -> {
                val paymentResponse = api.createPayment(CreatePaymentRequest(plan = plan.slug))
                if (paymentResponse.isSuccessful) {
                    val payment = paymentResponse.body()!!
                    onShowSnackbar("درخواست پرداخت ثبت شد. ID: ${payment.id}")
                } else {
                    onShowSnackbar("خطا در ایجاد پرداخت: ${paymentResponse.code()}")
                }
            }
            PurchaseMethod.BALANCE -> {
                val subResponse = api.createSubscription(
                    CreateSubscriptionRequest(
                        plan = plan.slug,
                        title = plan.name,
                        deviceId = prefs.deviceId
                    )
                )
                if (subResponse.isSuccessful) {
                    val sub = subResponse.body()!!
                    val configUrl = sub.trojanConfigUrl
                    if (configUrl != null) {
                        prefs.trojanConfigUrl = configUrl
                        prefs.subscriptionUuid = sub.uuid
                        prefs.subscriptionTitle = sub.title
                        prefs.subscriptionTrafficMb = sub.trafficTotalMb.toFloat()
                        prefs.subscriptionTrafficUsedMb = sub.trafficUsageMb.toFloat()
                        prefs.subscriptionExpiry = sub.expiredAt
                        val parsed = TrojanUrlParser.parse(configUrl)
                        if (parsed != null) {
                            configManager.updateConfig {
                                it.copy(trojanUrl = configUrl, trojanConfig = parsed)
                            }
                        }
                    }
                    onShowSnackbar("اشتراک ${plan.name} فعال شد!")
                } else {
                    val detail = subResponse.errorBody()?.string() ?: "خطای نامشخص"
                    onShowSnackbar("خرید ناموفق: $detail")
                }
            }
        }
    } catch (e: Exception) {
        onShowSnackbar("خطای شبکه: ${e.message}")
    }
}

private suspend fun ensureAccessToken(prefs: HaioPrefs): Boolean {
    val accessToken = prefs.jwtAccessToken
    if (!accessToken.isNullOrBlank()) {
        ApiClient.setAccessToken(accessToken)
        return true
    }
    val refreshed = ApiClient.tryRefreshAccessToken(prefs)
    if (refreshed != null) {
        ApiClient.setAccessToken(refreshed)
        return true
    }
    return false
}