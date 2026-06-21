package com.ella.music.ui.player

import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import com.ella.music.data.SettingsManager

internal fun Modifier.playerLyricPerspective(
    enabled: Boolean,
    angle: Int,
    lyricTextAlign: Int,
    cameraDistanceMultiplier: Float = 18f
): Modifier = composed {
    if (!enabled || angle <= 0) return@composed this

    val density = LocalDensity.current
    val originX = when (lyricTextAlign) {
        SettingsManager.PLAYER_LYRIC_ALIGN_RIGHT -> 1f
        SettingsManager.PLAYER_LYRIC_ALIGN_CENTER -> 0.5f
        else -> 0f
    }
    val resolvedAngle = when (lyricTextAlign) {
        SettingsManager.PLAYER_LYRIC_ALIGN_RIGHT -> angle.toFloat()
        else -> -angle.toFloat()
    }

    graphicsLayer {
        rotationY = resolvedAngle
        transformOrigin = TransformOrigin(originX, 0.5f)
        cameraDistance = density.density * cameraDistanceMultiplier
    }
}
