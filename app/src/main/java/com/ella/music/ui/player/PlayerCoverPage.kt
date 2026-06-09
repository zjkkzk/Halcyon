package com.ella.music.ui.player

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.data.model.AudioInfo
import com.ella.music.data.model.LyricLine
import com.ella.music.data.model.Song
import com.ella.music.viewmodel.MainViewModel
import com.ella.music.viewmodel.PlayerViewModel

@Composable
internal fun CoverPlayerPage(
    context: Context,
    mainViewModel: MainViewModel,
    playerViewModel: PlayerViewModel,
    song: Song?,
    embeddedCover: Bitmap?,
    annotation: String,
    dynamicCoverFailedPath: String?,
    dynamicCoverEnabled: Boolean,
    immersiveAlbumCover: Boolean,
    playerBackgroundEnabled: Boolean,
    playerBackgroundUri: String,
    hiResLogoEnabled: Boolean,
    hiResLogoUri: String,
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    shuffleEnabled: Boolean,
    repeatMode: Int,
    audioInfo: AudioInfo?,
    palette: PlayerPalette,
    flowEffectMode: Int,
    dynamicFlowEnabled: Boolean,
    lyrics: List<LyricLine>,
    currentLyricIndex: Int,
    miniLyricLine: LyricLine?,
    showTranslation: Boolean,
    showPronunciation: Boolean,
    fontFamily: FontFamily?,
    fontPath: String,
    fontWeight: FontWeight,
    fontScale: Float,
    playerTapSeekEnabled: Boolean,
    playerShowTotalDuration: Boolean,
    menuExpanded: Boolean,
    queueExpanded: Boolean,
    playlist: List<Song>,
    sleepTimerEndRealtimeMs: Long?,
    stopAfterCurrentEnabled: Boolean,
    sleepTimerCustomMinutes: Int,
    sleepTimerStopAfterCurrent: Boolean,
    playbackSpeed: Float,
    playbackPitch: Float,
    isFavorite: Boolean,
    audioSessionId: Int,
    visualizerEnabled: Boolean,
    metadataEditorId: String,
    lyricTimingEditorId: String,
    onVisualizerEnabled: (Boolean) -> Unit,
    onDynamicCoverFailed: (String) -> Unit,
    onMatchDynamicCover: () -> Unit,
    onToggleMenu: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDismissMenu: () -> Unit,
    onToggleQueue: () -> Unit,
    onDismissQueue: () -> Unit,
    onShowLyrics: () -> Unit,
    onLyricLineClick: (LyricLine) -> Unit,
    onLyricLineLongClick: (LyricLine) -> Unit,
    onSeek: (Float) -> Unit,
    onCyclePlaybackMode: () -> Unit,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onQueueSongClick: (Int) -> Unit,
    onRemoveQueueSong: (Int) -> Unit,
    onMoveQueueSong: (Int, Int) -> Unit,
    onAddQueueToPlaylist: () -> Unit,
    onClearQueue: () -> Unit,
    onAlbum: () -> Unit,
    onArtist: () -> Unit,
    onNavigateToAlbumId: (Long) -> Unit,
    onNavigateToArtistName: (String) -> Unit,
    onDownload: () -> Unit,
    onLandscape: () -> Unit,
    onSongInfo: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onShareSong: () -> Unit,
    onAddToQueue: () -> Unit,
    onPlayNext: () -> Unit,
    onSetRating: () -> Unit,
    onAiInterpret: () -> Unit,
    onSpectrum: () -> Unit,
    onDeleteSong: () -> Unit,
    onOpenTimer: () -> Unit,
    onOpenMetadataEditor: () -> Unit,
    onStopAfterCurrent: (Boolean) -> Unit,
    onTimer: (Int) -> Unit,
    onCustomTimerMinutes: (Int) -> Unit,
    onCancelTimer: () -> Unit,
    onSpeed: (Float) -> Unit,
    onPitch: (Float) -> Unit,
    actionMenuInitialPage: PlayerActionSheetPage,
    modifier: Modifier = Modifier
) {
    val bluetoothDeviceName = rememberBluetoothOutputName()
    val dynamicCoverSource = if (dynamicCoverEnabled) {
        song
            ?.dynamicCoverSource(context)
            ?.takeUnless { it.failureKey == dynamicCoverFailedPath }
    } else {
        null
    }

    BoxWithConstraints(modifier = modifier) {
        val useWidePlayer = maxWidth > maxHeight && maxWidth >= 700.dp
        val isSmallWindow = maxWidth < 300.dp || (maxWidth < 420.dp && maxHeight < 560.dp)
        val effectiveMiniLyricLine = miniLyricLine.takeUnless { isSmallWindow }
        val showHiResLogo = hiResLogoEnabled && audioInfo?.isHiResLogoTrack() == true
        val showCustomPlayerBackground =
            playerBackgroundEnabled && playerBackgroundUri.isNotBlank() && (useWidePlayer || !immersiveAlbumCover)
        if (showCustomPlayerBackground && !useWidePlayer) {
            PlayerCustomBackground(
                uri = playerBackgroundUri,
                modifier = Modifier.fillMaxSize()
            )
        }
        if (useWidePlayer) {
            LandscapeCoverPlayerPage(
                song = song,
                embeddedCover = embeddedCover,
                annotation = annotation,
                dynamicCoverSource = dynamicCoverSource,
                isPlaying = isPlaying,
                currentPosition = currentPosition,
                duration = duration,
                shuffleEnabled = shuffleEnabled,
                repeatMode = repeatMode,
                audioInfo = audioInfo,
                palette = palette,
                flowEffectMode = flowEffectMode,
                dynamicFlowEnabled = dynamicFlowEnabled,
                customBackgroundUri = playerBackgroundUri.takeIf { showCustomPlayerBackground }.orEmpty(),
                lyrics = lyrics,
                currentLyricIndex = currentLyricIndex,
                showTranslation = showTranslation,
                showPronunciation = showPronunciation,
                fontFamily = fontFamily,
                fontPath = fontPath,
                fontWeight = fontWeight,
                fontScale = fontScale,
                showTotalDuration = playerShowTotalDuration,
                queueExpanded = queueExpanded,
                playlist = playlist,
                audioSessionId = audioSessionId,
                visualizerEnabled = visualizerEnabled,
                onDynamicCoverFailed = onDynamicCoverFailed,
                isFavorite = isFavorite,
                onToggleMenu = onToggleMenu,
                onToggleFavorite = onToggleFavorite,
                onToggleQueue = onToggleQueue,
                onDismissQueue = onDismissQueue,
                onShowLyrics = onShowLyrics,
                onLyricLineClick = onLyricLineClick,
                onLyricLineLongClick = onLyricLineLongClick,
                onSeek = onSeek,
                onCyclePlaybackMode = onCyclePlaybackMode,
                onPrevious = onPrevious,
                onPlayPause = onPlayPause,
                onNext = onNext,
                onQueueSongClick = onQueueSongClick,
                onRemoveQueueSong = onRemoveQueueSong,
                onMoveQueueSong = onMoveQueueSong,
                onAddQueueToPlaylist = onAddQueueToPlaylist,
                onClearQueue = onClearQueue,
                onLineClick = onShowLyrics,
                onArtist = onArtist,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (immersiveAlbumCover) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f),
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
                            FullBleedCover(song = song, embeddedCover = embeddedCover, modifier = Modifier.fillMaxSize())
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.White.copy(alpha = 0.18f),
                                            Color.White.copy(alpha = 0.06f),
                                            Color.White.copy(alpha = 0.16f)
                                        )
                                    )
                                )
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(220.dp)
                                .background(
                                    Brush.verticalGradient(
                                        colorStops = arrayOf(
                                            0.0f to Color.Transparent,
                                            0.48f to palette.middle.copy(alpha = 0.42f),
                                            0.78f to palette.middle.copy(alpha = 0.86f),
                                            1.0f to palette.middle
                                        )
                                    )
                                )
                        )
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(playerContentSurfaceBrush(palette, flowEffectMode))
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .padding(horizontal = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PlayerSongMetaText(
                                song = song,
                                annotation = annotation,
                                titleFontSize = 22.sp,
                                artistFontSize = 14.sp,
                                artistAlpha = 0.54f,
                                onArtistClick = onArtist,
                                modifier = Modifier
                                    .weight(1f)
                                    .widthIn(max = 230.dp)
                            )
                            Spacer(modifier = Modifier.width(20.dp))
                            PlayerHeaderAction(
                                kind = PlayerHeaderActionKind.Favorite,
                                selected = isFavorite,
                                onClick = onToggleFavorite
                            )
                            PlayerHeaderAction(kind = PlayerHeaderActionKind.More, onClick = onToggleMenu)
                        }

                        if (effectiveMiniLyricLine != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            MiniLyricsPreview(
                                songId = song?.id ?: 0L,
                                songTitle = song?.title.orEmpty(),
                                songArtist = song?.artist.orEmpty(),
                                lyrics = lyrics,
                                currentIndex = currentLyricIndex,
                                showTranslation = showTranslation,
                                showPronunciation = showPronunciation,
                                currentPositionMs = currentPosition,
                                isPlaying = isPlaying,
                                fontPath = fontPath,
                                fontWeight = fontWeight,
                                fontScale = fontScale,
                                onLineClick = { onShowLyrics() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(miniLyricsPreviewHeight(effectiveMiniLyricLine, showTranslation, showPronunciation))
                            )
                        }

                        Spacer(modifier = Modifier.weight(1f))
                        PlayerProgressBlock(
                            currentPosition = currentPosition,
                            duration = duration,
                            audioInfo = audioInfo,
                            bluetoothDeviceName = bluetoothDeviceName,
                            palette = palette,
                            allowTapSeek = playerTapSeekEnabled,
                            showTotalDuration = playerShowTotalDuration,
                            onSeek = onSeek
                        )
                        Spacer(modifier = Modifier.height(12.dp))
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
                            onClearQueue = onClearQueue,
                            modifier = Modifier.height(76.dp)
                        )
                        AudioVisualizer(
                            enabled = visualizerEnabled,
                            audioSessionId = audioSessionId,
                            isPlaying = isPlaying,
                            positionMs = currentPosition,
                            accent = Color.White.copy(alpha = 0.86f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(30.dp)
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .windowInsetsPadding(WindowInsets.statusBars)
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .padding(horizontal = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(modifier = Modifier.height(22.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(24.dp)),
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
                                    cornerRadius = 24.dp,
                                    showHiResLogo = showHiResLogo,
                                    hiResLogoUri = hiResLogoUri,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PlayerSongMetaText(
                                song = song,
                                annotation = annotation,
                                titleFontSize = 23.sp,
                                artistFontSize = 14.sp,
                                artistAlpha = 0.62f,
                                onArtistClick = onArtist,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(18.dp))
                            PlayerHeaderAction(
                                kind = PlayerHeaderActionKind.Favorite,
                                selected = isFavorite,
                                onClick = onToggleFavorite
                            )
                        }

                        if (effectiveMiniLyricLine != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            MiniLyricsPreview(
                                songId = song?.id ?: 0L,
                                songTitle = song?.title.orEmpty(),
                                songArtist = song?.artist.orEmpty(),
                                lyrics = lyrics,
                                currentIndex = currentLyricIndex,
                                showTranslation = showTranslation,
                                showPronunciation = showPronunciation,
                                currentPositionMs = currentPosition,
                                isPlaying = isPlaying,
                                fontPath = fontPath,
                                fontWeight = fontWeight,
                                fontScale = fontScale,
                                onLineClick = { onShowLyrics() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(miniLyricsPreviewHeight(effectiveMiniLyricLine, showTranslation, showPronunciation, compact = true))
                            )
                        } else {
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        Spacer(modifier = Modifier.weight(1f))
                        PlayerQuickActionRow(
                            onSongInfo = onSongInfo,
                            onShareSong = onShareSong,
                            onTimer = onOpenTimer,
                            onEditMetadata = onOpenMetadataEditor,
                            onMore = onToggleMenu,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        PlayerProgressBlock(
                            currentPosition = currentPosition,
                            duration = duration,
                            audioInfo = audioInfo,
                            bluetoothDeviceName = bluetoothDeviceName,
                            palette = palette,
                            allowTapSeek = playerTapSeekEnabled,
                            showTotalDuration = playerShowTotalDuration,
                            onSeek = onSeek
                        )
                        Spacer(modifier = Modifier.height(12.dp))
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
                            onClearQueue = onClearQueue,
                            modifier = Modifier.height(92.dp)
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                    }
                }
            }
        }

        PlayerCoverActionSheet(
            show = menuExpanded,
            song = song,
            playbackSpeed = playbackSpeed,
            playbackPitch = playbackPitch,
            visualizerEnabled = visualizerEnabled,
            visualizerAvailable = immersiveAlbumCover,
            metadataEditorId = metadataEditorId,
            lyricTimingEditorId = lyricTimingEditorId,
            sleepTimerEndRealtimeMs = sleepTimerEndRealtimeMs,
            stopAfterCurrentEnabled = stopAfterCurrentEnabled,
            sleepTimerCustomMinutes = sleepTimerCustomMinutes,
            sleepTimerStopAfterCurrent = sleepTimerStopAfterCurrent,
            onDismiss = onDismissMenu,
            onAlbum = onAlbum,
            onArtist = onArtist,
            onDownload = onDownload,
            onLandscape = onLandscape,
            onSongInfo = onSongInfo,
            onAddToPlaylist = onAddToPlaylist,
            onAddToQueue = onAddToQueue,
            onPlayNext = onPlayNext,
            onShareSong = onShareSong,
            onSetRating = onSetRating,
            onAiInterpret = onAiInterpret,
            onSpectrum = onSpectrum,
            onDeleteSong = onDeleteSong,
            onMatchDynamicCover = onMatchDynamicCover,
            onStopAfterCurrent = onStopAfterCurrent,
            onTimer = onTimer,
            onCustomTimerMinutes = onCustomTimerMinutes,
            onCancelTimer = onCancelTimer,
            onSpeed = onSpeed,
            onPitch = onPitch,
            onVisualizerEnabled = onVisualizerEnabled,
            initialPage = actionMenuInitialPage
        )
    }
}
