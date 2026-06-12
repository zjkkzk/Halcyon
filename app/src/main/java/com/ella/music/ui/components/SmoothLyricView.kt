package com.ella.music.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.ella.music.R
import com.ella.music.data.model.LyricLine
import io.github.proify.lyricon.lyric.view.RawsLyricView
import top.yukonga.miuix.kmp.basic.Text
import kotlin.math.abs

@Composable
fun SmoothLyricView(
    songId: Long,
    songTitle: String,
    songArtist: String,
    lyrics: List<LyricLine>,
    currentIndex: Int,
    currentPositionMs: Long,
    isPlaying: Boolean,
    showTranslation: Boolean,
    showPronunciation: Boolean = true,
    modifier: Modifier = Modifier,
    fontScale: Float = 1f,
    fontPath: String = "",
    fontWeight: FontWeight = FontWeight.ExtraBold,
    italic: Boolean = false,
    contentColor: Color = Color.White,
    primaryTextSizeSp: Float = 28f,
    secondaryTextSizeSp: Float = 15f,
    anchorOffsetRatio: Float = -0.12f,
    topContentPadding: Dp = 0.dp,
    nonCurrentLineBlurDistance: Int = 2,
    nonCurrentLineBlurEnabled: Boolean = true,
    autoScrollResumeEnabled: Boolean = true,
    lineGapDp: Float? = null,
    onLineClick: (LyricLine) -> Unit = {},
    onLineDoubleClick: () -> Unit = {},
    onLineLongClick: (LyricLine) -> Unit = {}
) {
    if (lyrics.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.no_lyrics),
                fontSize = 16.sp,
                color = contentColor.copy(alpha = 0.72f)
            )
        }
        return
    }

    val density = LocalDensity.current
    val lyriconSong = remember(songId, songTitle, songArtist, lyrics) {
        lyrics.toLyriconSong(songId, songTitle, songArtist)
    }
    val lyricTypeface = remember(fontPath, fontWeight, italic) {
        loadAndroidTypeface(fontPath, fontWeight.weight, italic = italic, boldFallback = true)
    }
    val secondaryTypeface = remember(fontPath, fontWeight, italic) {
        loadAndroidTypeface(
            fontPath = fontPath,
            weight = (fontWeight.weight - 200).coerceIn(100, 900),
            italic = italic,
            boldFallback = false
        )
    }
    val contentArgb = contentColor.toArgb()
    val style = remember(
        fontScale,
        density.fontScale,
        lyricTypeface,
        secondaryTypeface,
        primaryTextSizeSp,
        secondaryTextSizeSp,
        contentArgb
    ) {
        buildLyriconRichLineConfig(
            primaryTextSizePx = with(density) { (primaryTextSizeSp.sp * fontScale).toPx() },
            secondaryTextSizePx = with(density) { (secondaryTextSizeSp.sp * fontScale).toPx() },
            primaryTypeface = lyricTypeface,
            secondaryTypeface = secondaryTypeface,
            primaryTextColor = contentArgb,
            secondaryTextColor = contentColor.copy(alpha = 0.745f).toArgb(),
            syllableBackgroundColor = contentColor.copy(alpha = 0.345f).toArgb()
        )
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            RawsLyricView(context).apply {
                layoutParams = android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                )
                setPadding(0, 0, 0, 0)
            }
        },
        update = { view ->
            if (view.tag !== lyriconSong) {
                view.song = lyriconSong
                view.tag = lyriconSong
            }
            view.setStyle(style)
            view.setNonCurrentLineBlurEnabled(nonCurrentLineBlurEnabled)
            view.setNonCurrentLineBlurDistance(nonCurrentLineBlurDistance)
            view.setEdgeFadeEnabled(false)
            view.setLineAlphaAnimationsEnabled(false)
            view.setContinuousFrameUpdatesEnabled(true)
            view.setPlaybackActive(isPlaying)
            view.setPronunciationAboveMainEnabled(true)
            view.setAutoScrollResumeEnabled(autoScrollResumeEnabled)
            view.setLineGapDp(lineGapDp ?: -1f)
            view.updateAnchorOffset(view.height * anchorOffsetRatio)
            view.setTopContentPadding(with(density) { topContentPadding.toPx() })
            view.updateDisplayTranslation(showTranslation, showPronunciation)
            view.onLineClickListener = object : RawsLyricView.OnLineClickListener {
                override fun onLineClick(beginMs: Long) {
                    val line = lyrics.minByOrNull { abs(it.timeMs - beginMs) }
                    if (line != null) onLineClick(line)
                }
            }
            view.onLineDoubleClickListener = RawsLyricView.OnLineDoubleClickListener {
                onLineDoubleClick()
            }
            view.onLineLongClickListener = RawsLyricView.OnLineLongClickListener { beginMs ->
                val line = lyrics.minByOrNull { abs(it.timeMs - beginMs) }
                if (line != null) onLineLongClick(line)
            }
            view.setPosition(currentPositionMs)
        }
    )
}
