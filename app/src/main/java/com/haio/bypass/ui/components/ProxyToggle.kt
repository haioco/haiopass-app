package com.haio.bypass.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
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
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        val haptic = LocalHapticFeedback.current

        val statusColor by animateColorAsState(
            targetValue = when {
                isActive -> Connected
                isConnecting -> Connecting
                else -> Disconnected
            },
            animationSpec = tween(400, easing = EaseInOutCubic),
            label = "statusColor"
        )

        val scale by animateFloatAsState(
            targetValue = if (isActive) 1.08f else 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            ),
            label = "scale"
        )

        val pulseState = if (isConnecting) {
            val trans = rememberInfiniteTransition(label = "pulse")
            trans.animateFloat(
                initialValue = 0.92f,
                targetValue = 1.05f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1000, easing = EaseInOutCubic),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "pulse"
            )
        } else {
            animateFloatAsState(
                targetValue = 1f,
                label = "pulse"
            )
        }
        val pulse = pulseState.value

        val glowAlpha by if (isConnecting) {
            val trans = rememberInfiniteTransition(label = "glow")
            trans.animateFloat(
                initialValue = 0.12f,
                targetValue = 0.25f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1200, easing = EaseInOutCubic),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "glow"
            )
        } else {
            animateFloatAsState(
                targetValue = 0.12f,
                label = "glow"
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = modifier
        ) {
            Box(
                modifier = Modifier
                    .size(152.dp)
                    .scale(scale * if (isConnecting) pulse else 1f),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(152.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    statusColor.copy(alpha = glowAlpha * 0.5f),
                                    Color.Transparent
                                )
                            )
                        )
                )

                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .shadow(
                            elevation = if (isActive) 12.dp else 4.dp,
                            shape = CircleShape,
                            ambientColor = statusColor.copy(alpha = 0.3f),
                            spotColor = statusColor.copy(alpha = 0.3f)
                        )
                        .clip(CircleShape)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    statusColor.copy(alpha = 0.15f),
                                    statusColor.copy(alpha = 0.05f)
                                )
                            )
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            onClick()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        statusColor,
                                        statusColor.copy(alpha = 0.7f)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            AnimatedVisibility(
                                visible = isConnecting,
                                enter = scaleIn() + fadeIn(),
                                exit = scaleOut() + fadeOut()
                            ) {
                                Text(
                                    text = "...",
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                            AnimatedVisibility(
                                visible = !isConnecting,
                                enter = scaleIn() + fadeIn(),
                                exit = scaleOut() + fadeOut()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PowerSettingsNew,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Surface(
                color = statusColor.copy(alpha = 0.12f),
                shape = RoundedCornerShape(20.dp)
            ) {
                Text(
                    text = when {
                        isConnecting -> "در حال اتصال"
                        isActive -> "متصل"
                        else -> "برای اتصال ضربه بزنید"
                    },
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = statusColor
                )
            }
        }
    }
}