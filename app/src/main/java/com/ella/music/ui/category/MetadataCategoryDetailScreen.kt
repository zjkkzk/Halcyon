package com.ella.music.ui.category

import android.app.Activity
import android.graphics.Bitmap
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
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
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
import com.ella.music.ui.components.FolderOutlineIcon
import com.ella.music.ui.components.LazyGridScrollIndicator
import com.ella.music.ui.components.LocateCurrentSongFloatingButton
import com.ella.music.ui.components.DefaultAlbumCover
import com.ella.music.ui.components.SafeCoverImage
import com.ella.music.ui.components.SongItem
import com.ella.music.ui.components.SongMoreActionHost
import com.ella.music.ui.components.ArtworkUsage
import com.ella.music.ui.components.ellaPageBackground
import com.ella.music.ui.components.rememberSongArtworkState
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
import top.yukonga.miuix.kmp.icon.extended.Music
import top.yukonga.miuix.kmp.icon.extended.SelectAll
import top.yukonga.miuix.kmp.icon.extended.Sort
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@Composable
fun MetadataCategoryDetailScreen(
    type: String,
    name: String,
    mainViewModel: MainViewModel,
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit,
    onAlbumClick: (Long) -> Unit = {},
    onArtistClick: (String) -> Unit = {},
    onMetadataCategoryClick: (String, String) -> Unit = { _, _ -> },
    onNavigateToPlayer: () -> Unit
) {
    val context = LocalContext.current
    val librarySongs by mainViewModel.songs.collectAsState()
    val libraryAlbums by mainViewModel.albums.collectAsState()
    val playlists by mainViewModel.playlists.collectAsState()
    val currentSong by playerViewModel.currentSong.collectAsState()
    val favoriteSongKeys by playerViewModel.favoriteSongKeys.collectAsState()
    val locateCurrentSongRequest by playerViewModel.locateCurrentSongRequest.collectAsState()
    val openPlayerOnPlay by mainViewModel.settingsManager.openPlayerOnPlay.collectAsState(initial = false)
    val showPlayNextInLists by mainViewModel.settingsManager.showPlayNextInLists.collectAsState(initial = false)
    val songs = remember(type, name, librarySongs) { mainViewModel.getSongsForMetadataCategory(type, name) }
    var sortExpanded by remember { mutableStateOf(false) }
    val detailSongSortIndexFlow = remember(type) { mainViewModel.settingsManager.metadataCategoryDetailSongSortIndex(type) }
    val detailAlbumSortIndexFlow = remember(type) { mainViewModel.settingsManager.metadataCategoryDetailAlbumSortIndex(type) }
    val sortIndex by detailSongSortIndexFlow.collectAsState(initial = 0)
    val albumSortIndex by detailAlbumSortIndexFlow.collectAsState(initial = 0)
    val sortMode = MetadataDetailSongSortMode.entries.getOrElse(sortIndex) { MetadataDetailSongSortMode.AlbumTrack }
    val albumSortMode = MetadataDetailAlbumSortMode.entries.getOrElse(albumSortIndex) { MetadataDetailAlbumSortMode.YearAsc }
    var selectedTab by rememberSaveable(type, name) { mutableStateOf(MetadataDetailTab.Songs) }
    var actionSong by remember { mutableStateOf<com.ella.music.data.model.Song?>(null) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var playlistPickerSongs by remember { mutableStateOf<List<Song>?>(null) }
    var createPlaylistSongs by remember { mutableStateOf<List<Song>?>(null) }
    var pendingDeleteSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var pendingSystemDeleteSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    val sortedSongs = remember(songs, sortMode) { songs.sortedForMetadataDetail(sortMode) }
    val showAlbumTab = type == "genre" || type == "year" || type == "composer" || type == "lyricist"
    val detailAlbums = remember(songs, libraryAlbums) {
        LibraryAlbumAggregator.toAlbumsForSongs(
            songs = songs,
            libraryAlbums = libraryAlbums,
            unknownAlbumName = context.getString(R.string.player_unknown_album)
        )
    }
    val albumDurations = remember(songs) {
        LibraryAlbumAggregator.durationsByAlbumIdentity(songs)
    }
    val sortedAlbums = remember(detailAlbums, albumSortMode, albumDurations) {
        detailAlbums.sortedForMetadataAlbumDetail(albumSortMode, albumDurations)
    }
    val albumArtUrisByAlbumId = remember(sortedAlbums) {
        sortedAlbums.associate { album -> album.id to mainViewModel.getAlbumArtUri(album.artAlbumId) }
    }
    val albumArtUrisBySongId = remember(sortedSongs) {
        sortedSongs.associate { song -> song.id to mainViewModel.getAlbumArtUri(song.albumId) }
    }
    val hasSameNameArtist = remember(type, name, librarySongs) {
        (type == "composer" || type == "lyricist") && mainViewModel.getSongsForArtist(name).isNotEmpty()
    }
    val hasSameNameComposer = remember(type, name, librarySongs) {
        type == "lyricist" && mainViewModel.getSongsForMetadataCategory("composer", name).isNotEmpty()
    }
    val hasSameNameLyricist = remember(type, name, librarySongs) {
        type == "composer" && mainViewModel.getSongsForMetadataCategory("lyricist", name).isNotEmpty()
    }
    val pageBackground = ellaPageBackground()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val deleteRequestLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val songsToDelete = pendingSystemDeleteSongs
        pendingSystemDeleteSongs = emptyList()
        if (result.resultCode == Activity.RESULT_OK && songsToDelete.isNotEmpty()) {
            mainViewModel.removeSongsFromLibrary(songsToDelete)
            Toast.makeText(context, context.getString(R.string.library_deleted_songs, songsToDelete.size), Toast.LENGTH_SHORT).show()
            selectedIds = emptySet()
            selectionMode = false
        } else if (songsToDelete.isNotEmpty()) {
            Toast.makeText(context, context.getString(R.string.library_delete_cancelled), Toast.LENGTH_SHORT).show()
        }
    }
    fun deleteSelectedSongs(songsToDelete: List<Song>) {
        if (songsToDelete.isEmpty()) return
        scope.launch {
            val result = mainViewModel.deleteSongsResult(songsToDelete)
            if (result.isSuccess) {
                Toast.makeText(context, context.getString(R.string.library_deleted_songs, songsToDelete.size), Toast.LENGTH_SHORT).show()
                selectedIds = emptySet()
                selectionMode = false
                return@launch
            }
            val error = result.exceptionOrNull()
            if (error is WritePermissionRequiredException) {
                pendingSystemDeleteSongs = songsToDelete
                deleteRequestLauncher.launch(IntentSenderRequest.Builder(error.intentSender).build())
            } else {
                Toast.makeText(context, error?.localizedMessage ?: context.getString(R.string.song_more_metadata_save_failed), Toast.LENGTH_SHORT).show()
            }
        }
    }
    val currentSongItemIndex = remember(sortedSongs, currentSong?.id, selectedTab, selectionMode) {
        if (selectedTab != MetadataDetailTab.Songs || selectionMode) return@remember -1
        sortedSongs.indexOfFirst { it.id == currentSong?.id }
            .takeIf { it >= 0 }
            ?.plus(if (showAlbumTab) 2 else 1)
            ?: -1
    }
    BackHandler(enabled = selectionMode || sortExpanded) {
        when {
            selectionMode -> {
                selectedIds = emptySet()
                selectionMode = false
            }
            sortExpanded -> sortExpanded = false
        }
    }
    LaunchedEffect(selectedTab) {
        if (selectedTab != MetadataDetailTab.Songs && selectionMode) {
            selectedIds = emptySet()
            selectionMode = false
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
                title = name.ifBlank { type.categoryTitle() },
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
                    if (selectionMode) {
                        IconButton(onClick = {
                            val selectedSongs = sortedSongs.filter { it.id in selectedIds }
                            if (selectedSongs.isEmpty()) {
                                Toast.makeText(context, context.getString(R.string.library_select_songs_first), Toast.LENGTH_SHORT).show()
                            } else {
                                playlistPickerSongs = selectedSongs
                            }
                        }) {
                            Icon(
                                imageVector = MiuixIcons.Regular.Add,
                                contentDescription = stringResource(R.string.song_more_add_to_playlist),
                                tint = MiuixTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        IconButton(onClick = {
                            val selectedSongs = sortedSongs.filter { it.id in selectedIds }
                            if (selectedSongs.isEmpty()) {
                                Toast.makeText(context, context.getString(R.string.library_select_songs_first), Toast.LENGTH_SHORT).show()
                            } else {
                                pendingDeleteSongs = selectedSongs
                            }
                        }) {
                            Icon(
                                imageVector = MiuixIcons.Regular.Delete,
                                contentDescription = stringResource(R.string.common_delete),
                                tint = Color(0xFFE5484D),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    } else {
                        if (selectedTab == MetadataDetailTab.Songs) {
                            IconButton(onClick = {
                                selectionMode = true
                                selectedIds = sortedSongs.map { it.id }.toSet()
                            }) {
                                Icon(
                                    imageVector = MiuixIcons.Regular.SelectAll,
                                    contentDescription = stringResource(R.string.common_multi_select),
                                    tint = MiuixTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        IconButton(onClick = { sortExpanded = !sortExpanded }) {
                            Icon(
                                imageVector = MiuixIcons.Regular.Sort,
                                contentDescription = stringResource(R.string.common_sort),
                                tint = MiuixTheme.colorScheme.onSurface,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            )
            DoubleTapScrollOverlay(
                onDoubleTap = { scope.launch { listState.animateScrollToItem(0) } },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                endPadding = 128.dp
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
                if (selectedTab == MetadataDetailTab.Albums) {
                    MetadataDetailAlbumSortMode.entries.forEach { mode ->
                        Text(
                            text = mode.label(),
                            fontSize = 14.sp,
                            fontWeight = if (albumSortMode == mode) FontWeight.Bold else FontWeight.Normal,
                            color = if (albumSortMode == mode) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    sortExpanded = false
                                    scope.launch { mainViewModel.settingsManager.setMetadataCategoryDetailAlbumSortIndex(type, mode.ordinal) }
                                    scope.launch { listState.animateScrollToItem(0) }
                                }
                                .padding(vertical = 10.dp)
                        )
                    }
                } else {
                    MetadataDetailSongSortMode.entries.forEach { mode ->
                    Text(
                        text = mode.label(),
                        fontSize = 14.sp,
                        fontWeight = if (sortMode == mode) FontWeight.Bold else FontWeight.Normal,
                        color = if (sortMode == mode) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                sortExpanded = false
                                scope.launch { mainViewModel.settingsManager.setMetadataCategoryDetailSongSortIndex(type, mode.ordinal) }
                                scope.launch { listState.animateScrollToItem(0) }
                            }
                            .padding(vertical = 10.dp)
                    )
                    }
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(bottom = 120.dp)
            ) {
                item {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        val summaryText = if (selectionMode) {
                            stringResource(R.string.category_selected_count, selectedIds.size)
                        } else if (selectedTab == MetadataDetailTab.Albums) {
                            stringResource(R.string.category_album_summary, sortedAlbums.size, type.categoryTitle(), albumSortMode.label())
                        } else {
                            stringResource(R.string.category_song_summary, sortedSongs.size, type.categoryTitle(), sortMode.label())
                        }
                        if (type == "composer" || type == "lyricist") {
                            Row(
                                modifier = Modifier.padding(bottom = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (hasSameNameArtist) {
                                    MetadataDetailLinkChip(
                                        text = stringResource(R.string.category_artist_page),
                                        onClick = { onArtistClick(name) }
                                    )
                                }
                                if (hasSameNameComposer) {
                                    MetadataDetailLinkChip(
                                        text = stringResource(R.string.category_composer_page),
                                        onClick = { onMetadataCategoryClick("composer", name) }
                                    )
                                }
                                if (hasSameNameLyricist) {
                                    MetadataDetailLinkChip(
                                        text = stringResource(R.string.category_lyricist_page),
                                        onClick = { onMetadataCategoryClick("lyricist", name) }
                                    )
                                }
                            }
                        }
                        if (showAlbumTab) {
                            Row(
                                modifier = Modifier.padding(bottom = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                MetadataDetailTab.entries.forEach { tab ->
                                    Text(
                                        text = tab.label(),
                                        fontSize = 13.sp,
                                        fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Normal,
                                        color = if (selectedTab == tab) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(999.dp))
                                            .background(
                                                if (selectedTab == tab) MiuixTheme.colorScheme.primary.copy(alpha = 0.12f)
                                                else Color.Transparent
                                            )
                                            .clickable { selectedTab = tab }
                                            .padding(horizontal = 12.dp, vertical = 7.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = summaryText,
                            fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )
                    }
                }
                if (selectedTab == MetadataDetailTab.Albums) {
                    items(sortedAlbums, key = { it.id }) { album ->
                        MetadataAlbumRow(
                            album = album,
                            duration = albumDurations[album.id] ?: 0L,
                            albumArtUri = albumArtUrisByAlbumId[album.id],
                            onClick = { onAlbumClick(album.id) }
                        )
                    }
                } else {
                    itemsIndexed(sortedSongs, key = { _, song -> song.id }) { index, song ->
                        val selected = song.id in selectedIds
                        SongItem(
                            song = song,
                            isCurrent = currentSong?.id == song.id,
                            albumArtUri = albumArtUrisBySongId[song.id],
                            loadCoverArt = mainViewModel::getCoverArtBitmap,
                            loadAudioInfo = mainViewModel::getAudioInfo,
                            showPlayNextInLists = showPlayNextInLists,
                            isFavorite = song.playlistIdentityKey() in favoriteSongKeys,
                            loadSongRating = mainViewModel::getSongRating,
                            selectionMode = selectionMode,
                            selected = selected,
                            onLongClick = {
                                selectionMode = true
                                selectedIds = selectedIds + song.id
                            },
                            onClick = {
                                if (selectionMode) {
                                    selectedIds = if (selected) selectedIds - song.id else selectedIds + song.id
                                } else {
                                    playerViewModel.setPlaylist(sortedSongs, index)
                                    if (openPlayerOnPlay) onNavigateToPlayer()
                                }
                            },
                            onPlayNext = { playerViewModel.playNext(song) },
                            onMore = { actionSong = song }
                        )
                    }
                }
                item { Spacer(modifier = Modifier.height(24.dp)) }
            }

            LocateCurrentSongFloatingButton(
                listState = listState,
                currentItemIndex = currentSongItemIndex,
                locateRequest = locateCurrentSongRequest,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 22.dp, bottom = 118.dp)
            )

            SongMoreActionHost(
                actionSong = actionSong,
                mainViewModel = mainViewModel,
                playerViewModel = playerViewModel,
                onDismissAction = { actionSong = null },
                onNavigateToAlbum = onAlbumClick,
                onNavigateToArtist = onArtistClick
            )

            playlistPickerSongs?.let { songsToAdd ->
                EllaMiuixBottomSheet(
                    show = true,
                    enableNestedScroll = false,
                    title = stringResource(R.string.song_more_add_to_playlist),
                    onDismissRequest = { playlistPickerSongs = null }
                ) {
                    AddToPlaylistSheet(
                        playlists = playlists
                            .sortedWith(compareByDescending<com.ella.music.data.model.UserPlaylist> { it.id == FAVORITES_PLAYLIST_ID }.thenByDescending { it.createdAt }),
                        songCount = songsToAdd.size,
                        onDismiss = { playlistPickerSongs = null },
                        onCreatePlaylist = {
                            createPlaylistSongs = songsToAdd
                            playlistPickerSongs = null
                        },
                        onPlaylistsConfirm = { selectedPlaylists, appendToEnd ->
                            selectedPlaylists.forEach { playlist ->
                                mainViewModel.addSongsToPlaylist(playlist.id, songsToAdd, appendToEnd)
                            }
                            Toast.makeText(context, context.getString(R.string.player_added_to_playlists, selectedPlaylists.size), Toast.LENGTH_SHORT).show()
                            playlistPickerSongs = null
                            selectedIds = emptySet()
                            selectionMode = false
                        }
                    )
                }
            }

            createPlaylistSongs?.let { songsToAdd ->
                CategoryCreatePlaylistAndAddSelectedSheet(
                    songCount = songsToAdd.size,
                    onDismiss = { createPlaylistSongs = null },
                    onCreate = { playlistName ->
                        mainViewModel.createPlaylist(playlistName) { playlist ->
                            if (playlist != null) {
                                mainViewModel.addSongsToPlaylist(playlist.id, songsToAdd)
                                Toast.makeText(context, context.getString(R.string.player_added_to_playlist_named, playlist.name), Toast.LENGTH_SHORT).show()
                                selectedIds = emptySet()
                                selectionMode = false
                            }
                        }
                        createPlaylistSongs = null
                    }
                )
            }

            ConfirmDangerDialog(
                show = pendingDeleteSongs.isNotEmpty(),
                title = stringResource(R.string.song_more_delete_song_title),
                message = stringResource(R.string.library_delete_selected_message, pendingDeleteSongs.size),
                confirmText = stringResource(R.string.song_more_delete_permanently),
                onDismiss = { pendingDeleteSongs = emptyList() },
                onConfirm = {
                    val songsToDelete = pendingDeleteSongs
                    pendingDeleteSongs = emptyList()
                    deleteSelectedSongs(songsToDelete)
                }
            )
        }
    }
}
