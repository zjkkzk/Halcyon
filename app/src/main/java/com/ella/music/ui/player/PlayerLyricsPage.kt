package com.ella.music.ui.player

import android.graphics.Bitmap
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ella.music.data.model.LyricLine
import com.ella.music.data.model.Song
import com.ella.music.data.repository.MusicRepository
import com.ella.music.ui.components.SmoothLyricView
import kotlin.math.abs

@Composable
internal fun LyricsPlayerPage(
    song: Song?,
    embeddedCover: Bitmap?,
    annotation: String,
    lyrics: List<LyricLine>,
    currentLyricIndex: Int,
    currentPosition: Long,
    showTranslation: Boolean,
    showPronunciation: Boolean,
    keepScreenOn: Boolean,
    lyricFormatAvailability: MusicRepository.LyricFormatAvailability,
    preferTtmlLyrics: Boolean?,
    lyricSourceMode: Int,
    fontFamily: FontFamily?,
    fontPath: String,
    fontWeight: FontWeight,
    italic: Boolean,
    fontScale: Float,
    perspectiveEffect: Boolean,
    palette: PlayerPalette,
    flowEffectMode: Int,
    currentPositionMs: Long,
    isPlaying: Boolean,
    playerBackgroundEnabled: Boolean,
    playerBackgroundUri: String,
    isFavorite: Boolean,
    audioSessionId: Int,
    visualizerEnabled: Boolean,
    onLineClick: (LyricLine) -> Unit,
    onLineDoubleClick: () -> Unit,
    onLineLongClick: (LyricLine) -> Unit,
    onDismissLyrics: () -> Unit,
    onTogglePronunciation: () -> Unit,
    onToggleTranslation: () -> Unit,
    onToggleKeepScreenOn: () -> Unit,
    onToggleFavorite: () -> Unit,
    onFontScale: (Float) -> Unit,
    onLyricSourceMode: (Int) -> Unit,
    onLyricFormatPreference: (Boolean) -> Unit,
    onArtist: () -> Unit,
    enableSwipeDismiss: Boolean,
    useBlurBackground: Boolean,
    modifier: Modifier = Modifier
) {
    var lyricMenuExpanded by remember { mutableStateOf(false) }
    var dismissDragX by remember { mutableFloatStateOf(0f) }

    val lyricBackgroundMotion = 0.42f

    val swipeDismissModifier = if (enableSwipeDismiss) {
        Modifier.pointerInput(onDismissLyrics) {
            detectDragGestures(
                onDragStart = { dismissDragX = 0f },
                onDrag = { change, dragAmount ->
                    if (abs(dragAmount.x) > abs(dragAmount.y)) {
                        dismissDragX += dragAmount.x
                        change.consume()
                    }
                },
                onDragCancel = { dismissDragX = 0f },
                onDragEnd = {
                    if (dismissDragX > 96.dp.toPx()) {
                        onDismissLyrics()
                    }
                    dismissDragX = 0f
                }
            )
        }
    } else {
        Modifier
    }

    Box(modifier = modifier.then(swipeDismissModifier)) {
        val useCustomPlayerBackground = playerBackgroundEnabled && playerBackgroundUri.isNotBlank() && !useBlurBackground
        if (useCustomPlayerBackground) {
            PlayerCustomBackground(
                uri = playerBackgroundUri,
                modifier = Modifier.fillMaxSize()
            )
        } else if (useBlurBackground) {
            PlayerBlurBackground(
                song = song,
                embeddedCover = embeddedCover,
                palette = palette,
                motion = lyricBackgroundMotion,
                isPlaying = isPlaying,
                modifier = Modifier.fillMaxSize()
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 28.dp)
        ) {
            LyricsPlayerHeader(
                song = song,
                embeddedCover = embeddedCover,
                annotation = annotation,
                isFavorite = isFavorite,
                onDismissLyrics = onDismissLyrics,
                onArtist = onArtist,
                onToggleFavorite = onToggleFavorite,
                onShowMenu = { lyricMenuExpanded = true },
                modifier = Modifier.padding(top = 28.dp)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                SmoothLyricView(
                    songId = song?.id ?: 0L,
                    songTitle = song?.title.orEmpty(),
                    songArtist = song?.artist.orEmpty(),
                    lyrics = lyrics,
                    currentIndex = currentLyricIndex,
                    currentPositionMs = currentPositionMs,
                    isPlaying = isPlaying,
                    showTranslation = showTranslation,
                    showPronunciation = showPronunciation,
                    fontScale = fontScale,
                    fontPath = fontPath,
                    fontWeight = fontWeight,
                    italic = italic,
                    onLineClick = onLineClick,
                    onLineDoubleClick = onLineDoubleClick,
                    onLineLongClick = onLineLongClick,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        AudioVisualizer(
            enabled = visualizerEnabled,
            audioSessionId = audioSessionId,
            isPlaying = isPlaying,
            positionMs = currentPositionMs,
            accent = Color.White.copy(alpha = 0.86f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .fillMaxWidth()
                .height(42.dp)
        )

        LyricsPlayerMenuSheet(
            show = lyricMenuExpanded,
            showPronunciation = showPronunciation,
            showTranslation = showTranslation,
            keepScreenOn = keepScreenOn,
            lyricFormatAvailability = lyricFormatAvailability,
            preferTtmlLyrics = preferTtmlLyrics,
            lyricSourceMode = lyricSourceMode,
            fontScale = fontScale,
            onDismiss = { lyricMenuExpanded = false },
            onTogglePronunciation = {
                lyricMenuExpanded = false
                onTogglePronunciation()
            },
            onToggleTranslation = {
                lyricMenuExpanded = false
                onToggleTranslation()
            },
            onToggleKeepScreenOn = {
                lyricMenuExpanded = false
                onToggleKeepScreenOn()
            },
            onLyricSourceMode = { mode ->
                lyricMenuExpanded = false
                onLyricSourceMode(mode)
            },
            onLyricFormatPreference = { preferTtml ->
                lyricMenuExpanded = false
                onLyricFormatPreference(preferTtml)
            },
            onFontScale = onFontScale,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
