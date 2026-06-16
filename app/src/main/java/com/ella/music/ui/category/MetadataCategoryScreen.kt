package com.ella.music.ui.category

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.data.LibraryAlbumAggregator
import com.ella.music.data.exception.WritePermissionRequiredException
import com.ella.music.data.model.Album
import com.ella.music.data.model.FAVORITES_PLAYLIST_ID
import com.ella.music.data.model.Song
import com.ella.music.data.model.UserPlaylist
import com.ella.music.data.model.playlistIdentityKey
import com.ella.music.ui.LibrarySortUiState
import com.ella.music.viewmodel.MainViewModel
import com.ella.music.viewmodel.MetadataCategoryItem
import com.ella.music.viewmodel.PlayerViewModel
import com.ella.music.ui.components.ConfirmDangerDialog
import com.ella.music.ui.components.AddToPlaylistSheet
import com.ella.music.ui.components.CreatePlaylistAndAddSheet
import com.ella.music.ui.components.rememberSongDeleteRequester
import com.ella.music.ui.components.shareLocalSongs
import com.ella.music.ui.components.DoubleTapScrollOverlay
import com.ella.music.ui.components.EllaSearchBar
import com.ella.music.ui.components.EllaMiuixBottomSheet
import com.ella.music.ui.components.EllaMiuixSheetActions
import com.ella.music.ui.components.EllaMiuixTextField
import com.ella.music.ui.components.FastIndexBar
import com.ella.music.ui.components.LazyGridScrollIndicator
import com.ella.music.ui.components.LocateCurrentSongFloatingButton
import com.ella.music.ui.components.SideIndexListEndPadding
import com.ella.music.ui.components.SongItem
import com.ella.music.ui.components.SongMoreActionHost
import com.ella.music.ui.components.SortDropdownItem
import com.ella.music.ui.components.SortDropdownMenu
import com.ella.music.ui.components.ellaPageBackground
import com.ella.music.ui.components.wallpaperContentOverlayColor
import com.ella.music.ui.components.requestPinnedEllaShortcut
import com.ella.music.ui.folder.FolderBlockDialog
import com.ella.music.ui.folder.normalizeFolderPath
import com.ella.music.ui.folder.toFolderSettingList
import com.ella.music.ui.navigation.Screen
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import com.ella.music.ui.components.EllaSmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Search
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.SelectAll
import top.yukonga.miuix.kmp.icon.extended.Sort
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun MetadataCategoryScreen(
    type: String,
    mainViewModel: MainViewModel,
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit,
    onCategoryClick: (String) -> Unit
) {
    val context = LocalContext.current
    val requestDeleteSongs = rememberSongDeleteRequester(mainViewModel)
    val songs by mainViewModel.songs.collectAsState()
    val items by produceState(emptyList<MetadataCategoryItem>(), type, songs) {
        value = withContext(Dispatchers.Default) { mainViewModel.getMetadataCategoryItems(type) }
    }
    var sortExpanded by remember { mutableStateOf(false) }
    val sortIndexFlow = remember(type) { mainViewModel.settingsManager.metadataCategorySortIndex(type) }
    val sortIndex by sortIndexFlow.collectAsState(initial = 0)
    val availableSortModes = remember(type) { MetadataCategorySortMode.entries.filter { it.availableFor(type) } }
    val sortMode = availableSortModes.getOrElse(sortIndex) { MetadataCategorySortMode.Name }
    val sortedItems = remember(items, sortMode) { items.sortedForCategory(sortMode) }
    val playlists by mainViewModel.playlists.collectAsState()
    val scanExcludeFolders by mainViewModel.settingsManager.scanExcludeFolders.collectAsState(initial = "")
    val blockedFolders = remember(scanExcludeFolders) { scanExcludeFolders.toFolderSettingList() }
    val pinnedCategoryKeys by mainViewModel.settingsManager
        .pinnedKeysFlow("category:$type")
        .collectAsState(initial = emptyList())
    val pinnedOrderedItems = remember(sortedItems, pinnedCategoryKeys) {
        if (pinnedCategoryKeys.isEmpty()) {
            sortedItems
        } else {
            val pinnedRank = pinnedCategoryKeys.withIndex().associate { it.value to it.index }
            val pinnedSet = pinnedRank.keys
            val pinned = sortedItems
                .filter { it.name in pinnedSet }
                .sortedBy { pinnedRank[it.name] ?: Int.MAX_VALUE }
            pinned + sortedItems.filterNot { it.name in pinnedSet }
        }
    }
    var searchExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var categoryMenuItem by remember { mutableStateOf<MetadataCategoryItem?>(null) }
    var folderToBlock by remember { mutableStateOf<String?>(null) }
    var playlistPickerSongs by remember { mutableStateOf<List<Song>?>(null) }
    var createPlaylistSongs by remember { mutableStateOf<List<Song>?>(null) }
    var pendingDeleteSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    val displayedItems = remember(pinnedOrderedItems, searchQuery, type) {
        val query = searchQuery.trim()
        if (query.isBlank()) {
            pinnedOrderedItems
        } else {
            pinnedOrderedItems.filter { it.matchesCategorySearch(query, type) }
        }
    }
    val gridColumns by mainViewModel.settingsManager.categoryGridColumns.collectAsState(initial = 2)
    val configuration = LocalConfiguration.current
    val safeGridColumns = if (type.usesSingleColumnCategory()) {
        1
    } else if (configuration.smallestScreenWidthDp >= 600) {
        gridColumns.coerceIn(5, 8)
    } else {
        gridColumns.coerceIn(1, 4)
    }
    val pageBackground = ellaPageBackground()
    val categoryScrollKey = remember(type, sortMode) { "$type\u0000${sortMode.name}" }
    val savedCategoryScroll = remember(categoryScrollKey) {
        LibrarySortUiState.metadataCategoryScrollPositions[categoryScrollKey] ?: (0 to 0)
    }
    val gridState = rememberLazyGridState(
        initialFirstVisibleItemIndex = savedCategoryScroll.first,
        initialFirstVisibleItemScrollOffset = savedCategoryScroll.second
    )
    val scope = rememberCoroutineScope()
    LaunchedEffect(categoryScrollKey, gridState) {
        snapshotFlow { gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset }
            .collect { position ->
                LibrarySortUiState.metadataCategoryScrollPositions[categoryScrollKey] = position
            }
    }
    BackHandler(enabled = sortExpanded || searchExpanded || folderToBlock != null) {
        when {
            folderToBlock != null -> folderToBlock = null
            searchExpanded -> {
                searchExpanded = false
                searchQuery = ""
            }
            sortExpanded -> sortExpanded = false
        }
    }

    val overlayColor = wallpaperContentOverlayColor()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(pageBackground)
    ) {
        if (overlayColor.alpha > 0f) {
            Box(modifier = Modifier.fillMaxSize().background(overlayColor))
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
        ) {
        Box {
            EllaSmallTopAppBar(
                title = type.categoryTitle(),
                color = pageBackground,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = MiuixIcons.Regular.Back,
                            contentDescription = stringResource(R.string.common_back),
                            tint = MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                },
                actions = {
                    SortDropdownMenu(
                        items = availableSortModes.map { mode ->
                            SortDropdownItem(
                                text = mode.displayLabel(type),
                                selected = sortMode == mode,
                                onClick = {
                                    scope.launch { mainViewModel.settingsManager.setMetadataCategorySortIndex(type, availableSortModes.indexOf(mode)) }
                                }
                            )
                        }
                    )
                    IconButton(onClick = {
                        searchExpanded = !searchExpanded
                        if (!searchExpanded) searchQuery = ""
                    }) {
                        Icon(
                            imageVector = MiuixIcons.Basic.Search,
                            contentDescription = stringResource(R.string.common_search),
                            tint = MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            )
            DoubleTapScrollOverlay(
                onDoubleTap = { scope.launch { gridState.animateScrollToItem(0) } },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                endPadding = 112.dp
            )
        }

        AnimatedVisibility(
            visible = searchExpanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            EllaSearchBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                onSearch = { searchExpanded = false },
                placeholder = stringResource(R.string.category_search_placeholder, type.categoryTitle()),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }

        AnimatedVisibility(
            visible = sortExpanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                availableSortModes.forEach { mode ->
                    Text(
                        text = mode.displayLabel(type),
                        fontSize = 14.sp,
                        fontWeight = if (sortMode == mode) FontWeight.Bold else FontWeight.Normal,
                        color = if (sortMode == mode) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                sortExpanded = false
                                scope.launch { mainViewModel.settingsManager.setMetadataCategorySortIndex(type, availableSortModes.indexOf(mode)) }
                            }
                            .padding(vertical = 10.dp)
                    )
                }
            }
        }

        if (displayedItems.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = if (searchQuery.isBlank()) stringResource(R.string.category_empty_hint, type.categoryTitle()) else stringResource(R.string.category_no_match, type.categoryTitle()),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    fontSize = 14.sp
                )
            }
        } else {
            // A-Z index bar for the single-column person categories (composer/lyricist) when
            // sorted by name, mirroring the artists list.
            val showCategoryIndexBar = (type == "composer" || type == "lyricist" || type == "folder") &&
                sortMode == MetadataCategorySortMode.Name &&
                displayedItems.size > 30
            val categoryIndexLetters = remember(displayedItems, showCategoryIndexBar) {
                if (showCategoryIndexBar) displayedItems.map { it.categoryIndexLetter() } else emptyList()
            }
            val categoryIndexTargets = remember(categoryIndexLetters) {
                buildMap {
                    categoryIndexLetters.forEachIndexed { index, letter -> putIfAbsent(letter, index) }
                }
            }
            val showCategorySideIndex = displayedItems.size > 30
            Box(modifier = Modifier.fillMaxSize()) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(safeGridColumns),
                    state = gridState,
                    contentPadding = PaddingValues(
                        end = if (showCategorySideIndex) SideIndexListEndPadding else 0.dp,
                        bottom = 120.dp
                    )
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = "${type.categoryCountSummary(displayedItems.size)} · ${sortMode.displayLabel(type)}",
                            fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                    items(displayedItems, key = { it.name }) { item ->
                        val albumArtUri = remember(item.coverAlbumIds) {
                            item.coverAlbumIds.firstOrNull()?.let(mainViewModel::getAlbumArtUri)
                        }
                        MetadataCategoryCard(
                            type = type,
                            item = item,
                            sortMode = sortMode,
                            albumArtUri = albumArtUri,
                            representativeSong = item.representativeSong,
                            loadCoverArt = if (type.prefersEmbeddedCategoryCardCover()) mainViewModel::getAlbumCoverArtBitmap else null,
                            isPinned = item.name in pinnedCategoryKeys,
                            onClick = { onCategoryClick(item.name) },
                            onLongClick = { categoryMenuItem = item }
                        )
                    }
                }
                if (showCategoryIndexBar) {
                    FastIndexBar(
                        letters = categoryIndexLetters,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .padding(end = 0.dp),
                        onLetterClick = { letter ->
                            val index = categoryIndexTargets[letter]
                            if (index != null) {
                                // +1 to skip the count-summary header item.
                                scope.launch { gridState.scrollToItem(index + 1) }
                            }
                        }
                    )
                } else if (showCategorySideIndex) {
                    LazyGridScrollIndicator(
                        state = gridState,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                    )
                }
            }
        }
    }

    categoryMenuItem?.let { item ->
        EllaMiuixBottomSheet(
            show = true,
            enableNestedScroll = false,
            title = item.name.substringAfterLast('/').ifBlank { item.name },
            onDismissRequest = { categoryMenuItem = null }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(MiuixTheme.colorScheme.background.copy(alpha = 0.98f))
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val isPinned = item.name in pinnedCategoryKeys
                CategorySheetItem(
                    stringResource(if (isPinned) R.string.common_unpin else R.string.common_pin_to_top)
                ) {
                    scope.launch {
                        mainViewModel.settingsManager.setPinned("category:$type", item.name, !isPinned)
                    }
                    categoryMenuItem = null
                }
                if (type == "folder") {
                    CategorySheetItem(stringResource(R.string.folder_block_folder)) {
                        folderToBlock = item.name.normalizeFolderPath()
                        categoryMenuItem = null
                    }
                }
                CategorySheetItem(stringResource(R.string.common_share)) {
                    val selectedSongs = mainViewModel.getSongsForMetadataCategory(type, item.name)
                    shareLocalSongs(context, selectedSongs)
                    categoryMenuItem = null
                }
                CategorySheetItem(stringResource(R.string.song_more_add_to_playlist)) {
                    scope.launch {
                        playlistPickerSongs = mainViewModel.detailSortedSongsForMetadataCategory(type, item.name)
                    }
                    categoryMenuItem = null
                }
                CategorySheetItem(stringResource(R.string.common_add_to_queue)) {
                    scope.launch {
                        val selectedSongs = mainViewModel.detailSortedSongsForMetadataCategory(type, item.name)
                        playerViewModel.addToPlaylist(selectedSongs)
                        Toast.makeText(context, context.getString(R.string.song_more_added_to_queue), Toast.LENGTH_SHORT).show()
                    }
                    categoryMenuItem = null
                }
                CategorySheetItem(stringResource(R.string.song_more_play_next)) {
                    scope.launch {
                        val selectedSongs = mainViewModel.detailSortedSongsForMetadataCategory(type, item.name)
                        playerViewModel.playNext(selectedSongs)
                        Toast.makeText(context, context.getString(R.string.song_more_added_to_play_next), Toast.LENGTH_SHORT).show()
                    }
                    categoryMenuItem = null
                }
                CategorySheetItem(stringResource(R.string.common_add_desktop_shortcut)) {
                    val ok = requestPinnedEllaShortcut(
                        context = context,
                        id = "category_${type}_${item.name}",
                        label = item.name,
                        route = Screen.MetadataCategoryDetail.createRoute(type, item.name)
                    )
                    Toast.makeText(
                        context,
                        if (ok) context.getString(R.string.playlist_shortcut_requested, item.name) else context.getString(R.string.playlist_shortcut_unsupported),
                        Toast.LENGTH_SHORT
                    ).show()
                    categoryMenuItem = null
                }
                if (type != "folder") {
                    CategorySheetItem(stringResource(R.string.song_more_delete_permanently)) {
                        pendingDeleteSongs = mainViewModel.getSongsForMetadataCategory(type, item.name)
                        categoryMenuItem = null
                    }
                }
                CategorySheetItem(stringResource(R.string.common_cancel)) {
                    categoryMenuItem = null
                }
            }
        }
    }

    folderToBlock?.let { folderPath ->
        FolderBlockDialog(
            folderPath = folderPath,
            onDismiss = { folderToBlock = null },
            onBlock = {
                scope.launch {
                    val normalizedPath = folderPath.normalizeFolderPath()
                    mainViewModel.settingsManager.setScanExcludeFolders(
                        (blockedFolders + normalizedPath)
                            .distinctBy { it.normalizeFolderPath().lowercase(Locale.ROOT) }
                            .joinToString("；")
                    )
                    mainViewModel.scanMusic()
                }
                folderToBlock = null
            }
        )
    }

    playlistPickerSongs?.let { songs ->
        EllaMiuixBottomSheet(
            show = true,
            enableNestedScroll = false,
            title = stringResource(R.string.song_more_add_to_playlist_title),
            onDismissRequest = { playlistPickerSongs = null }
        ) {
            AddToPlaylistSheet(
                playlists = playlists,
                songCount = songs.size,
                onDismiss = { playlistPickerSongs = null },
                onCreatePlaylist = {
                    createPlaylistSongs = songs
                    playlistPickerSongs = null
                },
                onPlaylistsConfirm = { selectedPlaylists, appendToEnd ->
                    selectedPlaylists.forEach { playlist ->
                        mainViewModel.addSongsToPlaylist(playlist.id, songs, appendToEnd)
                    }
                    Toast.makeText(
                        context,
                        context.getString(R.string.player_added_to_playlists, selectedPlaylists.size),
                        Toast.LENGTH_SHORT
                    ).show()
                    playlistPickerSongs = null
                }
            )
        }
    }

    createPlaylistSongs?.let { songs ->
        CreatePlaylistAndAddSheet(
            onDismiss = { createPlaylistSongs = null },
            onCreate = { name ->
                mainViewModel.createPlaylist(name) { playlist ->
                    if (playlist != null) mainViewModel.addSongsToPlaylist(playlist.id, songs)
                }
                createPlaylistSongs = null
            }
        )
    }

    if (pendingDeleteSongs.isNotEmpty()) {
        ConfirmDangerDialog(
            show = true,
            title = stringResource(R.string.song_more_delete_song_title),
            message = stringResource(R.string.library_delete_selected_message, pendingDeleteSongs.size),
            confirmText = stringResource(R.string.song_more_delete_permanently),
            onDismiss = { pendingDeleteSongs = emptyList() },
            onConfirm = {
                requestDeleteSongs(pendingDeleteSongs)
                pendingDeleteSongs = emptyList()
            }
        )
    }
}
}

/**
 * Gathers the songs for a metadata category and orders them using that category type's
 * persisted DETAIL-page song-sort setting (matching MetadataCategoryDetailScreen).
 */
private suspend fun MainViewModel.detailSortedSongsForMetadataCategory(
    type: String,
    name: String
): List<Song> {
    val index = settingsManager.metadataCategoryDetailSongSortIndex(type).first()
    val mode = MetadataDetailSongSortMode.entries.getOrElse(index) { MetadataDetailSongSortMode.AlbumTrack }
        .let { resolved ->
            if (type == "folder" && resolved == MetadataDetailSongSortMode.AlbumTrack) {
                MetadataDetailSongSortMode.Title
            } else {
                resolved
            }
        }
    return getSongsForMetadataCategory(type, name).sortedForMetadataDetail(mode)
}
