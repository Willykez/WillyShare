package com.willyshare.willykez.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.willyshare.willykez.ui.theme.CyanBright
import com.willyshare.willykez.ui.theme.SleekBg
import com.willyshare.willykez.ui.theme.SleekOnSurfaceVariant
import com.willyshare.willykez.ui.theme.SleekPrimary
import com.willyshare.willykez.ui.theme.SleekPrimaryContainer
import com.willyshare.willykez.ui.theme.VioletAccent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SplashScreen(onFinish: () -> Unit) {
    val pulseAnim = remember { Animatable(1f) }
    val progressAnim = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        launch {
            pulseAnim.animateTo(
                targetValue = 1.15f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1100, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                )
            )
        }
        launch {
            progressAnim.animateTo(
                targetValue = 1f,
                animationSpec = tween(2200, easing = LinearEasing)
            )
        }
        delay(2300)
        onFinish()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SleekBg),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Emblem
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .scale(pulseAnim.value)
                    .clip(RoundedCornerShape(32.dp))
                    .background(SleekPrimaryContainer)
                    .border(2.dp, SleekPrimary.copy(alpha = 0.4f), RoundedCornerShape(32.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(com.willyshare.willykez.ui.PulseIcons.Brand, contentDescription = null, tint = SleekPrimary, modifier = Modifier.size(56.dp))
            }

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = "SPARKS",
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                color = SleekPrimary,
                letterSpacing = 8.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "File Sharing Redefined",
                fontSize = 14.sp,
                color = SleekOnSurfaceVariant
            )

            Spacer(modifier = Modifier.height(52.dp))

            // Progress track
            Box(
                modifier = Modifier
                    .width(180.dp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(SleekPrimary.copy(alpha = 0.15f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progressAnim.value)
                        .clip(RoundedCornerShape(999.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(VioletAccent, CyanBright)
                            )
                        )
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "INITIALIZING ROOM STORAGE...",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = SleekOnSurfaceVariant.copy(alpha = 0.7f),
                letterSpacing = 1.sp
            )
        }
    }
}
