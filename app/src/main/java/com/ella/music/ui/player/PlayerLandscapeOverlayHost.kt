package com.ella.music.ui.player

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.ella.music.data.model.AudioInfo
import com.ella.music.data.model.LyricLine
import com.ella.music.data.model.Song

@Composable
internal fun PlayerLandscapeOverlayHost(
    context: Context,
    expanded: Boolean,
    coverMode: Boolean,
    dynamicCoverEnabled: Boolean,
    song: Song?,
    embeddedCover: Bitmap?,
    annotation: String,
    dynamicCoverFailedPath: String?,
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
    queueExpanded: Boolean,
    playlist: List<Song>,
    audioSessionId: Int,
    visualizerEnabled: Boolean,
    flowEffectMode: Int,
    isFavorite: Boolean,
    onDynamicCoverFailed: (String) -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleQueue: () -> Unit,
    onDismissQueue: () -> Unit,
    onShowLyrics: () -> Unit,
    onShowCoverPlayer: () -> Unit,
    onLyricLineClick: (LyricLine) -> Unit,
    onLyricLineLongClick: (LyricLine) -> Unit,
    onSeekProgress: (Float) -> Unit,
    onCyclePlaybackMode: () -> Unit,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onQueueSongClick: (Int) -> Unit,
    onRemoveQueueSong: (Int) -> Unit,
    onMoveQueueSong: (Int, Int) -> Unit,
    onAddQueueToPlaylist: () -> Unit,
    onClearQueue: () -> Unit,
    onArtist: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!expanded) return

    ForceLandscapePlayerBars(onDismiss = onDismiss)

    if (coverMode) {
        val landscapeDynamicCoverSource = if (dynamicCoverEnabled) {
            song
                ?.dynamicCoverSource(context)
                ?.takeUnless { it.failureKey == dynamicCoverFailedPath }
        } else {
            null
        }
        LandscapeCoverPlaybackOverlay(
            song = song,
            embeddedCover = embeddedCover,
            annotation = annotation,
            dynamicCoverSource = landscapeDynamicCoverSource,
            isPlaying = isPlaying,
            currentPosition = currentPosition,
            duration = duration,
            shuffleEnabled = shuffleEnabled,
            repeatMode = repeatMode,
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
            queueExpanded = queueExpanded,
            playlist = playlist,
            audioSessionId = audioSessionId,
            visualizerEnabled = visualizerEnabled,
            flowEffectMode = flowEffectMode,
            onDynamicCoverFailed = onDynamicCoverFailed,
            isFavorite = isFavorite,
            onToggleFavorite = onToggleFavorite,
            onToggleQueue = onToggleQueue,
            onDismissQueue = onDismissQueue,
            onShowLyrics = onShowLyrics,
            onLyricLineClick = onLyricLineClick,
            onLyricLineLongClick = onLyricLineLongClick,
            onSeek = onSeekProgress,
            onCyclePlaybackMode = onCyclePlaybackMode,
            onPrevious = onPrevious,
            onPlayPause = onPlayPause,
            onNext = onNext,
            onQueueSongClick = onQueueSongClick,
            onRemoveQueueSong = onRemoveQueueSong,
            onMoveQueueSong = onMoveQueueSong,
            onAddQueueToPlaylist = onAddQueueToPlaylist,
            onClearQueue = onClearQueue,
            onArtist = onArtist,
            onDismiss = onDismiss,
            modifier = Modifier.fillMaxSize()
        )
    } else {
        LandscapeLyricsOverlay(
            song = song,
            embeddedCover = embeddedCover,
            annotation = annotation,
            lyrics = lyrics,
            currentLyricIndex = currentLyricIndex,
            currentPosition = currentPosition,
            duration = duration,
            shuffleEnabled = shuffleEnabled,
            repeatMode = repeatMode,
            showTranslation = showTranslation,
            showPronunciation = showPronunciation,
            fontFamily = fontFamily,
            fontPath = fontPath,
            fontWeight = fontWeight,
            fontScale = fontScale,
            showTotalDuration = showTotalDuration,
            palette = palette,
            flowEffectMode = flowEffectMode,
            isPlaying = isPlaying,
            audioSessionId = audioSessionId,
            visualizerEnabled = visualizerEnabled,
            onLineClick = onLyricLineClick,
            onLineLongClick = onLyricLineLongClick,
            onSeek = onSeekProgress,
            onCyclePlaybackMode = onCyclePlaybackMode,
            onPrevious = onPrevious,
            onPlayPause = onPlayPause,
            onNext = onNext,
            onShowCoverPlayer = onShowCoverPlayer,
            onDismiss = onDismiss,
            modifier = Modifier.fillMaxSize()
        )
    }
}
