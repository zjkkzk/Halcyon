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
import com.ella.music.ui.components.DoubleTapScrollOverlay
import com.ella.music.ui.components.EllaSearchBar
import com.ella.music.ui.components.EllaMiuixBottomSheet
import com.ella.music.ui.components.EllaMiuixSheetActions
import com.ella.music.ui.components.EllaMiuixTextField
import com.ella.music.ui.components.LazyGridScrollIndicator
import com.ella.music.ui.components.LocateCurrentSongFloatingButton
import com.ella.music.ui.components.SongItem
import com.ella.music.ui.components.SongMoreActionHost
import com.ella.music.ui.components.ellaPageBackground
import com.ella.music.ui.components.requestPinnedEllaShortcut
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    val songs by mainViewModel.songs.collectAsState()
    val items = remember(type, songs) { mainViewModel.getMetadataCategoryItems(type) }
    var sortExpanded by remember { mutableStateOf(false) }
    val sortIndexFlow = remember(type) { mainViewModel.settingsManager.metadataCategorySortIndex(type) }
    val sortIndex by sortIndexFlow.collectAsState(initial = 0)
    val availableSortModes = remember(type) { MetadataCategorySortMode.entries.filter { it.availableFor(type) } }
    val sortMode = availableSortModes.getOrElse(sortIndex) { MetadataCategorySortMode.Name }
    val sortedItems = remember(items, sortMode) { items.sortedForCategory(sortMode) }
    var searchExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var categoryMenuItem by remember { mutableStateOf<MetadataCategoryItem?>(null) }
    val displayedItems = remember(sortedItems, searchQuery, type) {
        val query = searchQuery.trim()
        if (query.isBlank()) {
            sortedItems
        } else {
            sortedItems.filter { it.matchesCategorySearch(query, type) }
        }
    }
    val representativeSongsByName = remember(type, items, songs) {
        items.associate { item ->
            item.name to mainViewModel.getSongsForMetadataCategory(type, item.name).firstOrNull()
        }
    }
    val albumArtUrisByName = remember(items) {
        items.associate { item ->
            item.name to item.coverAlbumIds.firstOrNull()?.let(mainViewModel::getAlbumArtUri)
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
    val savedCategoryScroll = remember(type) {
        LibrarySortUiState.metadataCategoryScrollPositions[type] ?: (0 to 0)
    }
    val gridState = rememberLazyGridState(
        initialFirstVisibleItemIndex = savedCategoryScroll.first,
        initialFirstVisibleItemScrollOffset = savedCategoryScroll.second
    )
    val scope = rememberCoroutineScope()
    var skipInitialCategoryReset by remember(type) { mutableStateOf(true) }
    LaunchedEffect(type, sortMode, searchQuery, safeGridColumns) {
        if (skipInitialCategoryReset) {
            skipInitialCategoryReset = false
        } else {
            gridState.scrollToItem(0)
        }
    }
    LaunchedEffect(type, gridState) {
        snapshotFlow { gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset }
            .collect { position ->
                LibrarySortUiState.metadataCategoryScrollPositions[type] = position
            }
    }
    BackHandler(enabled = sortExpanded || searchExpanded) {
        when {
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
            .background(pageBackground)
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
                    IconButton(onClick = { sortExpanded = !sortExpanded }) {
                        Icon(
                            imageVector = MiuixIcons.Regular.Sort,
                            contentDescription = stringResource(R.string.common_sort),
                            tint = MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier.size(24.dp)
                        )
                    }
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
                                scope.launch { gridState.scrollToItem(0) }
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
            Box(modifier = Modifier.fillMaxSize()) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(safeGridColumns),
                    state = gridState,
                    contentPadding = PaddingValues(bottom = 120.dp)
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
                        MetadataCategoryCard(
                            type = type,
                            item = item,
                            sortMode = sortMode,
                            albumArtUri = albumArtUrisByName[item.name],
                            representativeSong = representativeSongsByName[item.name],
                            loadCoverArt = if (type.prefersEmbeddedCategoryCardCover()) mainViewModel::getAlbumCoverArtBitmap else null,
                            onClick = { onCategoryClick(item.name) },
                            onLongClick = { categoryMenuItem = item }
                        )
                    }
                }
                if (displayedItems.size > 30) {
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
                CategorySheetItem(stringResource(R.string.song_more_play_next)) {
                    val selectedSongs = mainViewModel.getSongsForMetadataCategory(type, item.name)
                    playerViewModel.playNext(selectedSongs)
                    Toast.makeText(context, context.getString(R.string.song_more_added_to_play_next), Toast.LENGTH_SHORT).show()
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
                CategorySheetItem(stringResource(R.string.common_cancel)) {
                    categoryMenuItem = null
                }
            }
        }
    }
}
