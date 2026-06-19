package com.ella.music.ui.player

import android.net.Uri
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.data.model.AudioInfo
import com.ella.music.data.model.LyricLine
import com.ella.music.data.model.Song
import com.ella.music.data.model.playlistIdentityKey
import com.ella.music.ui.components.DefaultAlbumCover
import com.ella.music.ui.components.SafeCoverImage
import com.ella.music.ui.components.SmoothLyricView
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

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
    secondaryFontScale: Float,
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
    val compactPhoneLandscape = LocalConfiguration.current.smallestScreenWidthDp < 600
    val swipeThresholdPx = with(LocalDensity.current) { 84.dp.toPx() }
    val swipeScope = rememberCoroutineScope()
    val dragOffset = remember { androidx.compose.animation.core.Animatable(0f) }

    if (compactPhoneLandscape) {
        CompactPhoneLandscapeCoverPlayerPage(
            song = song,
            embeddedCover = embeddedCover,
            paletteBitmap = paletteBitmap,
            annotation = annotation,
            dynamicCoverSource = dynamicCoverSource,
            isPlaying = isPlaying,
            currentPosition = currentPosition,
            duration = duration,
            audioInfo = audioInfo,
            palette = palette,
            lyrics = lyrics,
            currentLyricIndex = currentLyricIndex,
            showTranslation = showTranslation,
            showPronunciation = showPronunciation,
            fontFamily = fontFamily,
            fontPath = fontPath,
            fontWeight = fontWeight,
            fontScale = fontScale,
            secondaryFontScale = secondaryFontScale,
            lyricTextAlign = lyricTextAlign,
            showTotalDuration = showTotalDuration,
            playerTapSeekEnabled = playerTapSeekEnabled,
            coverSwipeEnabled = coverSwipeEnabled,
            audioSessionId = audioSessionId,
            visualizerEnabled = visualizerEnabled,
            visualizerOpacity = visualizerOpacity,
            flowEffectMode = flowEffectMode,
            dynamicFlowEnabled = dynamicFlowEnabled,
            customBackgroundUri = customBackgroundUri,
            customBackgroundOpacity = customBackgroundOpacity,
            customBackgroundDim = customBackgroundDim,
            beautifulLyricsBackground = beautifulLyricsBackground,
            onDynamicCoverFailed = onDynamicCoverFailed,
            isFavorite = isFavorite,
            onToggleMenu = onToggleMenu,
            onToggleFavorite = onToggleFavorite,
            onSeek = onSeek,
            onPrevious = onPrevious,
            onSwipePrevious = onSwipePrevious,
            onPlayPause = onPlayPause,
            onNext = onNext,
            onLyricLineClick = onLyricLineClick,
            onLyricLineLongClick = onLyricLineLongClick,
            onArtist = onArtist,
            drawBackground = drawBackground,
            modifier = modifier
        )
        return
    }

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
                            fontFamily = fontFamily,
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
                            fontFamily = fontFamily,
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
                    currentSongKey = song?.playlistIdentityKey(),
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
                        secondaryFontScale = secondaryFontScale,
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

@Composable
private fun CompactPhoneLandscapeCoverPlayerPage(
    song: Song?,
    embeddedCover: Bitmap?,
    paletteBitmap: Bitmap?,
    annotation: String,
    dynamicCoverSource: DynamicCoverSource?,
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
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
    secondaryFontScale: Float,
    lyricTextAlign: Int,
    showTotalDuration: Boolean,
    playerTapSeekEnabled: Boolean,
    coverSwipeEnabled: Boolean,
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
    onSeek: (Float) -> Unit,
    onPrevious: () -> Unit,
    onSwipePrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onLyricLineClick: (LyricLine) -> Unit,
    onLyricLineLongClick: (LyricLine) -> Unit,
    onArtist: () -> Unit,
    drawBackground: Boolean,
    modifier: Modifier = Modifier
) {
    val swipeThresholdPx = with(LocalDensity.current) { 84.dp.toPx() }
    val swipeScope = rememberCoroutineScope()
    val dragOffset = remember { androidx.compose.animation.core.Animatable(0f) }

    fun Modifier.coverSwipeModifier(): Modifier {
        return if (coverSwipeEnabled) {
            pointerInput(song?.id, onSwipePrevious, onNext) {
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
            this
        }
    }

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
                    .background(Color.Black.copy(alpha = 0.18f))
            )
        }

        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(0.50f)
                    .graphicsLayer { translationX = dragOffset.value * 0.32f }
                    .coverSwipeModifier(),
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
                    PhoneLandscapeCoverImage(
                        song = song,
                        embeddedCover = embeddedCover,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.horizontalGradient(
                                colorStops = arrayOf(
                                    0.00f to Color.Transparent,
                                    0.72f to Color.Transparent,
                                    1.00f to palette.middle.copy(alpha = 0.56f)
                                )
                            )
                        )
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(0.50f)
                    .background(
                        Brush.horizontalGradient(
                            colorStops = arrayOf(
                                0.00f to palette.middle.copy(alpha = 0.36f),
                                0.18f to palette.middle.copy(alpha = 0.64f),
                                1.00f to palette.middle.copy(alpha = 0.82f)
                            )
                        )
                    )
                    .padding(start = 18.dp, end = 26.dp)
            ) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(top = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    CompactLandscapeIconButton(onClick = onPrevious) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_skip_previous),
                            contentDescription = stringResource(R.string.common_previous),
                            tint = palette.onBackground.copy(alpha = 0.94f),
                            modifier = Modifier.size(27.dp)
                        )
                    }
                    CompactLandscapeIconButton(onClick = onPlayPause) {
                        CenteredPlayPauseGlyph(
                            isPlaying = isPlaying,
                            tint = palette.onBackground.copy(alpha = 0.96f),
                            modifier = Modifier.size(31.dp)
                        )
                    }
                    CompactLandscapeIconButton(onClick = onNext) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_skip_next),
                            contentDescription = stringResource(R.string.common_next),
                            tint = palette.onBackground.copy(alpha = 0.94f),
                            modifier = Modifier.size(27.dp)
                        )
                    }
                    PlayerHeaderAction(
                        kind = PlayerHeaderActionKind.Favorite,
                        selected = isFavorite,
                        onClick = onToggleFavorite
                    )
                    PlayerHeaderAction(kind = PlayerHeaderActionKind.More, onClick = onToggleMenu)
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(top = 58.dp, end = 218.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = song?.title?.takeIf { it.isNotBlank() } ?: stringResource(R.string.app_name),
                        color = palette.onBackground.copy(alpha = 0.96f),
                        fontSize = 22.sp,
                        lineHeight = 25.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = fontFamily,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = song?.artist?.takeIf { it.isNotBlank() } ?: stringResource(R.string.player_unknown_artist),
                        color = palette.onBackground.copy(alpha = 0.58f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = fontFamily,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp)
                    )
                    annotation.takeIf { it.isNotBlank() }?.let { text ->
                        Text(
                            text = text,
                            color = palette.onBackground.copy(alpha = 0.44f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = fontFamily,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 2.dp)
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(top = 106.dp, bottom = 62.dp)
                ) {
                    if (lyrics.isNotEmpty()) {
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
                            secondaryFontScale = secondaryFontScale,
                            fontPath = fontPath,
                            fontWeight = fontWeight,
                            lyricTextAlign = lyricTextAlign,
                            primaryTextSizeSp = 25f,
                            secondaryTextSizeSp = 13f,
                            anchorOffsetRatio = -0.08f,
                            topContentPadding = 0.dp,
                            contentColor = palette.onBackground,
                            nonCurrentLineBlurEnabled = customBackgroundUri.isBlank(),
                            onLineClick = onLyricLineClick,
                            onLineLongClick = onLyricLineLongClick,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.player_no_lyrics),
                            color = palette.onBackground.copy(alpha = 0.54f),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = fontFamily,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .height(58.dp)
                .background(Color.Black.copy(alpha = 0.20f))
                .padding(horizontal = 15.dp, vertical = 8.dp)
        ) {
            GlowSeekBar(
                value = if (duration > 0L) currentPosition.toFloat() / duration.toFloat() else 0f,
                onSeek = onSeek,
                accent = palette.accent,
                allowTapSeek = playerTapSeekEnabled,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(24.dp)
            )
            Text(
                text = "${formatTime(currentPosition)} / ${
                    if (showTotalDuration) formatTime(duration.coerceAtLeast(0L))
                    else "-${formatTime((duration - currentPosition).coerceAtLeast(0L))}"
                }",
                color = palette.onBackground.copy(alpha = 0.74f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier.align(Alignment.BottomEnd)
            )
        }
    }
}

@Composable
private fun CompactLandscapeIconButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .playerNoIndicationClick(onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
private fun PhoneLandscapeCoverImage(
    song: Song?,
    embeddedCover: Bitmap?,
    modifier: Modifier = Modifier
) {
    val coverModel = embeddedCover
        ?: song?.coverUrl?.takeIf { it.isNotBlank() }
        ?: song?.albumId
            ?.takeIf { it > 0L }
            ?.let { Uri.parse("content://media/external/audio/albumart/$it") }

    Box(
        modifier = modifier.background(MiuixTheme.colorScheme.surfaceContainer),
        contentAlignment = Alignment.Center
    ) {
        if (coverModel != null) {
            SafeCoverImage(
                model = coverModel,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                sizePx = 960,
                showDefaultPlaceholder = false
            )
        } else {
            DefaultAlbumCover(modifier = Modifier.fillMaxSize())
        }
    }
}
