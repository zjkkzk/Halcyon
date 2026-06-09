package com.ella.music.ui.player

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.ella.music.R
import com.ella.music.data.model.Song
import top.yukonga.miuix.kmp.window.WindowBottomSheet

@Composable
internal fun PlayerCoverActionSheet(
    show: Boolean,
    song: Song?,
    playbackSpeed: Float,
    playbackPitch: Float,
    visualizerEnabled: Boolean,
    visualizerAvailable: Boolean,
    metadataEditorId: String,
    lyricTimingEditorId: String,
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
    onDeleteSong: () -> Unit,
    onMatchDynamicCover: () -> Unit,
    onStopAfterCurrent: (Boolean) -> Unit,
    onTimer: (Int) -> Unit,
    onCustomTimerMinutes: (Int) -> Unit,
    onCancelTimer: () -> Unit,
    onSpeed: (Float) -> Unit,
    onPitch: (Float) -> Unit,
    onVisualizerEnabled: (Boolean) -> Unit,
    initialPage: PlayerActionSheetPage
) {
    if (!show) return

    WindowBottomSheet(
        show = true,
        enableNestedScroll = false,
        title = stringResource(R.string.player_more_actions),
        onDismissRequest = onDismiss
    ) {
        PlayerActionMenu(
            modifier = Modifier.fillMaxWidth(),
            song = song,
            speed = playbackSpeed,
            pitch = playbackPitch,
            visualizerEnabled = visualizerEnabled,
            visualizerAvailable = visualizerAvailable,
            metadataEditorId = metadataEditorId,
            lyricTimingEditorId = lyricTimingEditorId,
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
            onDeleteSong = onDeleteSong,
            onMatchDynamicCover = onMatchDynamicCover,
            onStopAfterCurrent = onStopAfterCurrent,
            onTimer = onTimer,
            onCustomTimerMinutes = onCustomTimerMinutes,
            onCancelTimer = onCancelTimer,
            onSpeed = onSpeed,
            onPitch = onPitch,
            onVisualizerEnabled = onVisualizerEnabled,
            initialPage = initialPage
        )
    }
}
