package com.haio.bypass.ui.util

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haio.bypass.ui.theme.StatusGreen
import com.haio.bypass.ui.theme.StatusRed
import com.haio.bypass.ui.theme.StatusYellow

@Composable
fun ShimmerEffect(modifier: Modifier = Modifier, radius: Dp = 8.dp) {
    val shimmerColors = listOf(
        Color.LightGray.copy(alpha = 0.6f),
        Color.LightGray.copy(alpha = 0.2f),
        Color.LightGray.copy(alpha = 0.6f)
    )
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(radius))
            .background(
                brush = Brush.linearGradient(
                    colors = shimmerColors,
                    start = Offset.Zero,
                    end = Offset(x = translateAnim, y = translateAnim)
                )
            )
    )
}

@Composable
fun ShimmerCard(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().padding(16.dp)) {
        ShimmerEffect(modifier = Modifier.fillMaxWidth().height(20.dp))
        Spacer(modifier = Modifier.height(12.dp))
        ShimmerEffect(modifier = Modifier.fillMaxWidth(0.7f).height(16.dp))
        Spacer(modifier = Modifier.height(8.dp))
        ShimmerEffect(modifier = Modifier.fillMaxWidth(0.5f).height(16.dp))
    }
}

fun trafficProgressColor(progress: Float): Color = when {
    progress < 0.6f -> StatusGreen
    progress < 0.85f -> StatusYellow
    else -> StatusRed
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null
) {
    Column(modifier = modifier.padding(vertical = 4.dp)) {
        Text(
            text = title,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface
        )
        if (subtitle != null) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

fun formatTrafficMb(mb: Float): String {
    return if (mb >= 1024f) {
        String.format("%.1f GB", mb / 1024f)
    } else {
        String.format("%.1f MB", mb)
    }
}

fun formatDuration(days: Int): String {
    return when {
        days >= 365 -> "${days / 365} سال ${if (days % 365 > 0) "${days % 365} روز" else ""}"
        days >= 30 -> "${days / 30} ماه ${if (days % 30 > 0) "${days % 30} روز" else ""}"
        else -> "$days روز"
    }
}