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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
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
    paletteBitmap: Bitmap?,
    annotation: String,
    lyrics: List<LyricLine>,
    lyricsLoading: Boolean,
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
    secondaryFontScale: Float,
    perspectiveEffect: Boolean,
    perspectiveYAngle: Int,
    lyricTextAlign: Int,
    palette: PlayerPalette,
    flowEffectMode: Int,
    currentPositionMs: Long,
    isPlaying: Boolean,
    playerBackgroundEnabled: Boolean,
    playerBackgroundUri: String,
    playerBackgroundOpacity: Float,
    playerBackgroundDim: Float,
    beautifulLyricsBackground: Boolean,
    isFavorite: Boolean,
    audioSessionId: Int,
    visualizerEnabled: Boolean,
    visualizerOpacity: Float,
    onLineClick: (LyricLine) -> Unit,
    onLineDoubleClick: () -> Unit,
    onLineLongClick: (LyricLine) -> Unit,
    onDismissLyrics: () -> Unit,
    onTogglePronunciation: () -> Unit,
    onToggleTranslation: () -> Unit,
    onToggleKeepScreenOn: () -> Unit,
    onTogglePerspectiveEffect: () -> Unit,
    onPerspectiveYAngle: (Int) -> Unit,
    onToggleFavorite: () -> Unit,
    onFontScale: (Float) -> Unit,
    onSecondaryFontScale: (Float) -> Unit,
    onLyricSourceMode: (Int) -> Unit,
    onLyricFormatPreference: (Boolean) -> Unit,
    onArtist: () -> Unit,
    enableSwipeDismiss: Boolean,
    useBlurBackground: Boolean,
    drawBackground: Boolean = true,
    modifier: Modifier = Modifier
) {
    var lyricMenuExpanded by remember { mutableStateOf(false) }
    var dismissDragX by remember { mutableFloatStateOf(0f) }
    val activeAgentLabel = remember(lyrics, currentPosition) {
        lyrics.activeTtmlAgentLabel(currentPosition)
    }
    val headerAnnotation = remember(activeAgentLabel, annotation) {
        listOfNotNull(
            activeAgentLabel?.takeIf { it.isNotBlank() },
            annotation.takeIf { it.isNotBlank() }
        ).distinct().joinToString(" · ")
    }

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
        val density = LocalDensity.current
        if (drawBackground) {
            SharedPlayerPageBackground(
                song = song,
                embeddedCover = embeddedCover,
                paletteBitmap = paletteBitmap,
                palette = palette,
                currentPositionMs = currentPositionMs,
                isPlaying = isPlaying,
                playerBackgroundEnabled = playerBackgroundEnabled,
                playerBackgroundUri = playerBackgroundUri,
                playerBackgroundOpacity = playerBackgroundOpacity,
                playerBackgroundDim = playerBackgroundDim,
                beautifulLyricsBackground = beautifulLyricsBackground,
                useBlurBackground = useBlurBackground,
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
                annotation = headerAnnotation,
                isFavorite = isFavorite,
                onDismissLyrics = onDismissLyrics,
                onArtist = onArtist,
                onToggleFavorite = onToggleFavorite,
                onShowMenu = { lyricMenuExpanded = true },
                fontFamily = fontFamily,
                modifier = Modifier.padding(top = 28.dp)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .then(
                        if (perspectiveEffect && perspectiveYAngle > 0) {
                            Modifier.graphicsLayer {
                                rotationY = perspectiveYAngle.toFloat()
                                cameraDistance = density.density * 12f
                            }
                        } else Modifier
                    )
            ) {
                if (!lyricsLoading) {
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
                        lyricTextAlign = lyricTextAlign,
                        secondaryFontScale = secondaryFontScale,
                        contentColor = palette.onBackground,
                        // Keep far lines sharp over a busy custom wallpaper so they stay readable.
                        nonCurrentLineBlurEnabled = !useCustomPlayerBackground,
                        onLineClick = onLineClick,
                        onLineDoubleClick = onLineDoubleClick,
                        onLineLongClick = onLineLongClick,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        AudioVisualizer(
            enabled = visualizerEnabled,
            audioSessionId = audioSessionId,
            isPlaying = isPlaying,
            positionMs = currentPositionMs,
            opacity = visualizerOpacity,
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
            perspectiveEffect = perspectiveEffect,
            perspectiveYAngle = perspectiveYAngle,
            lyricFormatAvailability = lyricFormatAvailability,
            preferTtmlLyrics = preferTtmlLyrics,
            lyricSourceMode = lyricSourceMode,
            fontScale = fontScale,
            secondaryFontScale = secondaryFontScale,
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
            onTogglePerspectiveEffect = {
                onTogglePerspectiveEffect()
            },
            onPerspectiveYAngle = onPerspectiveYAngle,
            onLyricSourceMode = { mode ->
                lyricMenuExpanded = false
                onLyricSourceMode(mode)
            },
            onLyricFormatPreference = { preferTtml ->
                lyricMenuExpanded = false
                onLyricFormatPreference(preferTtml)
            },
            onFontScale = onFontScale,
            onSecondaryFontScale = onSecondaryFontScale,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private fun List<LyricLine>.activeTtmlAgentLabel(positionMs: Long): String? {
    val names = asSequence()
        .filter { line ->
            line.isTtml &&
                !line.agentName.isNullOrBlank() &&
                positionMs >= line.timeMs &&
                positionMs < (line.endMs ?: (line.timeMs + 3_500L))
        }
        .mapNotNull { it.agentName?.trim()?.takeIf(String::isNotBlank) }
        .distinct()
        .take(3)
        .toList()
    if (names.isEmpty()) return null
    return "\uD83C\uDFA4 ${names.joinToString("/")}"
}
