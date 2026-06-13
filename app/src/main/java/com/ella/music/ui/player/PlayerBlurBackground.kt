package com.ella.music.ui.player

import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.ella.music.data.SettingsManager
import com.ella.music.data.model.Song

@Composable
internal fun FluidLyricBackground(
    palette: PlayerPalette,
    positionMs: Long,
    isPlaying: Boolean,
    flowEffectMode: Int = SettingsManager.PLAYER_FLOW_EFFECT_DARK,
    animate: Boolean = false,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(animate) {
        Log.d("PlayerScreenPerf", "flow background ${if (animate) "animated" else "static"}")
    }
    val drift = if (animate) {
        val transition = rememberInfiniteTransition(label = "fluid_lyric_background")
        val value by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 18_000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "fluid_lyric_background_drift"
        )
        value
    } else {
        0.36f
    }
    val pulse = if (animate && isPlaying) {
        0.5f + 0.5f * kotlin.math.sin(positionMs / 900.0).toFloat()
    } else {
        0.28f
    }

    Canvas(modifier = modifier.background(palette.middle)) {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    palette.top.copy(alpha = 0.98f),
                    palette.middle.copy(alpha = 0.98f),
                    palette.bottom.copy(alpha = 1f)
                )
            )
        )
        val w = size.width
        val h = size.height
        val t = drift * kotlin.math.PI.toFloat() * 2f
        val centers = listOf(
            Offset((0.18f + 0.04f * kotlin.math.sin(t)) * w, (0.24f + 0.08f * kotlin.math.cos(t * 0.7f)) * h),
            Offset((0.82f + 0.05f * kotlin.math.cos(t * 0.8f)) * w, (0.20f + 0.06f * kotlin.math.sin(t)) * h),
            Offset((0.48f + 0.08f * kotlin.math.sin(t * 0.55f)) * w, (0.62f + 0.05f * kotlin.math.cos(t * 0.9f)) * h),
            Offset((0.72f + 0.06f * kotlin.math.sin(t * 0.95f)) * w, (0.86f + 0.04f * kotlin.math.cos(t * 0.6f)) * h)
        )
        val colors = listOf(
            palette.accent.copy(alpha = 0.22f + pulse * 0.05f),
            Color.White.copy(alpha = 0.10f),
            palette.top.copy(alpha = 0.20f),
            Color.Black.copy(alpha = 0.20f)
        )
        centers.forEachIndexed { index, center ->
            val radius = minOf(w, h) * (0.34f + index * 0.055f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(colors[index], Color.Transparent),
                    center = center,
                    radius = radius
                ),
                radius = radius,
                center = center
            )
        }
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.Black.copy(alpha = 0.10f),
                    Color.Transparent,
                    Color.Black.copy(alpha = 0.42f)
                )
            )
        )
    }
}

@Composable
internal fun BeautifulLyricsDynamicBackground(
    palette: PlayerPalette,
    positionMs: Long,
    isPlaying: Boolean,
    animate: Boolean = true,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "beautiful_lyrics_background")
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 12_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "beautiful_lyrics_background_drift"
    )
    val activeDrift = if (animate) drift else 0.42f
    val pulse = if (isPlaying) {
        0.5f + 0.5f * kotlin.math.sin(positionMs / 760.0).toFloat()
    } else {
        0.36f
    }
    val scrim = if (palette.isLight) Color.White else Color.Black

    Canvas(modifier = modifier.background(palette.middle)) {
        val w = size.width
        val h = size.height
        val t = activeDrift * kotlin.math.PI.toFloat() * 2f

        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    palette.top.copy(alpha = 0.96f),
                    palette.accent.copy(alpha = if (palette.isLight) 0.34f else 0.42f),
                    palette.bottom.copy(alpha = 0.98f)
                ),
                start = Offset(0f, 0f),
                end = Offset(w, h)
            )
        )

        val blobs = listOf(
            Triple(
                palette.accent.copy(alpha = 0.34f + pulse * 0.08f),
                Offset((0.20f + 0.18f * kotlin.math.sin(t)) * w, (0.20f + 0.14f * kotlin.math.cos(t)) * h),
                0.54f
            ),
            Triple(
                palette.top.copy(alpha = 0.30f),
                Offset((0.82f + 0.16f * kotlin.math.cos(t * 0.7f)) * w, (0.28f + 0.18f * kotlin.math.sin(t * 0.8f)) * h),
                0.46f
            ),
            Triple(
                Color.White.copy(alpha = if (palette.isLight) 0.22f else 0.13f),
                Offset((0.46f + 0.20f * kotlin.math.sin(t * 0.55f)) * w, (0.58f + 0.16f * kotlin.math.cos(t * 0.9f)) * h),
                0.40f
            ),
            Triple(
                palette.bottom.copy(alpha = 0.36f),
                Offset((0.70f + 0.18f * kotlin.math.cos(t * 0.95f)) * w, (0.84f + 0.10f * kotlin.math.sin(t)) * h),
                0.58f
            )
        )
        blobs.forEach { (color, center, radiusFactor) ->
            val radius = maxOf(w, h) * radiusFactor
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color, Color.Transparent),
                    center = center,
                    radius = radius
                ),
                radius = radius,
                center = center
            )
        }
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    scrim.copy(alpha = if (palette.isLight) 0.18f else 0.16f),
                    Color.Transparent,
                    scrim.copy(alpha = if (palette.isLight) 0.30f else 0.46f)
                )
            )
        )
    }
}

@Composable
internal fun PlayerBlurBackground(
    song: Song?,
    embeddedCover: Bitmap?,
    palette: PlayerPalette,
    motion: Float,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val uri = if ((song?.albumId ?: 0L) > 0) {
        Uri.parse("content://media/external/audio/albumart/${song?.albumId}")
    } else null
    val coverModel = embeddedCover ?: song?.coverUrl?.takeIf { it.isNotBlank() } ?: uri
    val movingScale = 2.90f
    val movingOffset = 0f
    LaunchedEffect(coverModel, isPlaying) {
        Log.d("PlayerScreenPerf", "blur background static")
    }

    // On a light player theme, wash the blurred cover toward white (dark lyrics on top) instead of
    // darkening it like the dark theme does.
    val isLight = palette.isLight
    val scrim = if (isLight) Color.White else Color.Black
    Box(modifier = modifier.background(palette.middle)) {
        if (coverModel != null) {
            PlayerCoverImage(
                model = coverModel,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = movingScale
                        scaleY = movingScale
                        translationX = movingOffset
                        translationY = -movingOffset * 0.65f
                        alpha = 0.78f
                    }
                    .blur(48.dp),
                contentScale = ContentScale.Crop,
                sizePx = 1200
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            palette.accent.copy(alpha = if (isLight) 0.18f else 0.28f),
                            palette.top.copy(alpha = if (isLight) 0.34f else 0.42f),
                            scrim.copy(alpha = 0.34f)
                        ),
                        start = Offset.Zero,
                        end = Offset.Infinite
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.06f),
                            Color.Transparent,
                            scrim.copy(alpha = 0.32f)
                        )
                    )
                )
        )
    }
}
