package com.ella.music.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.ella.music.R
import com.ella.music.data.model.Song
import com.ella.music.data.model.playlistIdentityKey
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.theme.MiuixTheme

private data class QueueEntry(
    val stableKey: String,
    val song: Song
)

private fun buildQueueEntries(items: List<Song>): List<QueueEntry> {
    val occurrenceByIdentity = linkedMapOf<String, Int>()
    return items.map { song ->
        val identity = song.playlistIdentityKey()
        val occurrence = (occurrenceByIdentity[identity] ?: 0) + 1
        occurrenceByIdentity[identity] = occurrence
        QueueEntry(
            stableKey = "$identity|queue#$occurrence",
            song = song
        )
    }
}

@Composable
internal fun PlayerQueueMenu(
    playlist: List<Song>,
    currentSongId: Long?,
    onSongClick: (Int) -> Unit,
    onRemoveSong: (Int) -> Unit,
    onMoveSong: (Int, Int) -> Unit,
    onAddQueueToPlaylist: () -> Unit,
    onClearQueue: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var manualPlaylist by remember(playlist) { mutableStateOf(buildQueueEntries(playlist)) }
    var pendingMoveStart by remember(playlist) { mutableStateOf<Int?>(null) }
    var pendingMoveTarget by remember(playlist) { mutableStateOf<Int?>(null) }
    val currentIndex = remember(manualPlaylist, currentSongId) {
        manualPlaylist.indexOfFirst { it.song.id == currentSongId }
    }
    LaunchedEffect(currentIndex) {
        if (currentIndex >= 0) {
            listState.scrollToItem(currentIndex)
        }
    }
    val reorderableLazyListState = rememberReorderableLazyListState(
        lazyListState = listState,
        onMove = { from, to ->
            if (from.index !in manualPlaylist.indices || to.index !in manualPlaylist.indices) return@rememberReorderableLazyListState
            manualPlaylist = manualPlaylist.toMutableList().apply {
                add(to.index, removeAt(from.index))
            }
            if (pendingMoveStart == null) pendingMoveStart = from.index
            pendingMoveTarget = to.index
        }
    )

    Column(
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.weight(1f))
            if (playlist.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .playerNoIndicationClick(onAddQueueToPlaylist),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = MiuixIcons.Regular.Add,
                        contentDescription = stringResource(R.string.player_add_to_playlist),
                        tint = MiuixTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .playerNoIndicationClick {
                            if (currentIndex >= 0) {
                                scope.launch { listState.animateScrollToItem(currentIndex) }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_my_location),
                        contentDescription = stringResource(R.string.player_locate_current_song),
                        tint = MiuixTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .playerNoIndicationClick(onClearQueue),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_delete),
                        contentDescription = stringResource(R.string.player_clear_queue),
                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
        if (playlist.isEmpty()) {
            Text(
                text = stringResource(R.string.player_queue_empty),
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 18.dp)
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.heightIn(max = 420.dp)
            ) {
                itemsIndexed(manualPlaylist, key = { _, item -> item.stableKey }) { index, item ->
                    ReorderableItem(
                        state = reorderableLazyListState,
                        key = item.stableKey
                    ) { isDragging ->
                        val dragHandleModifier = Modifier.draggableHandle(
                            onDragStopped = {
                                val fromIndex = pendingMoveStart
                                val toIndex = pendingMoveTarget
                                if (fromIndex != null && toIndex != null && fromIndex != toIndex) {
                                    onMoveSong(fromIndex, toIndex)
                                }
                                pendingMoveStart = null
                                pendingMoveTarget = null
                            }
                        )
                        val queueSong = item.song
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 60.dp)
                                .zIndex(if (isDragging) 1f else 0f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    when {
                                        isDragging -> MiuixTheme.colorScheme.primary.copy(alpha = 0.16f)
                                        queueSong.id == currentSongId -> MiuixTheme.colorScheme.primary.copy(alpha = 0.12f)
                                        else -> Color.Transparent
                                    }
                                )
                                .clickable { onSongClick(index) }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AlbumArtView(
                                song = queueSong,
                                embeddedCover = null,
                                cornerRadius = 10.dp,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = queueSong.title,
                                    fontSize = 13.sp,
                                    fontWeight = if (queueSong.id == currentSongId) FontWeight.Bold else FontWeight.Medium,
                                    color = if (queueSong.id == currentSongId) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = queueSong.artist,
                                    fontSize = 11.sp,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .then(dragHandleModifier)
                                    .size(30.dp)
                                    .clip(RoundedCornerShape(15.dp))
                                    .background(
                                        if (isDragging) MiuixTheme.colorScheme.primary.copy(alpha = 0.14f)
                                        else Color.Transparent
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "\u2630",
                                    fontSize = 15.sp,
                                    color = if (isDragging) {
                                        MiuixTheme.colorScheme.primary
                                    } else {
                                        MiuixTheme.colorScheme.onSurfaceVariantSummary
                                    }
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .playerNoIndicationClick { onRemoveSong(index) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_delete),
                                    contentDescription = stringResource(R.string.player_remove_from_queue),
                                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
