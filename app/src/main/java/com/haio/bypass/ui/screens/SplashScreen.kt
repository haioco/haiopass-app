package com.haio.bypass.ui.screens

import androidx.compose.animation.core.EaseInCubic
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haio.bypass.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SplashScreen(onSplashFinished: () -> Unit) {
    val alpha = remember { Animatable(0f) }
    val scale = remember { Animatable(0.78f) }
    val taglineAlpha = remember { Animatable(0f) }
    val taglineOffset = remember { Animatable(18f) }

    val infinite = rememberInfiniteTransition(label = "splash")
    val ringRotation by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 9000, easing = LinearEasing)
        ),
        label = "ring"
    )
    val pulse by infinite.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    LaunchedEffect(Unit) {
        alpha.animateTo(1f, tween(durationMillis = 450, easing = EaseOutCubic))
        launch {
            scale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        }
        delay(260)
        launch {
            taglineAlpha.animateTo(1f, tween(durationMillis = 420, easing = EaseOutCubic))
        }
        launch {
            taglineOffset.animateTo(0f, tween(durationMillis = 420, easing = EaseOutCubic))
        }
        delay(850)
        alpha.animateTo(0f, tween(durationMillis = 320, easing = EaseInCubic))
        onSplashFinished()
    }

    val primary = MaterialTheme.colorScheme.primary
    val background = MaterialTheme.colorScheme.background

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        primary.copy(alpha = 0.26f),
                        background,
                        background
                    )
                )
            )
    ) {
        // Soft ambient glow behind the mark.
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primary.copy(alpha = 0.28f),
                        Color.Transparent
                    ),
                    center = Offset(size.width / 2f, size.height * 0.40f),
                    radius = size.minDimension * 0.62f
                ),
                radius = size.minDimension * 0.62f,
                center = Offset(size.width / 2f, size.height * 0.40f)
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .alpha(alpha.value),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.size(250.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .scale(pulse)
                ) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val radius = size.minDimension * 0.42f

                    drawCircle(
                        color = primary.copy(alpha = 0.08f),
                        radius = radius,
                        center = center
                    )
                    drawCircle(
                        color = primary.copy(alpha = 0.20f),
                        radius = radius,
                        center = center,
                        style = Stroke(width = 1.2.dp.toPx())
                    )

                    rotate(degrees = ringRotation, pivot = center) {
                        drawArc(
                            brush = Brush.sweepGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    primary.copy(alpha = 0.85f),
                                    primary.copy(alpha = 0.18f),
                                    Color.Transparent
                                ),
                                center = center
                            ),
                            startAngle = 20f,
                            sweepAngle = 170f,
                            useCenter = false,
                            topLeft = Offset(center.x - radius, center.y - radius),
                            size = androidx.compose.ui.geometry.Size(radius * 2f, radius * 2f),
                            style = Stroke(width = 2.4.dp.toPx())
                        )
                    }

                    // Small orbiting spark.
                    val sparkAngle = Math.toRadians(ringRotation.toDouble())
                    val spark = Offset(
                        center.x + (radius * kotlin.math.cos(sparkAngle)).toFloat(),
                        center.y + (radius * kotlin.math.sin(sparkAngle)).toFloat()
                    )
                    drawCircle(
                        color = primary,
                        radius = 3.2.dp.toPx(),
                        center = spark
                    )
                }

                Surface(
                    modifier = Modifier
                        .size(136.dp)
                        .scale(scale.value)
                        .shadow(22.dp, RoundedCornerShape(38.dp)),
                    shape = RoundedCornerShape(38.dp),
                    color = Color.White
                ) {
                    Box(
                        modifier = Modifier.padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.haio_logo_new),
                            contentDescription = "HaioBypass Logo",
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(26.dp))

            Text(
                text = "HaioBypass",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.4.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(7.dp))

            Text(
                text = "تحریم‌شکن هوشمند هایو",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .alpha(taglineAlpha.value)
                    .offset(y = taglineOffset.value.dp)
            )

            Spacer(modifier = Modifier.height(22.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(3) { index ->
                    Box(
                        modifier = Modifier
                            .size(if (index == 1) 7.dp else 5.dp)
                            .background(
                                color = primary.copy(alpha = 0.35f + index * 0.2f),
                                shape = CircleShape
                            )
                    )
                }
            }
        }
    }
}
