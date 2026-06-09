package com.ella.music.ui.player

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.ella.music.R
import com.ella.music.data.model.Song
import top.yukonga.miuix.kmp.window.WindowBottomSheet

@Composable
internal fun PlayerQueueSheet(
    show: Boolean,
    playlist: List<Song>,
    currentSongId: Long?,
    onDismiss: () -> Unit,
    onSongClick: (Int) -> Unit,
    onRemoveSong: (Int) -> Unit,
    onMoveSong: (Int, Int) -> Unit,
    onAddQueueToPlaylist: () -> Unit,
    onClearQueue: () -> Unit
) {
    if (!show) return

    WindowBottomSheet(
        show = true,
        enableNestedScroll = false,
        title = stringResource(R.string.player_queue_title),
        onDismissRequest = onDismiss
    ) {
        PlayerQueueMenu(
            playlist = playlist,
            currentSongId = currentSongId,
            onSongClick = onSongClick,
            onRemoveSong = onRemoveSong,
            onMoveSong = onMoveSong,
            onAddQueueToPlaylist = onAddQueueToPlaylist,
            onClearQueue = onClearQueue,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
