package com.ella.music.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ella.music.data.model.LyricLine
import com.ella.music.ui.components.SmoothLyricView

internal fun miniLyricsPreviewHeight(
    line: LyricLine?,
    showTranslation: Boolean,
    showPronunciation: Boolean,
    compact: Boolean = false
) = when (line?.miniVisiblePartCount(showTranslation, showPronunciation) ?: 1) {
    // Single-line (e.g. Chinese only): keep a tall area and let the tighter line gap fit 5 lines.
    0, 1 -> if (compact) 150.dp else 186.dp
    2 -> if (compact) 150.dp else 190.dp
    3 -> if (compact) 162.dp else 206.dp
    else -> if (compact) 170.dp else 214.dp
}

/**
 * Height for the lyric preview in a cramped floating window: just enough for the current line
 * (plus its translation/pronunciation), so the transport controls below stay on-screen.
 */
internal fun miniLyricsCompactHeight(
    line: LyricLine?,
    showTranslation: Boolean,
    showPronunciation: Boolean
) = when (line?.miniVisiblePartCount(showTranslation, showPronunciation) ?: 1) {
    0, 1 -> 40.dp
    2 -> 60.dp
    else -> 78.dp
}

@Composable
internal fun MiniLyricsPreview(
    songId: Long,
    songTitle: String,
    songArtist: String,
    lyrics: List<LyricLine>,
    currentIndex: Int,
    showTranslation: Boolean,
    showPronunciation: Boolean,
    currentPositionMs: Long,
    isPlaying: Boolean,
    fontPath: String = "",
    fontWeight: FontWeight = FontWeight.ExtraBold,
    fontScale: Float = 1f,
    compact: Boolean = false,
    onLineClick: (LyricLine) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val safeIndex = currentIndex.takeIf { it in lyrics.indices }
        ?: lyrics.indexOfFirst { it.hasMiniLyric() }.takeIf { it >= 0 }
        ?: return
    // When only the main line shows (e.g. Chinese with no translation/pronunciation), tighten the
    // line gap so the preview fits ~5 lines instead of ~4.
    val singleLinePreview = compact || (lyrics.getOrNull(safeIndex)
        ?.miniVisiblePartCount(showTranslation, showPronunciation) ?: 1) <= 1
    // In a cramped floating window, shrink the type so long (e.g. English) lines fit the narrow
    // width instead of overflowing, and take less vertical room.
    val primarySizeSp = if (compact) 15.5f else 19f
    val secondarySizeSp = if (compact) 11.5f else 13.5f
    SmoothLyricView(
        songId = songId,
        songTitle = songTitle,
        songArtist = songArtist,
        lyrics = lyrics,
        currentIndex = safeIndex,
        currentPositionMs = currentPositionMs,
        isPlaying = isPlaying,
        showTranslation = showTranslation,
        showPronunciation = showPronunciation,
        fontScale = fontScale * 0.92f,
        fontPath = fontPath,
        fontWeight = fontWeight,
        primaryTextSizeSp = primarySizeSp,
        secondaryTextSizeSp = secondarySizeSp,
        anchorOffsetRatio = -0.01f,
        topContentPadding = 0.dp,
        onLineClick = onLineClick,
        nonCurrentLineBlurEnabled = false,
        nonCurrentLineBlurDistance = Int.MAX_VALUE,
        autoScrollResumeEnabled = true,
        lineGapDp = if (singleLinePreview) 4f else null,
        modifier = modifier.fillMaxWidth()
    )
}

internal fun LyricLine.hasMiniLyric(): Boolean {
    return !pronunciation.isNullOrBlank() ||
        text.takeIf { it.isNotBlank() && !it.isMusicSymbolOnly() } != null ||
        !translation.isNullOrBlank() ||
        backgroundText?.takeIf { it.isNotBlank() && !it.isMusicSymbolOnly() } != null ||
        !backgroundTranslation.isNullOrBlank()
}

internal fun LyricLine.miniVisiblePartCount(
    showTranslation: Boolean,
    showPronunciation: Boolean
): Int {
    var count = 0
    if (showPronunciation && !pronunciation.isNullOrBlank()) count++
    if (text.isNotBlank() && !text.isMusicSymbolOnly()) count++
    if (showTranslation && !translation.isNullOrBlank()) count++
    if (!backgroundText.isNullOrBlank() && !backgroundText.isMusicSymbolOnly()) count++
    if (showTranslation && !backgroundTranslation.isNullOrBlank()) count++
    return count
}

internal fun String.isMusicSymbolOnly(): Boolean {
    val cleaned = trim()
    if (cleaned.isEmpty()) return true
    return cleaned.all { char ->
        char.isWhitespace() ||
            char in setOf('♪', '♫', '♬', '♩', '♭', '♮', '♯', '☆', '★', '·', '.', '。', '…')
    }
}
