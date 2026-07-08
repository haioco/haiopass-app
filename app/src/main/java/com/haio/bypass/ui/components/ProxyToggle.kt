package com.haio.bypass.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haio.bypass.ui.theme.Connected
import com.haio.bypass.ui.theme.Connecting
import com.haio.bypass.ui.theme.Disconnected

@Composable
fun ProxyToggle(
    isActive: Boolean,
    isConnecting: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val statusColor by animateColorAsState(
        targetValue = when {
            isActive -> Connected
            isConnecting -> Connecting
            else -> Disconnected
        },
        animationSpec = tween(300),
        label = "statusColor"
    )

    val scale by animateFloatAsState(
        targetValue = if (isActive) 1.1f else 1f,
        animationSpec = tween(200),
        label = "scale"
    )

    Box(
        modifier = modifier
            .size(160.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(statusColor.copy(alpha = 0.2f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = when {
                isActive -> "ON"
                isConnecting -> "..."
                else -> "OFF"
            },
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = statusColor
        )
    }
}
