package com.haio.bypass.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import com.haio.bypass.ui.theme.Accent
import com.haio.bypass.ui.theme.Background

@Composable
fun SplashScreen(onSplashFinished: () -> Unit) {
    val alpha = remember { Animatable(0f) }
    val scale = remember { Animatable(0.8f) }

    LaunchedEffect(Unit) {
        alpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 300)
        )
        scale.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 400)
        )
        kotlinx.coroutines.delay(800)
        alpha.animateTo(
            targetValue = 0f,
            animationSpec = tween(durationMillis = 250)
        )
        onSplashFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.alpha(alpha.value)
        ) {
            Image(
                painter = painterResource(id = R.drawable.haio_logo),
                contentDescription = "HaioBypass Logo",
                modifier = Modifier
                    .size(160.dp)
                    .scale(scale.value)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "HaioBypass",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Accent
            )

            
        }
    }
}