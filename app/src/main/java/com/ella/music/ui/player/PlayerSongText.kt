package com.ella.music.ui.player

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.data.model.Song
import kotlinx.coroutines.isActive
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.ui.graphics.Color

@Composable
internal fun LandscapeSongTitle(
    song: Song?,
    annotation: String,
    modifier: Modifier = Modifier
) {
    PlayerSongMetaText(
        song = song,
        annotation = annotation,
        titleFontSize = 28.sp,
        artistFontSize = 16.sp,
        artistAlpha = 0.50f,
        fallbackTitle = stringResource(R.string.app_name),
        modifier = modifier.padding(end = 16.dp)
    )
}

@Composable
internal fun PlayerSongMetaText(
    song: Song?,
    annotation: String,
    titleFontSize: TextUnit,
    artistFontSize: TextUnit,
    artistAlpha: Float,
    modifier: Modifier = Modifier,
    fallbackTitle: String? = null,
    showArtistWithAnnotation: Boolean = false,
    contentColor: Color = Color.White,
    onArtistClick: (() -> Unit)? = null,
    onAlbumClick: (() -> Unit)? = null
) {
    val artist = song?.artist.orEmpty()
    fun clickableMetaModifier(enabled: Boolean, onClick: (() -> Unit)?): Modifier {
        return if (enabled && onClick != null) {
            Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onClick)
        } else {
            Modifier
        }
    }
    Column(modifier = modifier) {
        PlayerSongTitleText(
            text = song?.title?.takeIf { it.isNotBlank() }
                ?: fallbackTitle?.takeIf { it.isNotBlank() }
                ?: stringResource(R.string.player_not_playing),
            fontSize = titleFontSize,
            fontWeight = FontWeight.ExtraBold,
            color = contentColor.copy(alpha = 0.96f),
            modifier = Modifier.fillMaxWidth()
        )
        if (annotation.isNotBlank()) {
            PlayerMarqueeText(
                text = annotation,
                fontSize = (artistFontSize.value * 0.82f).sp,
                fontWeight = FontWeight.Bold,
                color = contentColor.copy(alpha = (artistAlpha + 0.16f).coerceAtMost(0.82f)),
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (annotation.isBlank() || showArtistWithAnnotation) {
            PlayerMarqueeText(
                text = artist,
                fontSize = artistFontSize,
                fontWeight = FontWeight.Bold,
                color = contentColor.copy(alpha = artistAlpha),
                modifier = Modifier
                    .fillMaxWidth()
                    .then(clickableMetaModifier(artist.isNotBlank(), onArtistClick))
            )
        }
    }
}

@Composable
internal fun PlayerSongTitleText(
    text: String,
    fontSize: TextUnit,
    fontWeight: FontWeight,
    color: Color,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        fontSize = fontSize,
        fontWeight = fontWeight,
        color = color,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
        modifier = modifier.basicMarquee(iterations = Int.MAX_VALUE)
    )
}

@Composable
internal fun PlayerMarqueeText(
    text: String,
    fontSize: TextUnit,
    fontWeight: FontWeight,
    color: Color,
    modifier: Modifier = Modifier
) {
    LyriconStyleMarqueeText(
        text = AnnotatedString(text),
        style = TextStyle(
            fontSize = fontSize,
            fontWeight = fontWeight,
            color = color
        ),
        enabled = true,
        textAlign = TextAlign.Start,
        modifier = modifier
    )
}

@Composable
internal fun LyriconStyleMarqueeText(
    text: AnnotatedString,
    style: TextStyle,
    enabled: Boolean,
    textAlign: TextAlign,
    modifier: Modifier = Modifier,
    ghostSpacing: androidx.compose.ui.unit.Dp = 70.dp,
    speedDpPerSecond: Float = 40f,
    initialDelayMs: Long = 300L,
    loopDelayMs: Long = 700L
) {
    val density = LocalDensity.current
    val spacingPx = with(density) { ghostSpacing.toPx() }
    val speedPxPerMs = with(density) { speedDpPerSecond.dp.toPx() } / 1000f
    var elapsedMs by remember(text, enabled) { mutableFloatStateOf(0f) }

    LaunchedEffect(text, enabled) {
        elapsedMs = 0f
        if (!enabled) return@LaunchedEffect
        var lastFrameNanos = 0L
        while (isActive) {
            withFrameNanos { frameNanos ->
                if (lastFrameNanos != 0L) {
                    elapsedMs += (frameNanos - lastFrameNanos) / 1_000_000f
                }
                lastFrameNanos = frameNanos
            }
        }
    }

    Layout(
        content = {
            BasicText(
                text = text,
                style = style.copy(textAlign = TextAlign.Start),
                maxLines = 1,
                overflow = TextOverflow.Visible
            )
            BasicText(
                text = text,
                style = style.copy(textAlign = TextAlign.Start),
                maxLines = 1,
                overflow = TextOverflow.Visible
            )
        },
        modifier = modifier
            .fillMaxWidth()
            .clipToBounds()
    ) { measurables, constraints ->
        val textConstraints = constraints.copy(minWidth = 0, maxWidth = Constraints.Infinity)
        val primary = measurables[0].measure(textConstraints)
        val ghost = measurables[1].measure(textConstraints)
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else primary.width
        val height = primary.height
        val overflowPx = (primary.width - width).coerceAtLeast(0)
        val shouldScroll = enabled && overflowPx > 0
        val x = if (shouldScroll) {
            -lyriconMarqueeOffsetPx(
                elapsedMs = elapsedMs,
                textWidthPx = primary.width.toFloat(),
                spacingPx = spacingPx,
                speedPxPerMs = speedPxPerMs,
                initialDelayMs = initialDelayMs,
                loopDelayMs = loopDelayMs
            )
        } else {
            when (textAlign) {
                TextAlign.Center -> ((width - primary.width) / 2).coerceAtLeast(0).toFloat()
                TextAlign.End, TextAlign.Right -> (width - primary.width).coerceAtLeast(0).toFloat()
                else -> 0f
            }
        }
        val unit = primary.width + spacingPx

        layout(width, height) {
            primary.placeRelativeWithLayer(0, 0) {
                translationX = x
            }
            if (shouldScroll) {
                ghost.placeRelativeWithLayer(0, 0) {
                    translationX = x + unit
                }
            }
        }
    }
}

internal fun lyriconMarqueeOffsetPx(
    elapsedMs: Float,
    textWidthPx: Float,
    spacingPx: Float,
    speedPxPerMs: Float,
    initialDelayMs: Long,
    loopDelayMs: Long
): Float {
    val activeMs = (elapsedMs - initialDelayMs).coerceAtLeast(0f)
    if (activeMs <= 0f) return 0f
    val unit = textWidthPx + spacingPx
    val travelMs = unit / speedPxPerMs.coerceAtLeast(0.001f)
    val cycleMs = travelMs + loopDelayMs
    val cycleTime = activeMs % cycleMs
    if (cycleTime >= travelMs) return 0f
    return (cycleTime * speedPxPerMs).coerceIn(0f, unit)
}
