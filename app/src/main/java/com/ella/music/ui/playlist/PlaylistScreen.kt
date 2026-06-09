package com.ella.music.ui.playlist

import android.widget.Toast
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ella.music.R
import com.ella.music.data.model.FIVE_STAR_PLAYLIST_ID
import com.ella.music.data.model.FAVORITES_PLAYLIST_ID
import com.ella.music.data.model.UserPlaylist
import com.ella.music.data.PlaylistExportFormat
import com.ella.music.data.PlaylistImportMode
import com.ella.music.ui.LibrarySortUiState
import com.ella.music.ui.components.ConfirmDangerDialog
import com.ella.music.ui.components.ellaPageBackground
import com.ella.music.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun PlaylistScreen(
    mainViewModel: MainViewModel,
    onBack: () -> Unit,
    onPlaylistClick: (String) -> Unit
) {
    val context = LocalContext.current
    val playlists by mainViewModel.playlists.collectAsState()
    val librarySongs by mainViewModel.songs.collectAsState()
    val ratingRevision by mainViewModel.ratingRevision.collectAsState()
    val showPlayNextInLists by mainViewModel.settingsManager.showPlayNextInLists.collectAsState(initial = false)
    var showCreateDialog by remember { mutableStateOf(false) }
    var sortExpanded by remember { mutableStateOf(false) }
    var searchExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val playlistSortIndex by mainViewModel.settingsManager.playlistListSortIndex.collectAsState(initial = LibrarySortUiState.playlistListSortIndex)
    val playlistCustomOrderIds by mainViewModel.settingsManager.playlistCustomOrder.collectAsState(initial = emptyList())
    val specialPlaylistEntriesVisible by mainViewModel.settingsManager.playlistSpecialEntriesVisible.collectAsState(initial = false)
    val playlistSortMode = PlaylistSortMode.entries.getOrElse(playlistSortIndex) { PlaylistSortMode.UpdatedAt }
    LaunchedEffect(playlistSortIndex) {
        LibrarySortUiState.playlistListSortIndex = playlistSortIndex
    }
    var pendingImportUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var showImportModeSheet by remember { mutableStateOf(false) }
    var playlistPendingDelete by remember { mutableStateOf<UserPlaylist?>(null) }
    var playlistsPendingDelete by remember { mutableStateOf<List<UserPlaylist>>(emptyList()) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedPlaylistIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showExportAllFormatSheet by remember { mutableStateOf(false) }
    var pendingExportAllFormat by remember { mutableStateOf<PlaylistExportFormat?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val favorites = playlists.firstOrNull { it.id == FAVORITES_PLAYLIST_ID }
    val storedCustomPlaylists = remember(playlists) {
        playlists.filterNot { it.id == FAVORITES_PLAYLIST_ID || it.id == FIVE_STAR_PLAYLIST_ID }
    }
    val orderedCustomPlaylists = remember(storedCustomPlaylists, playlistCustomOrderIds) {
        storedCustomPlaylists.applyPlaylistCustomOrder(playlistCustomOrderIds)
    }
    var manualCustomPlaylists by remember(orderedCustomPlaylists) { mutableStateOf(orderedCustomPlaylists) }
    LaunchedEffect(orderedCustomPlaylists) {
        manualCustomPlaylists = orderedCustomPlaylists
    }
    val customPlaylists = remember(storedCustomPlaylists, orderedCustomPlaylists, playlistSortMode) {
        when (playlistSortMode) {
            PlaylistSortMode.Custom -> orderedCustomPlaylists
            PlaylistSortMode.CustomDesc -> orderedCustomPlaylists.asReversed()
            else -> storedCustomPlaylists.sortedForPlaylistList(playlistSortMode)
        }
    }
    val reorderEnabled = selectionMode && playlistSortMode == PlaylistSortMode.Custom && searchQuery.isBlank()
    val customPlaylistsSource = if (reorderEnabled) manualCustomPlaylists else customPlaylists
    val displayedCustomPlaylists = remember(customPlaylistsSource, searchQuery) {
        val query = searchQuery.trim()
        if (query.isBlank()) customPlaylistsSource else customPlaylistsSource.filter { it.matchesPlaylistSearch(query) }
    }
    val playlistCoverModels = remember(playlists, librarySongs) {
        playlists.associate { playlist ->
            playlist.id to mainViewModel.playlistSongs(playlist).firstOrNull().playlistCoverModel()
        }
    }
    val showFavorites = remember(favorites, searchQuery, specialPlaylistEntriesVisible) {
        specialPlaylistEntriesVisible &&
            favorites != null &&
            (searchQuery.isBlank() || favorites.matchesPlaylistSearch(searchQuery.trim()))
    }
    val fiveStarName = stringResource(R.string.playlist_five_star_name)
    val showFiveStar = remember(searchQuery, fiveStarName, specialPlaylistEntriesVisible) {
        specialPlaylistEntriesVisible &&
            (searchQuery.isBlank() || fiveStarName.contains(searchQuery.trim(), ignoreCase = true))
    }
    val fiveStarSongs by produceState(initialValue = emptyList(), librarySongs, ratingRevision) {
        value = mainViewModel.getFiveStarSongs()
    }
    val fiveStarCoverModel = remember(fiveStarSongs) {
        fiveStarSongs.firstOrNull().playlistCoverModel()
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        pendingImportUris = uris
        showImportModeSheet = true
    }
    val exportAllFolderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val format = pendingExportAllFormat
        pendingExportAllFormat = null
        if (uri == null || format == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
        mainViewModel.exportLocalPlaylists(storedCustomPlaylists, uri, format) { result ->
            result
                .onSuccess { exportResult ->
                    Toast.makeText(
                        context,
                        context.getString(
                            R.string.playlist_export_all_done,
                            exportResult.exportedPlaylists,
                            exportResult.exportedSongs
                        ),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                .onFailure {
                    Toast.makeText(
                        context,
                        context.getString(R.string.playlist_export_failed, it.message.orEmpty()),
                        Toast.LENGTH_SHORT
                    ).show()
                }
        }
    }
    fun importPendingPlaylists(mode: PlaylistImportMode) {
        val uris = pendingImportUris
        if (uris.isEmpty()) return
        showImportModeSheet = false
        pendingImportUris = emptyList()
        mainViewModel.importLocalPlaylists(uris, mode) { result ->
            result
                .onSuccess { importResult ->
                    val message = if (importResult.importedCount == 0) {
                        context.getString(R.string.playlist_import_none)
                    } else {
                        val missingText = if (importResult.missingCount > 0) {
                            context.getString(
                                R.string.playlist_import_missing_paths,
                                importResult.missingCount
                            )
                        } else ""
                        val duplicateText = if (importResult.duplicateCount > 0) {
                            context.getString(
                                R.string.playlist_import_duplicates,
                                importResult.duplicateCount
                            )
                        } else ""
                        val playlistText = if (importResult.importedPlaylists > 1) {
                            context.getString(
                                R.string.playlist_import_playlist_prefix,
                                importResult.importedPlaylists
                            )
                        } else ""
                        context.getString(
                            R.string.playlist_import_result,
                            playlistText,
                            importResult.importedCount,
                            importResult.matchedCount,
                            missingText,
                            duplicateText
                        )
                    }
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
                .onFailure {
                    Toast.makeText(
                        context,
                        context.getString(
                            R.string.playlist_import_failed,
                            it.message.orEmpty()
                        ),
                        Toast.LENGTH_SHORT
                    ).show()
                }
        }
    }
    fun finishSelectionMode() {
        selectionMode = false
        selectedPlaylistIds = emptySet()
    }
    fun togglePlaylistSelection(playlist: UserPlaylist) {
        val next = if (playlist.id in selectedPlaylistIds) selectedPlaylistIds - playlist.id else selectedPlaylistIds + playlist.id
        selectedPlaylistIds = next
        if (next.isEmpty()) selectionMode = false
    }
    val playlistListHeaderCount = (if (showFavorites) 1 else 0) + (if (showFiveStar) 1 else 0) + 1
    val reorderableLazyListState = rememberReorderableLazyListState(
        lazyListState = listState,
        onMove = { from, to ->
            if (!reorderEnabled) return@rememberReorderableLazyListState
            val fromIndex = from.index - playlistListHeaderCount
            val toIndex = to.index - playlistListHeaderCount
            if (fromIndex !in manualCustomPlaylists.indices || toIndex !in manualCustomPlaylists.indices) return@rememberReorderableLazyListState
            manualCustomPlaylists = manualCustomPlaylists.toMutableList().apply {
                add(toIndex, removeAt(fromIndex))
            }
        }
    )

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ellaPageBackground())
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        PlaylistScreenTopBar(
            selectionMode = selectionMode,
            selectedCount = selectedPlaylistIds.size,
            onBackClick = { if (selectionMode) finishSelectionMode() else onBack() },
            onDeleteSelectedClick = {
                val targets = storedCustomPlaylists.filter { it.id in selectedPlaylistIds }
                if (targets.isNotEmpty()) playlistsPendingDelete = targets
            },
            onSortClick = { sortExpanded = !sortExpanded },
            onSearchClick = {
                searchExpanded = !searchExpanded
                if (!searchExpanded) searchQuery = ""
            },
            onImportClick = {
                importLauncher.launch(
                    arrayOf(
                        "audio/x-mpegurl",
                        "audio/mpegurl",
                        "application/vnd.apple.mpegurl",
                        "text/plain",
                        "application/octet-stream",
                        "*/*"
                    )
                )
            },
            onExportAllClick = { showExportAllFormatSheet = true },
            onScrollToTop = { scope.launch { listState.animateScrollToItem(0) } }
        )

        PlaylistSearchSection(
            visible = searchExpanded,
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            onSearch = { searchExpanded = false }
        )

        PlaylistSortSection(
            visible = sortExpanded,
            selectedMode = playlistSortMode,
            onModeSelected = { mode ->
                sortExpanded = false
                scope.launch { mainViewModel.settingsManager.setPlaylistListSortIndex(mode.ordinal) }
                scope.launch { listState.animateScrollToItem(0) }
            }
        )

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
        ) {
            if (favorites != null && showFavorites) {
                item(key = favorites.id) {
                    PlaylistRow(
                        playlist = favorites,
                        coverModel = playlistCoverModels[favorites.id],
                        accent = true,
                        onClick = { onPlaylistClick(favorites.id) }
                    )
                }
            }

            if (showFiveStar) item(key = FIVE_STAR_PLAYLIST_ID) {
                PlaylistRow(
                    playlist = UserPlaylist(
                        id = FIVE_STAR_PLAYLIST_ID,
                        name = stringResource(R.string.playlist_five_star_name),
                        createdAt = 0L,
                        updatedAt = 0L
                    ),
                    coverModel = fiveStarCoverModel,
                    countOverride = fiveStarSongs.size,
                    durationOverride = fiveStarSongs.sumOf { it.duration },
                    accent = true,
                    onClick = { onPlaylistClick(FIVE_STAR_PLAYLIST_ID) }
                )
            }

            item {
                PlaylistListSummaryRow(
                    playlistCount = displayedCustomPlaylists.size,
                    sortMode = playlistSortMode,
                    selectionMode = selectionMode,
                    onCreateClick = { showCreateDialog = true },
                    onSelectAllClick = { selectionMode = true }
                )
            }

            if (displayedCustomPlaylists.isEmpty()) {
                item {
                    PlaylistEmptyMessage(searchQuery = searchQuery)
                }
            } else {
                itemsIndexed(displayedCustomPlaylists, key = { _, playlist -> playlist.id }) { _, playlist ->
                    ReorderableItem(
                        state = reorderableLazyListState,
                        key = playlist.id
                    ) { isDragging ->
                        val dragHandleModifier = Modifier.draggableHandle(
                            onDragStopped = {
                                val orderedIds = manualCustomPlaylists.map { it.id }
                                scope.launch { mainViewModel.settingsManager.setPlaylistCustomOrder(orderedIds) }
                                mainViewModel.reorderPlaylists(orderedIds)
                            }
                        )
                        PlaylistRow(
                            playlist = playlist,
                            coverModel = playlistCoverModels[playlist.id],
                            selectionMode = selectionMode,
                            selected = playlist.id in selectedPlaylistIds,
                            onClick = {
                                if (selectionMode) {
                                    togglePlaylistSelection(playlist)
                                } else {
                                    onPlaylistClick(playlist.id)
                                }
                            },
                            onLongClick = {
                                if (selectionMode) {
                                    togglePlaylistSelection(playlist)
                                } else {
                                    selectionMode = true
                                    selectedPlaylistIds = selectedPlaylistIds + playlist.id
                                }
                            },
                            onDelete = if (selectionMode) null else { { playlistPendingDelete = playlist } },
                            trailingContent = if (reorderEnabled) {
                                {
                                    PlaylistDragHandle(
                                        isDragging = isDragging,
                                        modifier = Modifier
                                            .then(dragHandleModifier)
                                    )
                                }
                            } else null
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(150.dp)) }
        }
    }

    if (showCreateDialog) {
        CreatePlaylistDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name ->
                mainViewModel.createPlaylist(name)
                showCreateDialog = false
            }
        )
    }
    if (showImportModeSheet) {
        ImportPlaylistModeSheet(
            count = pendingImportUris.size,
            onDismiss = {
                showImportModeSheet = false
                pendingImportUris = emptyList()
            },
            onModeSelected = ::importPendingPlaylists
        )
    }
    playlistPendingDelete?.let { playlist ->
        ConfirmDangerDialog(
            show = true,
            title = stringResource(R.string.playlist_delete_title),
            message = stringResource(R.string.playlist_delete_message, playlist.name),
            confirmText = stringResource(R.string.common_delete),
            onDismiss = { playlistPendingDelete = null },
            onConfirm = {
                mainViewModel.deletePlaylist(playlist.id)
                playlistPendingDelete = null
            }
        )
    }
    if (playlistsPendingDelete.isNotEmpty()) {
        ConfirmDangerDialog(
            show = true,
            title = stringResource(R.string.playlist_delete_title),
            message = stringResource(R.string.playlist_delete_multiple_message, playlistsPendingDelete.size),
            confirmText = stringResource(R.string.common_delete),
            onDismiss = { playlistsPendingDelete = emptyList() },
            onConfirm = {
                mainViewModel.deletePlaylists(playlistsPendingDelete.mapTo(mutableSetOf()) { it.id })
                playlistsPendingDelete = emptyList()
                finishSelectionMode()
            }
        )
    }
    if (showExportAllFormatSheet) {
        ExportPlaylistFormatSheet(
            onDismiss = { showExportAllFormatSheet = false },
            onFormatSelected = { format ->
                showExportAllFormatSheet = false
                pendingExportAllFormat = format
                exportAllFolderLauncher.launch(null)
            }
        )
    }
}
