package com.ella.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun GlassPill(
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(32.dp),
    blurRadius: Float = 34f,
    content: @Composable BoxScope.() -> Unit
) {
    val isLight = MiuixTheme.colorScheme.background.luminance() > 0.5f
    val containerColor = if (isLight) {
        Color.White.copy(alpha = 0.56f)
    } else {
        Color(0xFF111114).copy(alpha = 0.58f)
    }

    val glassModifier = if (backdrop != null) {
        Modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                blur(blurRadius.dp.toPx())
            },
            highlight = {
                Highlight.Default.copy(alpha = if (isLight) 0.22f else 0.14f)
            },
            shadow = {
                Shadow.Default.copy(
                    color = Color.Black.copy(alpha = if (isLight) 0.12f else 0.30f)
                )
            },
            onDrawSurface = {
                drawRect(containerColor)
            }
        )
    } else {
        Modifier.background(containerColor, shape)
    }

    Box(
        modifier = modifier
            .clip(shape)
            .then(glassModifier),
        content = content
    )
}

private fun Color.luminance(): Float {
    return 0.2126f * red + 0.7152f * green + 0.0722f * blue
}
