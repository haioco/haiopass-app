package com.haio.bypass.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haio.bypass.config.AppConfig
import com.haio.bypass.ui.theme.Accent
import com.haio.bypass.ui.theme.Connecting
import com.haio.bypass.ui.theme.Connected
import com.haio.bypass.ui.theme.Disconnected
import com.haio.bypass.ui.theme.Surface

@Composable
fun MainScreen(
    config: AppConfig,
    isVpnRunning: Boolean,
    statusMessage: String,
    domainCount: Int,
    onToggleVpn: (Boolean) -> Unit,
    onRefreshDomains: () -> Unit,
    onConfigureClick: () -> Unit = {}
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        val isConnecting = statusMessage.contains("اتصال", ignoreCase = true) ||
                statusMessage.contains("Connecting", ignoreCase = true)

        val statusColor by animateColorAsState(
            targetValue = when {
                isConnecting -> Connecting
                isVpnRunning -> Connected
                else -> Disconnected
            },
            animationSpec = tween(250),
            label = "statusColor"
        )

        val toggleScale by animateFloatAsState(
            targetValue = if (isVpnRunning) 1.06f else 1f,
            animationSpec = tween(180),
            label = "toggleScale"
        )

        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
        val pulse by infiniteTransition.animateFloat(
            initialValue = 0.85f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
            label = "pulse"
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically)
        ) {
            Box(
                modifier = Modifier
                    .size(150.dp)
                    .scale(toggleScale * if (isConnecting) pulse else 1f)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(statusColor.copy(alpha = 0.22f), Color.Transparent)
                        )
                    )
                    .border(
                        width = if (isVpnRunning || isConnecting) 2.dp else 1.dp,
                        color = statusColor.copy(alpha = if (isVpnRunning) 0.6f else 0.3f),
                        shape = CircleShape
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onToggleVpn(!isVpnRunning) },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.PowerSettingsNew,
                        contentDescription = if (isVpnRunning) "قطع اتصال" else "اتصال",
                        tint = statusColor,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    when {
                        isConnecting -> Text(
                            text = "در حال اتصال",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = statusColor
                        )
                        isVpnRunning -> Text(
                            text = "متصل",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = statusColor
                        )
                        else -> Text(
                            text = "برای اتصال ضربه بزنید",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = statusColor
                        )
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Surface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = statusColor,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isVpnRunning) statusMessage.ifEmpty { "متصل" }
                                else if (isConnecting) statusMessage.ifEmpty { "در حال اتصال" }
                                else "قطع شده",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = statusColor
                            )
                        }
                        IconButton(
                            onClick = onRefreshDomains,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "به‌روزرسانی دامنه‌ها",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))

                    Spacer(modifier = Modifier.height(14.dp))

                    config.trojanConfig?.let { tc ->
                        InfoRow(icon = Icons.Default.VpnKey, label = "سرور", value = tc.server)
                        InfoRow(icon = Icons.Default.Public, label = "SNI", value = tc.sni)
                        InfoRow(icon = Icons.Default.Dns, label = "پورت", value = tc.port.toString())
                    } ?: run {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .clickable { onConfigureClick() }
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.VpnKey,
                                contentDescription = null,
                                tint = Accent,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "برای تنظیم تروجان ضربه بزنید",
                                color = Accent,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    InfoRow(
                        icon = Icons.Default.Dns,
                        label = "دامنه‌ها",
                        value = if (domainCount > 0) "$domainCount فعال" else "هیچ‌کدام بارگذاری نشده"
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
