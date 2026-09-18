package com.haio.bypass.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haio.bypass.R
import kotlinx.coroutines.launch

@Composable
fun SplashScreen(onSplashFinished: () -> Unit) {
    val alpha = remember { Animatable(0f) }
    val scale = remember { Animatable(0.85f) }
    val taglineAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        alpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 400, easing = EaseOutCubic)
        )
        launch {
            scale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        }
        kotlinx.coroutines.delay(300)
        launch {
            taglineAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 350, easing = EaseOutCubic)
            )
        }
        kotlinx.coroutines.delay(600)
        alpha.animateTo(
            targetValue = 0f,
            animationSpec = tween(durationMillis = 300, easing = EaseInCubic)
        )
        onSplashFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.alpha(alpha.value)
        ) {
            Image(
                painter = painterResource(id = R.drawable.haio_logo_new),
                contentDescription = "HaioBypass Logo",
                modifier = Modifier
                    .size(140.dp)
                    .scale(scale.value)
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "HaioBypass",
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "تحریم شکن هایو",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                    alpha = taglineAlpha.value
                ),
                modifier = Modifier.alpha(taglineAlpha.value)
            )
        }
    }
}