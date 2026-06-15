package com.ella.music.ui.player

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.data.model.AudioInfo
import com.ella.music.data.model.LyricLine
import com.ella.music.data.model.Song
import com.ella.music.ui.components.SmoothLyricView
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon

@Composable
internal fun LandscapeCoverPlayerPage(
    song: Song?,
    embeddedCover: Bitmap?,
    paletteBitmap: Bitmap?,
    annotation: String,
    dynamicCoverSource: DynamicCoverSource?,
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    shuffleEnabled: Boolean,
    repeatMode: Int,
    audioInfo: AudioInfo?,
    palette: PlayerPalette,
    lyrics: List<LyricLine>,
    currentLyricIndex: Int,
    showTranslation: Boolean,
    showPronunciation: Boolean,
    fontFamily: FontFamily?,
    fontPath: String,
    fontWeight: FontWeight,
    fontScale: Float,
    lyricTextAlign: Int,
    showTotalDuration: Boolean,
    playerTapSeekEnabled: Boolean,
    coverSwipeEnabled: Boolean,
    queueExpanded: Boolean,
    playlist: List<Song>,
    audioSessionId: Int,
    visualizerEnabled: Boolean,
    visualizerOpacity: Float,
    flowEffectMode: Int,
    dynamicFlowEnabled: Boolean,
    customBackgroundUri: String,
    customBackgroundOpacity: Float,
    customBackgroundDim: Float,
    beautifulLyricsBackground: Boolean,
    onDynamicCoverFailed: (String) -> Unit,
    isFavorite: Boolean,
    onToggleMenu: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleQueue: () -> Unit,
    onDismissQueue: () -> Unit,
    onShowLyrics: () -> Unit,
    onLyricLineClick: (LyricLine) -> Unit,
    onLyricLineLongClick: (LyricLine) -> Unit,
    onSeek: (Float) -> Unit,
    onCyclePlaybackMode: () -> Unit,
    onPrevious: () -> Unit,
    onSwipePrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onQueueSongClick: (Int) -> Unit,
    onRemoveQueueSong: (Int) -> Unit,
    onMoveQueueSong: (Int, Int) -> Unit,
    onAddQueueToPlaylist: () -> Unit,
    onClearQueue: () -> Unit,
    onLineClick: () -> Unit,
    onArtist: () -> Unit,
    onDismiss: () -> Unit = {},
    drawBackground: Boolean = true,
    modifier: Modifier = Modifier
) {
    val bluetoothDeviceName = rememberBluetoothOutputName()
    val hasLyrics = lyrics.isNotEmpty()
    val swipeThresholdPx = with(LocalDensity.current) { 84.dp.toPx() }
    val swipeScope = rememberCoroutineScope()
    val dragOffset = remember { androidx.compose.animation.core.Animatable(0f) }

    Box(modifier = modifier.then(if (drawBackground) Modifier.background(palette.middle) else Modifier)) {
        if (drawBackground) {
            LandscapeCoverModeBackground(
                palette = palette,
                embeddedCover = embeddedCover,
                paletteBitmap = paletteBitmap,
                currentPosition = currentPosition,
                isPlaying = isPlaying,
                flowEffectMode = flowEffectMode,
                dynamicFlowEnabled = dynamicFlowEnabled,
                visualizerEnabled = visualizerEnabled,
                visualizerOpacity = visualizerOpacity,
                customBackgroundUri = customBackgroundUri,
                customBackgroundOpacity = customBackgroundOpacity,
                customBackgroundDim = customBackgroundDim,
                beautifulLyricsBackground = beautifulLyricsBackground,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colorStops = arrayOf(
                                0.00f to Color.Black.copy(alpha = 0.04f),
                                0.34f to Color.Transparent,
                                0.50f to palette.middle.copy(alpha = 0.08f),
                                0.66f to Color.Transparent,
                                1.00f to Color.Black.copy(alpha = 0.05f)
                            )
                        )
                    )
            )
        }
        Row(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 32.dp, vertical = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (hasLyrics) Arrangement.Start else Arrangement.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .then(if (hasLyrics) Modifier.weight(0.38f) else Modifier.fillMaxWidth(0.46f)),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (hasLyrics) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PlayerSongMetaText(
                            song = song,
                            annotation = annotation,
                            titleFontSize = 20.sp,
                            artistFontSize = 12.sp,
                            artistAlpha = 0.56f,
                            showArtistWithAnnotation = true,
                            contentColor = palette.onBackground,
                            onArtistClick = onArtist,
                            modifier = Modifier.weight(1f)
                        )
                        PlayerHeaderAction(
                            kind = PlayerHeaderActionKind.Favorite,
                            selected = isFavorite,
                            onClick = onToggleFavorite
                        )
                        PlayerHeaderAction(kind = PlayerHeaderActionKind.More, onClick = onToggleMenu)
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PlayerSongMetaText(
                            song = song,
                            annotation = annotation,
                            titleFontSize = 24.sp,
                            artistFontSize = 16.sp,
                            artistAlpha = 0.62f,
                            showArtistWithAnnotation = true,
                            contentColor = palette.onBackground,
                            onArtistClick = onArtist,
                            modifier = Modifier.weight(1f)
                        )
                        PlayerHeaderAction(
                            kind = PlayerHeaderActionKind.Favorite,
                            selected = isFavorite,
                            onClick = onToggleFavorite
                        )
                        PlayerHeaderAction(kind = PlayerHeaderActionKind.More, onClick = onToggleMenu)
                    }
                }
                Spacer(modifier = Modifier.height(22.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(if (hasLyrics) 0.88f else 0.78f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .then(
                                if (coverSwipeEnabled) {
                                    Modifier.pointerInput(song?.id, onSwipePrevious, onNext) {
                                        detectHorizontalDragGestures(
                                            onDragCancel = {
                                                swipeScope.launch { dragOffset.animateTo(0f) }
                                            },
                                            onDragEnd = {
                                                val travel = dragOffset.value
                                                swipeScope.launch { dragOffset.animateTo(0f) }
                                                when {
                                                    travel > swipeThresholdPx -> onSwipePrevious()
                                                    travel < -swipeThresholdPx -> onNext()
                                                }
                                            },
                                            onHorizontalDrag = { change, dragAmount ->
                                                change.consume()
                                                swipeScope.launch {
                                                    dragOffset.snapTo(dragOffset.value + dragAmount)
                                                }
                                            }
                                        )
                                    }
                                } else {
                                    Modifier
                                }
                            )
                            .graphicsLayer {
                                translationX = dragOffset.value * 0.35f
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (dynamicCoverSource != null) {
                            DynamicCoverVideo(
                                source = dynamicCoverSource,
                                isPlaying = isPlaying,
                                onPlaybackError = { onDynamicCoverFailed(dynamicCoverSource.failureKey) },
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            AlbumArtView(
                                song = song,
                                embeddedCover = embeddedCover,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                PlayerProgressBlock(
                    currentPosition = currentPosition,
                    duration = duration,
                    audioInfo = audioInfo,
                    bluetoothDeviceName = bluetoothDeviceName,
                    palette = palette,
                    allowTapSeek = playerTapSeekEnabled,
                    showTotalDuration = showTotalDuration,
                    onSeek = onSeek
                )
                Spacer(modifier = Modifier.height(4.dp))
                PlayerTransportControls(
                    isPlaying = isPlaying,
                    shuffleEnabled = shuffleEnabled,
                    repeatMode = repeatMode,
                    palette = palette,
                    queueExpanded = queueExpanded,
                    playlist = playlist,
                    currentSongId = song?.id,
                    onCyclePlaybackMode = onCyclePlaybackMode,
                    onPrevious = onPrevious,
                    onPlayPause = onPlayPause,
                    onNext = onNext,
                    onToggleQueue = onToggleQueue,
                    onDismissQueue = onDismissQueue,
                    onQueueSongClick = onQueueSongClick,
                    onRemoveQueueSong = onRemoveQueueSong,
                    onMoveQueueSong = onMoveQueueSong,
                    onAddQueueToPlaylist = onAddQueueToPlaylist,
                    onClearQueue = onClearQueue
                )
            }
            if (hasLyrics) {
                Spacer(modifier = Modifier.width(48.dp))
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(0.62f)
                        .padding(start = 0.dp)
                ) {
                    SmoothLyricView(
                        songId = song?.id ?: 0L,
                        songTitle = song?.title.orEmpty(),
                        songArtist = song?.artist.orEmpty(),
                        lyrics = lyrics,
                        currentIndex = currentLyricIndex,
                        currentPositionMs = currentPosition,
                        isPlaying = isPlaying,
                        showTranslation = showTranslation,
                        showPronunciation = showPronunciation,
                        fontScale = fontScale,
                        fontPath = fontPath,
                        fontWeight = fontWeight,
                        lyricTextAlign = lyricTextAlign,
                        primaryTextSizeSp = 28f,
                        secondaryTextSizeSp = 14f,
                        anchorOffsetRatio = -0.08f,
                        topContentPadding = 8.dp,
                        contentColor = palette.onBackground,
                        // A custom wallpaper is a busy background; blurring far lines makes them
                        // unreadable, so keep all lines sharp when one is set.
                        nonCurrentLineBlurEnabled = customBackgroundUri.isBlank(),
                        onLineClick = onLyricLineClick,
                        onLineLongClick = onLyricLineLongClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight()
                    )
                }
            }
        }
        AudioVisualizer(
            enabled = visualizerEnabled,
            audioSessionId = audioSessionId,
            isPlaying = isPlaying,
            positionMs = currentPosition,
            opacity = visualizerOpacity,
            accent = Color.White.copy(alpha = 0.72f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .fillMaxWidth()
                .height(68.dp)
        )
    }
}
