package com.ella.music.ui.player

import android.graphics.Bitmap
import androidx.compose.foundation.background
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import top.yukonga.miuix.kmp.basic.Icon

@Composable
internal fun LandscapeCoverPlayerPage(
    song: Song?,
    embeddedCover: Bitmap?,
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
    showTotalDuration: Boolean,
    playerTapSeekEnabled: Boolean,
    queueExpanded: Boolean,
    playlist: List<Song>,
    audioSessionId: Int,
    visualizerEnabled: Boolean,
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
    modifier: Modifier = Modifier
) {
    val bluetoothDeviceName = rememberBluetoothOutputName()
    Box(modifier = modifier.background(palette.middle)) {
        LandscapeCoverModeBackground(
            palette = palette,
            currentPosition = currentPosition,
            isPlaying = isPlaying,
            flowEffectMode = flowEffectMode,
            dynamicFlowEnabled = dynamicFlowEnabled,
            visualizerEnabled = visualizerEnabled,
            customBackgroundUri = customBackgroundUri,
            customBackgroundOpacity = customBackgroundOpacity,
            customBackgroundDim = customBackgroundDim,
            beautifulLyricsBackground = beautifulLyricsBackground,
            modifier = Modifier.fillMaxSize()
        )
        Row(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 32.dp, vertical = 22.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(0.38f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.88f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(14.dp)),
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
                Spacer(modifier = Modifier.height(8.dp))
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
                Spacer(modifier = Modifier.height(4.dp))
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
            Spacer(modifier = Modifier.width(28.dp))
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(0.62f)
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
        AudioVisualizer(
            enabled = visualizerEnabled,
            audioSessionId = audioSessionId,
            isPlaying = isPlaying,
            positionMs = currentPosition,
            accent = Color.White.copy(alpha = 0.72f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .fillMaxWidth()
                .height(68.dp)
        )
    }
}
