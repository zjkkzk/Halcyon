package com.ella.music.ui.player

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ella.music.R
import com.ella.music.data.model.Song
import com.ella.music.ui.components.EllaMiuixBottomSheet

@Composable
internal fun PlayerCoverActionSheet(
    show: Boolean,
    song: Song?,
    playbackSpeed: Float,
    playbackPitch: Float,
    visualizerEnabled: Boolean,
    visualizerAvailable: Boolean,
    lyricOffsetMs: Long,
    metadataEditorId: String,
    lyricTimingEditorId: String,
    showPlayerKeepScreenOnAction: Boolean,
    playerKeepScreenOn: Boolean,
    sleepTimerEndRealtimeMs: Long?,
    stopAfterCurrentEnabled: Boolean,
    sleepTimerCustomMinutes: Int,
    sleepTimerStopAfterCurrent: Boolean,
    onDismiss: () -> Unit,
    onAlbum: () -> Unit,
    onArtist: () -> Unit,
    onDownload: () -> Unit,
    onLandscape: () -> Unit,
    onSongInfo: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onAddToQueue: () -> Unit,
    onPlayNext: () -> Unit,
    onShareSong: () -> Unit,
    onSetRating: () -> Unit,
    onAiInterpret: () -> Unit,
    onSpectrum: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onDeleteSong: () -> Unit,
    onEditMetadata: () -> Unit,
    onLyricTiming: () -> Unit,
    onMatchOnlineLyrics: () -> Unit,
    onMatchDynamicCover: () -> Unit,
    onStopAfterCurrent: (Boolean) -> Unit,
    onTimer: (Int) -> Unit,
    onCustomTimerMinutes: (Int) -> Unit,
    onCancelTimer: () -> Unit,
    onSpeed: (Float) -> Unit,
    onPitch: (Float) -> Unit,
    onLyricOffset: (Long) -> Unit,
    onVisualizerEnabled: (Boolean) -> Unit,
    onPlayerKeepScreenOnChange: (Boolean) -> Unit,
    initialPage: PlayerActionSheetPage
) {
    if (!show) return

    EllaMiuixBottomSheet(
        show = true,
        enableNestedScroll = false,
        title = stringResource(R.string.player_more_actions),
        onDismissRequest = onDismiss
    ) {
        PlayerActionMenu(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp),
            song = song,
            speed = playbackSpeed,
            pitch = playbackPitch,
            visualizerEnabled = visualizerEnabled,
            visualizerAvailable = visualizerAvailable,
            lyricOffsetMs = lyricOffsetMs,
            metadataEditorId = metadataEditorId,
            lyricTimingEditorId = lyricTimingEditorId,
            showPlayerKeepScreenOnAction = showPlayerKeepScreenOnAction,
            playerKeepScreenOn = playerKeepScreenOn,
            sleepTimerEndRealtimeMs = sleepTimerEndRealtimeMs,
            stopAfterCurrentEnabled = stopAfterCurrentEnabled,
            sleepTimerCustomMinutes = sleepTimerCustomMinutes,
            sleepTimerStopAfterCurrent = sleepTimerStopAfterCurrent,
            onClose = onDismiss,
            onAlbum = onAlbum,
            onArtist = onArtist,
            onDownload = onDownload,
            onLandscape = onLandscape,
            onSongInfo = onSongInfo,
            onAddToPlaylist = onAddToPlaylist,
            onAddToQueue = onAddToQueue,
            onPlayNext = onPlayNext,
            onShare = onShareSong,
            onSetRating = onSetRating,
            onAiInterpret = onAiInterpret,
            onSpectrum = onSpectrum,
            onOpenEqualizer = onOpenEqualizer,
            onDeleteSong = onDeleteSong,
            onEditMetadata = onEditMetadata,
            onLyricTiming = onLyricTiming,
            onMatchOnlineLyrics = onMatchOnlineLyrics,
            onMatchDynamicCover = onMatchDynamicCover,
            onStopAfterCurrent = onStopAfterCurrent,
            onTimer = onTimer,
            onCustomTimerMinutes = onCustomTimerMinutes,
            onCancelTimer = onCancelTimer,
            onSpeed = onSpeed,
            onPitch = onPitch,
            onLyricOffset = onLyricOffset,
            onVisualizerEnabled = onVisualizerEnabled,
            onPlayerKeepScreenOnChange = onPlayerKeepScreenOnChange,
            initialPage = initialPage
        )
    }
}
