package com.haio.bypass.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haio.bypass.config.AppConfig
import com.haio.bypass.ui.theme.Connected
import com.haio.bypass.ui.theme.Connecting
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
    val isConnecting = statusMessage.contains("Connecting")

    val statusColor by animateColorAsState(
        targetValue = when {
            isConnecting -> Connecting
            isVpnRunning -> Connected
            else -> Disconnected
        },
        animationSpec = tween(200),
        label = "statusColor"
    )

    val toggleScale by animateFloatAsState(
        targetValue = if (isVpnRunning) 1.08f else 1f,
        animationSpec = tween(150),
        label = "toggleScale"
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
                .size(140.dp)
                .scale(toggleScale)
                .clip(CircleShape)
                .background(statusColor.copy(alpha = 0.15f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onToggleVpn(!isVpnRunning) },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = when {
                    isConnecting -> "●"
                    isVpnRunning -> "ON"
                    else -> "OFF"
                },
                fontSize = if (isConnecting) 40.sp else 28.sp,
                fontWeight = FontWeight.Bold,
                color = statusColor
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Surface),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = statusMessage.ifEmpty { "Disconnected" },
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = statusColor
                    )
                    IconButton(
                        onClick = onRefreshDomains,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))

                Spacer(modifier = Modifier.height(10.dp))

                config.trojanConfig?.let { tc ->
                    InfoRow("Server", tc.server)
                    InfoRow("Port", tc.port.toString())
                    InfoRow("SNI", tc.sni)
                } ?: run {
                    Text(
                        text = "Tap to configure Trojan",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 14.sp,
                        modifier = Modifier.clickable { onConfigureClick() }
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                InfoRow("Domains", if (domainCount > 0) "$domainCount active" else "None loaded")
            }
        }

        TextButton(
            onClick = { /* Open console.haio.ir */ }
        ) {
            Text(
                text = "Buy Config",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}