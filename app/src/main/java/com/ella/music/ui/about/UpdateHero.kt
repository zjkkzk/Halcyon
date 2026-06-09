package com.ella.music.ui.about

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Text

@Composable
internal fun UpdatePressureHero(
    state: UpdateUiState,
    isDark: Boolean,
    buttonText: String,
    buttonEnabled: Boolean,
    onButtonClick: () -> Unit
) {
    val foldProgress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        delay(80)
        foldProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 760,
                easing = CubicBezierEasing(0.18f, 0.82f, 0.22f, 1f)
            )
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(276.dp)
            .graphicsLayer {
                val progress = foldProgress.value
                cameraDistance = 34f * density
                transformOrigin = TransformOrigin(0.5f, 0f)
                rotationX = -58f * (1f - progress)
                rotationY = -4f * (1f - progress)
                translationY = -18.dp.toPx() * (1f - progress)
                scaleX = 0.96f + 0.04f * progress
                scaleY = 0.94f + 0.06f * progress
                alpha = 0.88f + 0.12f * progress
                shadowElevation = 18.dp.toPx()
            }
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.linearGradient(
                    colors = updateHeroColors(isDark)
                )
            )
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(x = 42.dp, y = (-18).dp)
                .size(168.dp)
                .background(Color.White.copy(alpha = 0.15f), CircleShape)
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 96.dp, bottom = 58.dp)
                .size(112.dp)
                .background(Color.White.copy(alpha = 0.10f), CircleShape)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(28.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.height(2.dp))

            Column {
                Text(
                    text = state.heroTitle(),
                    color = Color.White,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = state.heroSummary(),
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Button(
                    onClick = onButtonClick,
                    enabled = buttonEnabled,
                    modifier = Modifier.padding(top = 20.dp)
                ) {
                    Text(text = buttonText)
                }
            }
        }
    }
}

private fun updateHeroColors(isDark: Boolean): List<Color> =
    if (isDark) {
        listOf(
            Color(0xFF202231),
            Color(0xFF1B2B45),
            Color(0xFF28385E)
        )
    } else {
        listOf(
            Color(0xFF1A73FF),
            Color(0xFF6B8CFF),
            Color(0xFFFF7A94)
        )
    }
