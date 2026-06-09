package com.ella.music.ui.playlist

import com.ella.music.ui.components.EllaMiuixBottomSheet

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ella.music.R
import com.ella.music.data.model.FIVE_STAR_PLAYLIST_ID
import com.ella.music.data.model.FAVORITES_PLAYLIST_ID
import com.ella.music.data.model.Song
import com.ella.music.data.model.UserPlaylist
import com.ella.music.data.model.playlistIdentityKey
import com.ella.music.data.PlaylistExportFormat
import com.ella.music.ui.components.AddToPlaylistSheet
import com.ella.music.ui.components.ConfirmDangerDialog
import com.ella.music.ui.components.CreatePlaylistAndAddSheet
import com.ella.music.ui.components.DoubleTapScrollOverlay
import com.ella.music.ui.components.LocateCurrentSongFloatingButton
import com.ella.music.ui.components.SongItem
import com.ella.music.ui.components.SongMoreActionHost
import com.ella.music.ui.components.ellaPageBackground
import com.ella.music.viewmodel.MainViewModel
import com.ella.music.viewmodel.PlayerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun PlaylistDetailScreen(
    playlistId: String,
    mainViewModel: MainViewModel,
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit,
    onNavigateToAlbum: (Long) -> Unit = {},
    onNavigateToArtist: (String) -> Unit = {},
    onNavigateToPlayer: () -> Unit
) {
    val context = LocalContext.current
    val playlists by mainViewModel.playlists.collectAsState()
    val currentSong by playerViewModel.currentSong.collectAsState()
    val favoriteSongKeys by playerViewModel.favoriteSongKeys.collectAsState()
    val locateCurrentSongRequest by playerViewModel.locateCurrentSongRequest.collectAsState()
    val librarySongs by mainViewModel.songs.collectAsState()
    val ratingRevision by mainViewModel.ratingRevision.collectAsState()
    val playbackStats by mainViewModel.playbackStats.collectAsState()
    val openPlayerOnPlay by mainViewModel.settingsManager.openPlayerOnPlay.collectAsState(initial = false)
    val showPlayNextInLists by mainViewModel.settingsManager.showPlayNextInLists.collectAsState(initial = false)
    val isFiveStarPlaylist = playlistId == FIVE_STAR_PLAYLIST_ID
    val storedPlaylist = playlists.firstOrNull { it.id == playlistId }
    val fiveStarSongs by produceState(initialValue = emptyList(), isFiveStarPlaylist, librarySongs, ratingRevision) {
        value = if (isFiveStarPlaylist) mainViewModel.getFiveStarSongs() else emptyList()
    }
    val playlist = if (isFiveStarPlaylist) {
        UserPlaylist(
            id = FIVE_STAR_PLAYLIST_ID,
            name = stringResource(R.string.playlist_five_star_name),
            createdAt = 0L,
            updatedAt = 0L
        )
    } else {
        storedPlaylist
    }
    val songs = remember(playlist, librarySongs, fiveStarSongs, isFiveStarPlaylist) {
        if (isFiveStarPlaylist) fiveStarSongs else playlist?.let(mainViewModel::playlistSongs).orEmpty()
    }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var actionSong by remember { mutableStateOf<com.ella.music.data.model.Song?>(null) }
    var sortExpanded by remember { mutableStateOf(false) }
    val sortIndex by mainViewModel.settingsManager.playlistDetailSongSortIndex.collectAsState(initial = 2)
    val sortMode = PlaylistSongSortMode.entries.getOrElse(sortIndex) { PlaylistSongSortMode.AddedAt }
    var searchExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var removeFromPlaylistSong by remember { mutableStateOf<Song?>(null) }
    var removeSelectedPlaylistSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var playlistPickerSongs by remember { mutableStateOf<List<Song>?>(null) }
    var createPlaylistSongs by remember { mutableStateOf<List<Song>?>(null) }
    var manualOrder by remember(playlist?.id) { mutableStateOf(songs) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedSongKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    val sortedSongs = remember(songs, sortMode) { songs.sortedForPlaylistDetail(sortMode) }
    LaunchedEffect(playlist?.id, songs) {
        manualOrder = songs
    }
    LaunchedEffect(playlist?.id) {
        selectionMode = false
        selectedSongKeys = emptySet()
    }
    val reorderEnabled = playlist?.isFiveStarRating != true &&
        sortMode == PlaylistSongSortMode.Custom &&
        searchQuery.isBlank()
    val reorderHandlesVisible = selectionMode && reorderEnabled
    val baseSongs = if (reorderEnabled) manualOrder else sortedSongs
    val displayedSongs by produceState(initialValue = baseSongs, baseSongs, searchQuery, ratingRevision) {
        val query = searchQuery.trim()
        value = if (query.isBlank()) {
            baseSongs
        } else {
            withContext(Dispatchers.IO) {
                baseSongs.filter { song ->
                    mainViewModel.songMatchesSearchSnapshot(song, query)
                }
            }
        }
    }
    val songListHeaderCount = 2
    val reorderableLazyListState = rememberReorderableLazyListState(
        lazyListState = listState,
        onMove = { from, to ->
            if (!reorderHandlesVisible) return@rememberReorderableLazyListState
            val fromSongIndex = from.index - songListHeaderCount
            val toSongIndex = to.index - songListHeaderCount
            if (fromSongIndex !in manualOrder.indices || toSongIndex !in manualOrder.indices) return@rememberReorderableLazyListState
            manualOrder = manualOrder.toMutableList().apply {
                add(toSongIndex, removeAt(fromSongIndex))
            }
        }
    )
    fun finishSelectionMode() {
        selectionMode = false
        selectedSongKeys = emptySet()
    }
    fun toggleSelection(song: Song) {
        val key = song.playlistIdentityKey()
        val next = if (key in selectedSongKeys) selectedSongKeys - key else selectedSongKeys + key
        selectedSongKeys = next
        if (next.isEmpty()) selectionMode = false
    }
    fun selectAllDisplayedSongs() {
        val displayedKeys = displayedSongs.mapTo(mutableSetOf()) { it.playlistIdentityKey() }
        selectedSongKeys = if (displayedKeys.isNotEmpty() && displayedKeys.all { it in selectedSongKeys }) {
            emptySet()
        } else {
            displayedKeys
        }
        selectionMode = true
    }
    fun selectedDisplayedSongs(): List<Song> =
        displayedSongs.filter { it.playlistIdentityKey() in selectedSongKeys }
    BackHandler(enabled = selectionMode || sortExpanded || searchExpanded) {
        when {
            selectionMode -> finishSelectionMode()
            searchExpanded -> {
                searchExpanded = false
                searchQuery = ""
            }
            sortExpanded -> sortExpanded = false
        }
    }
    val currentSongItemIndex = remember(displayedSongs, currentSong?.playlistIdentityKey()) {
        displayedSongs.indexOfFirst { it.playlistIdentityKey() == currentSong?.playlistIdentityKey() }
            .takeIf { it >= 0 }
            ?.plus(2)
            ?: -1
    }
    val playlistCoverModel = remember(sortedSongs) {
        sortedSongs.firstOrNull()?.let { song ->
            song.coverUrl.takeIf { it.isNotBlank() } ?: mainViewModel.getAlbumArtUri(song.albumId)
        }
    }
    var showExportFormatSheet by remember { mutableStateOf(false) }
    var pendingM3uExportFormat by remember { mutableStateOf<PlaylistExportFormat?>(null) }
    val txtExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        val targetPlaylist = playlist
        if (uri == null || targetPlaylist == null) return@rememberLauncherForActivityResult
        mainViewModel.exportLocalPlaylist(targetPlaylist, uri, PlaylistExportFormat.PlainText) { result ->
            result
                .onSuccess { exportResult ->
                    val skippedText = if (exportResult.skippedCount > 0) context.getString(R.string.playlist_export_skipped, exportResult.skippedCount) else ""
                    Toast.makeText(context, context.getString(R.string.playlist_export_done, exportResult.exportedCount, skippedText), Toast.LENGTH_SHORT).show()
                }
                .onFailure {
                    Toast.makeText(context, context.getString(R.string.playlist_export_failed, it.message.orEmpty()), Toast.LENGTH_SHORT).show()
                }
        }
    }
    val m3uExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/x-mpegurl")) { uri ->
        val targetPlaylist = playlist
        val targetFormat = pendingM3uExportFormat ?: PlaylistExportFormat.M3u8
        pendingM3uExportFormat = null
        if (uri == null || targetPlaylist == null) return@rememberLauncherForActivityResult
        mainViewModel.exportLocalPlaylist(targetPlaylist, uri, targetFormat) { result ->
            result
                .onSuccess { exportResult ->
                    val skippedText = if (exportResult.skippedCount > 0) context.getString(R.string.playlist_export_skipped, exportResult.skippedCount) else ""
                    Toast.makeText(context, context.getString(R.string.playlist_export_done, exportResult.exportedCount, skippedText), Toast.LENGTH_SHORT).show()
                }
                .onFailure {
                    Toast.makeText(context, context.getString(R.string.playlist_export_failed, it.message.orEmpty()), Toast.LENGTH_SHORT).show()
                }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ellaPageBackground())
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        Box {
            PlaylistDetailTopBar(
                title = when {
                    selectionMode -> stringResource(R.string.library_selected_count, selectedSongKeys.size)
                    playlist == null -> stringResource(R.string.playlist_title)
                    listState.firstVisibleItemIndex > 0 -> playlist.name
                    else -> stringResource(R.string.playlist_title)
                },
                selectionMode = selectionMode,
                showRemoveSelected = !isFiveStarPlaylist,
                showExport = playlist != null && !isFiveStarPlaylist,
                onNavigationClick = {
                    if (selectionMode) finishSelectionMode() else onBack()
                },
                onAddSelectedClick = {
                    val selected = selectedDisplayedSongs()
                    if (selected.isEmpty()) {
                        Toast.makeText(context, context.getString(R.string.library_select_songs_first), Toast.LENGTH_SHORT).show()
                    } else {
                        playlistPickerSongs = selected
                    }
                },
                onRemoveSelectedClick = {
                    val selected = selectedDisplayedSongs()
                    if (selected.isNotEmpty()) removeSelectedPlaylistSongs = selected
                },
                onSortClick = { sortExpanded = !sortExpanded },
                onSearchClick = {
                    searchExpanded = !searchExpanded
                    if (!searchExpanded) searchQuery = ""
                },
                onExportClick = { showExportFormatSheet = true }
            )
            DoubleTapScrollOverlay(
                onDoubleTap = { scope.launch { listState.animateScrollToItem(0) } },
                onClick = { scope.launch { listState.animateScrollToItem(0) } },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                endPadding = 160.dp
            )
        }

        PlaylistDetailSortSection(
            visible = sortExpanded && !selectionMode,
            sortMode = sortMode,
            onModeSelected = { mode ->
                sortExpanded = false
                scope.launch { mainViewModel.settingsManager.setPlaylistDetailSongSortIndex(mode.ordinal) }
                scope.launch { listState.animateScrollToItem(0) }
            }
        )

        PlaylistDetailSearchSection(
            visible = searchExpanded && !selectionMode,
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            onSearch = { searchExpanded = false }
        )

        if (playlist == null) {
            PlaylistDetailNotFoundState()
            return@Column
        }

        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 150.dp)
            ) {
                item {
                    val playlistPlayCount = remember(sortedSongs, playbackStats) {
                        val statsMap = playbackStats.associateBy { it.songId }
                        sortedSongs.sumOf { statsMap[it.id]?.playCount ?: 0 }
                    }
                    PlaylistDetailHero(
                        playlist = playlist,
                        coverModel = playlistCoverModel,
                        songCount = sortedSongs.size,
                        playCount = playlistPlayCount,
                        duration = sortedSongs.sumOf { it.duration },
                        sortLabel = stringResource(sortMode.labelRes)
                    )
                }

                item {
                    PlaylistPlayAllBar(
                        songCount = displayedSongs.size,
                        sortLabel = stringResource(sortMode.labelRes),
                        onPlayAll = {
                            if (displayedSongs.isNotEmpty()) {
                                playerViewModel.setPlaylist(displayedSongs, 0)
                                if (openPlayerOnPlay) onNavigateToPlayer()
                            }
                        },
                        onSort = { sortExpanded = !sortExpanded }
                    )
                }

            if (displayedSongs.isEmpty()) {
                item {
                    PlaylistDetailEmptyState(
                        searchQuery = searchQuery,
                        playlist = playlist
                    )
                }
            } else {
                itemsIndexed(displayedSongs, key = { _, song -> song.playlistIdentityKey() }) { index, song ->
                    ReorderableItem(
                        state = reorderableLazyListState,
                        key = song.playlistIdentityKey()
                    ) { isDragging ->
                        val dragHandleModifier = Modifier.draggableHandle(
                            onDragStopped = {
                                mainViewModel.reorderPlaylistSongs(
                                    playlist.id,
                                    manualOrder.map { it.playlistIdentityKey() }
                                )
                            }
                        )
                        SongItem(
                            song = song,
                            isCurrent = currentSong?.playlistIdentityKey() == song.playlistIdentityKey(),
                            albumArtUri = mainViewModel.getAlbumArtUri(song.albumId),
                            loadCoverArt = mainViewModel::getCoverArtBitmap,
                            loadAudioInfo = mainViewModel::getAudioInfo,
                            selectionMode = selectionMode,
                            selected = song.playlistIdentityKey() in selectedSongKeys,
                            isFavorite = song.playlistIdentityKey() in favoriteSongKeys,
                            loadSongRating = mainViewModel::getSongRating,
                            ratingRevision = ratingRevision,
                            showPlayNextInLists = showPlayNextInLists,
                            onClick = {
                                if (selectionMode) {
                                    toggleSelection(song)
                                } else {
                                    playerViewModel.setPlaylist(displayedSongs, index)
                                    if (openPlayerOnPlay) onNavigateToPlayer()
                                }
                            },
                            onLongClick = {
                                selectionMode = true
                                selectedSongKeys = selectedSongKeys + song.playlistIdentityKey()
                            },
                            onPlayNext = { playerViewModel.playNext(song) },
                            onRemove = if (playlist.isFiveStarRating) null else {
                                {
                                    removeFromPlaylistSong = song
                                }
                            },
                            onMore = { actionSong = song },
                            leadingLabel = (index + 1).toString(),
                            leadingLabelBeforeCover = true,
                            trailingContent = if (reorderHandlesVisible) {
                                {
                                    PlaylistDetailReorderHandle(
                                        isDragging = isDragging,
                                        modifier = Modifier
                                            .then(dragHandleModifier)
                                    )
                                }
                            } else null,
                            showTrailingContentInSelectionMode = reorderHandlesVisible,
                            modifier = Modifier
                        )
                    }
                }
            }
            }

            LocateCurrentSongFloatingButton(
                listState = listState,
                currentItemIndex = if (selectionMode) -1 else currentSongItemIndex,
                locateRequest = locateCurrentSongRequest,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 22.dp, bottom = 118.dp)
            )

            PlaylistDetailSelectAllFloatingButton(
                visible = selectionMode && displayedSongs.isNotEmpty(),
                onClick = { selectAllDisplayedSongs() },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 22.dp, bottom = 118.dp)
            )

            SongMoreActionHost(
                actionSong = actionSong,
                mainViewModel = mainViewModel,
                playerViewModel = playerViewModel,
                onDismissAction = { actionSong = null },
                onNavigateToAlbum = onNavigateToAlbum,
                onNavigateToArtist = onNavigateToArtist,
                onSongRemovedFromPlaylist = if (playlist.isFiveStarRating) null else {
                    { song -> removeFromPlaylistSong = song }
                }
            )

            removeFromPlaylistSong?.let { song ->
                ConfirmDangerDialog(
                    show = true,
                    title = stringResource(R.string.playlist_remove_song_title),
                    message = stringResource(R.string.playlist_remove_song_message, playlist.name, song.title.ifBlank { song.fileName.ifBlank { stringResource(R.string.common_this_song) } }),
                    confirmText = stringResource(R.string.common_remove),
                    onDismiss = { removeFromPlaylistSong = null },
                    onConfirm = {
                        mainViewModel.removeSongFromPlaylist(playlist.id, song.playlistIdentityKey())
                        removeFromPlaylistSong = null
                    }
                )
            }

            if (removeSelectedPlaylistSongs.isNotEmpty()) {
                ConfirmDangerDialog(
                    show = true,
                    title = stringResource(R.string.playlist_remove_selected_title),
                    message = stringResource(R.string.playlist_remove_selected_message, removeSelectedPlaylistSongs.size),
                    confirmText = stringResource(R.string.common_remove),
                    onDismiss = { removeSelectedPlaylistSongs = emptyList() },
                    onConfirm = {
                        mainViewModel.removeSongsFromPlaylist(
                            playlist.id,
                            removeSelectedPlaylistSongs.mapTo(mutableSetOf()) { it.playlistIdentityKey() }
                        )
                        removeSelectedPlaylistSongs = emptyList()
                        finishSelectionMode()
                    }
                )
            }

            playlistPickerSongs?.let { songsToAdd ->
                EllaMiuixBottomSheet(
                    show = true,
                    enableNestedScroll = false,
                    title = stringResource(R.string.song_more_add_to_playlist_title),
                    onDismissRequest = { playlistPickerSongs = null }
                ) {
                    AddToPlaylistSheet(
                        playlists = playlists
                            .sortedWith(compareByDescending<UserPlaylist> { it.id == FAVORITES_PLAYLIST_ID }.thenByDescending { it.createdAt }),
                        songCount = songsToAdd.size,
                        onDismiss = { playlistPickerSongs = null },
                        onCreatePlaylist = {
                            createPlaylistSongs = songsToAdd
                            playlistPickerSongs = null
                        },
                        onPlaylistsConfirm = { selectedPlaylists, appendToEnd ->
                            selectedPlaylists.forEach { targetPlaylist ->
                                mainViewModel.addSongsToPlaylist(targetPlaylist.id, songsToAdd, appendToEnd)
                            }
                            Toast.makeText(
                                context,
                                context.getString(R.string.player_added_to_playlists, selectedPlaylists.size),
                                Toast.LENGTH_SHORT
                            ).show()
                            playlistPickerSongs = null
                            finishSelectionMode()
                        }
                    )
                }
            }

            createPlaylistSongs?.let { songsToAdd ->
                CreatePlaylistAndAddSheet(
                    onDismiss = { createPlaylistSongs = null },
                    onCreate = { name ->
                        mainViewModel.createPlaylist(name) { targetPlaylist ->
                            if (targetPlaylist != null) {
                                mainViewModel.addSongsToPlaylist(targetPlaylist.id, songsToAdd)
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.player_added_to_playlist_named, targetPlaylist.name),
                                    Toast.LENGTH_SHORT
                                ).show()
                                finishSelectionMode()
                            }
                        }
                        createPlaylistSongs = null
                    }
                )
            }
        }
    }

    if (showExportFormatSheet && playlist != null) {
        ExportPlaylistFormatSheet(
            onDismiss = { showExportFormatSheet = false },
            onFormatSelected = { format ->
                val extension = when (format) {
                    PlaylistExportFormat.PlainText -> "txt"
                    PlaylistExportFormat.M3u8 -> "m3u8"
                    PlaylistExportFormat.M3u -> "m3u"
                }
                showExportFormatSheet = false
                val fileName = "${playlist.name.safePlaylistFileName()}.$extension"
                when (format) {
                    PlaylistExportFormat.PlainText -> txtExportLauncher.launch(fileName)
                    PlaylistExportFormat.M3u8,
                    PlaylistExportFormat.M3u -> {
                        pendingM3uExportFormat = format
                        m3uExportLauncher.launch(fileName)
                    }
                }
            }
        )
    }
}
