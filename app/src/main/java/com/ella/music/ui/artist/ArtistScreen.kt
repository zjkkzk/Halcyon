package com.ella.music.ui.artist

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextOverflow
import com.ella.music.data.detailedAudioInfo
import com.ella.music.data.model.AudioInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.ella.music.R
import com.ella.music.data.LibraryAlbumAggregator
import com.ella.music.data.model.FAVORITES_PLAYLIST_ID
import com.ella.music.data.model.Album
import com.ella.music.data.model.Song
import com.ella.music.data.model.SongTagInfo
import com.ella.music.data.model.UserPlaylist
import com.ella.music.data.model.formatPlaybackDuration
import com.ella.music.data.model.playlistIdentityKey
import com.ella.music.ui.LibrarySortUiState
import com.ella.music.ui.components.AppleStylePlayButton
import com.ella.music.ui.components.DefaultAlbumCover
import com.ella.music.ui.components.DoubleTapScrollOverlay
import com.ella.music.ui.components.LocateCurrentSongFloatingButton
import com.ella.music.ui.components.SafeCoverImage
import com.ella.music.ui.components.EllaMiuixBottomSheet
import com.ella.music.ui.components.EllaMiuixSheetActions
import com.ella.music.ui.components.EllaMiuixTextField
import com.ella.music.ui.components.ellaPageBackground
import com.ella.music.ui.components.buildTagEditorOptions
import com.ella.music.ui.components.TagEditorOptionKind
import com.ella.music.ui.components.launchTagEditorOption
import com.ella.music.ui.components.openSongSpectrumWithAspectPro
import com.ella.music.ui.components.shareLocalSong
import com.ella.music.ui.components.SongItem
import com.ella.music.ui.components.AddToPlaylistSheet
import com.ella.music.ui.components.ArtworkUsage
import com.ella.music.ui.components.SongMoreActionHost
import com.ella.music.ui.components.rememberSongArtworkState
import com.ella.music.viewmodel.MainViewModel
import com.ella.music.viewmodel.PlayerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.MapAlbum
import top.yukonga.miuix.kmp.icon.extended.SelectAll
import top.yukonga.miuix.kmp.icon.extended.Sort
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ArtistScreen(
    artistName: String,
    mainViewModel: MainViewModel,
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit,
    onAlbumClick: (Long) -> Unit,
    onArtistClick: (String) -> Unit = {},
    onMetadataCategoryClick: (String, String) -> Unit = { _, _ -> },
    onNavigateToPlayer: () -> Unit
) {
    val context = LocalContext.current
    val songs by mainViewModel.songs.collectAsState()
    val albums by mainViewModel.albums.collectAsState()
    val playlists by mainViewModel.playlists.collectAsState()
    val currentSong by playerViewModel.currentSong.collectAsState()
    val favoriteSongKeys by playerViewModel.favoriteSongKeys.collectAsState()
    val locateCurrentSongRequest by playerViewModel.locateCurrentSongRequest.collectAsState()
    val openPlayerOnPlay by mainViewModel.settingsManager.openPlayerOnPlay.collectAsState(initial = false)
    val showPlayNextInLists by mainViewModel.settingsManager.showPlayNextInLists.collectAsState(initial = false)
    val showAlbumArtists by mainViewModel.settingsManager.showAlbumArtists.collectAsState(initial = false)
    var sortExpanded by remember { mutableStateOf(false) }
    val sortIndex by mainViewModel.settingsManager.artistDetailSongSortIndex.collectAsState(initial = LibrarySortUiState.artistDetailSongSortIndex)
    val sortMode = ArtistDetailSongSortMode.entries.getOrElse(sortIndex) { ArtistDetailSongSortMode.Title }
    var albumSortMode by remember { mutableStateOf(ArtistDetailAlbumSortMode.YearAsc) }
    val scope = rememberCoroutineScope()
    var selectedTabTarget by rememberSaveable(artistName) { mutableStateOf(ArtistTab.Songs) }
    var scrollToTopRequest by remember { mutableStateOf(0) }
    var actionSong by remember { mutableStateOf<Song?>(null) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var playlistPickerSong by remember { mutableStateOf<Song?>(null) }
    var createPlaylistSong by remember { mutableStateOf<Song?>(null) }
    var playlistPickerSongs by remember { mutableStateOf<List<Song>?>(null) }
    var createPlaylistSongs by remember { mutableStateOf<List<Song>?>(null) }
    var tagEditorSong by remember { mutableStateOf<Song?>(null) }
    var songInfoSheetSong by remember { mutableStateOf<Song?>(null) }
    var aiInterpretationSong by remember { mutableStateOf<Song?>(null) }

    val artistSongs = remember(songs, artistName) {
        mainViewModel.getSongsForArtist(artistName)
    }
    val sortedArtistSongs = remember(artistSongs, sortMode) { artistSongs.sortedForArtistDetail(sortMode) }
    val participatedAlbums = remember(albums, songs, artistName) {
        mainViewModel.getParticipatedAlbumsForArtist(artistName)
    }
    val releaseAlbums = remember(albums, songs, artistName) {
        mainViewModel.getReleaseAlbumsForArtist(artistName)
    }
    val showReleaseAlbums = remember(albums, songs, artistName, showAlbumArtists) {
        showAlbumArtists && mainViewModel.hasAlbumArtistTags() && releaseAlbums.isNotEmpty()
    }
    val albumDurations = remember(songs) {
        LibraryAlbumAggregator.durationsByAlbumIdentity(songs)
    }
    val sortedParticipatedAlbums = remember(participatedAlbums, albumSortMode, albumDurations) {
        participatedAlbums.sortedForArtistAlbumDetail(albumSortMode, albumDurations)
    }
    val sortedReleaseAlbums = remember(releaseAlbums, albumSortMode, albumDurations) {
        releaseAlbums.sortedForArtistAlbumDetail(albumSortMode, albumDurations)
    }
    val albumArtUrisBySongId = remember(sortedArtistSongs) {
        sortedArtistSongs.associate { song -> song.id to mainViewModel.getAlbumArtUri(song.albumId) }
    }
    val albumArtUrisByAlbumId = remember(sortedParticipatedAlbums, sortedReleaseAlbums) {
        (sortedParticipatedAlbums + sortedReleaseAlbums)
            .distinctBy { it.id }
            .associate { album -> album.id to mainViewModel.getAlbumArtUri(album.artAlbumId) }
    }
    val hasComposerCategory = remember(songs, artistName) {
        mainViewModel.hasMetadataCategory("composer", artistName)
    }
    val hasLyricistCategory = remember(songs, artistName) {
        mainViewModel.hasMetadataCategory("lyricist", artistName)
    }
    val neteaseArtistUrl by produceState<String?>(initialValue = null, artistName, songs) {
        value = mainViewModel.getNeteaseArtistUrlForArtist(artistName)
    }
    val tabs = remember(showReleaseAlbums) {
        buildList {
            add(ArtistTab.Songs)
            add(ArtistTab.ParticipatedAlbums)
            if (showReleaseAlbums) add(ArtistTab.ReleaseAlbums)
        }
    }
    val selectedArtistTab = selectedTabTarget.takeIf { it in tabs } ?: ArtistTab.Songs
    val listState = rememberLazyListState()
    val currentSongItemIndex = remember(sortedArtistSongs, currentSong?.id, selectedArtistTab) {
        if (selectedArtistTab != ArtistTab.Songs || selectionMode) {
            -1
        } else {
            sortedArtistSongs.indexOfFirst { it.id == currentSong?.id }
                .takeIf { it >= 0 }
                ?.plus(3)
                ?: -1
        }
    }

    val representativeCoverSong = remember(artistSongs) { artistSongs.firstOrNull() }
    val artistCoverUri = representativeCoverSong?.albumId
        ?.takeIf { it > 0L }
        ?.let { mainViewModel.getAlbumArtUri(it) }
    val artistCoverState = rememberSongArtworkState(
        song = representativeCoverSong,
        albumArtUri = artistCoverUri,
        loadCoverArt = mainViewModel::getAlbumCoverArtBitmap,
        usage = ArtworkUsage.ArtistImage,
        showDefaultWhenMissing = false
    )

    fun finishSelectionMode() {
        selectionMode = false
        selectedIds = emptySet()
    }
    fun toggleSelection(song: Song) {
        val next = if (song.id in selectedIds) selectedIds - song.id else selectedIds + song.id
        selectedIds = next
        if (next.isEmpty()) selectionMode = false
    }
    fun selectedSongs(): List<Song> = sortedArtistSongs.filter { it.id in selectedIds }

    BackHandler(enabled = selectionMode || sortExpanded) {
        if (selectionMode) finishSelectionMode() else sortExpanded = false
    }

    LaunchedEffect(selectedArtistTab) {
        if (selectedArtistTab != ArtistTab.Songs && selectionMode) finishSelectionMode()
    }

    LaunchedEffect(scrollToTopRequest) {
        if (scrollToTopRequest > 0) listState.animateScrollToItem(0)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ellaPageBackground())
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp)
        ) {
            item {
                ArtistHeader(
                    artistName = artistName,
                    coverModel = artistCoverState.model,
                    songCount = sortedArtistSongs.size,
                    albumCount = (participatedAlbums + releaseAlbums).distinctBy { it.id }.size,
                    onPlayAll = {
                        if (sortedArtistSongs.isNotEmpty()) {
                            playerViewModel.setPlaylist(sortedArtistSongs, 0)
                            if (openPlayerOnPlay) onNavigateToPlayer()
                        }
                    }
                )
            }

            if (hasComposerCategory || hasLyricistCategory || !neteaseArtistUrl.isNullOrBlank()) {
                item {
                    ArtistJumpActions(
                        hasComposerCategory = hasComposerCategory,
                        hasLyricistCategory = hasLyricistCategory,
                        hasNeteaseArtist = !neteaseArtistUrl.isNullOrBlank(),
                        onComposerClick = { onMetadataCategoryClick("composer", artistName) },
                        onLyricistClick = { onMetadataCategoryClick("lyricist", artistName) },
                        onNeteaseClick = { openUrl(context, neteaseArtistUrl.orEmpty()) }
                    )
                }
            }

            item {
                ArtistTabRow(
                    tabs = tabs,
                    selectedTab = selectedArtistTab,
                    onTabSelected = { tab -> selectedTabTarget = tab }
                )
            }

            when (selectedArtistTab) {
                ArtistTab.Songs -> {
                    item {
                        Text(
                            text = stringResource(R.string.artist_song_count_sorted, sortedArtistSongs.size, stringResource(sortMode.labelRes)),
                            fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }

                    itemsIndexed(sortedArtistSongs) { index, song ->
                        val selected = song.id in selectedIds
                        SongItem(
                            song = song,
                            isCurrent = currentSong?.id == song.id,
                            albumArtUri = albumArtUrisBySongId[song.id],
                            loadCoverArt = mainViewModel::getCoverArtBitmap,
                            loadAudioInfo = mainViewModel::getAudioInfo,
                            isFavorite = song.playlistIdentityKey() in favoriteSongKeys,
                            loadSongRating = mainViewModel::getSongRating,
                            showPlayNextInLists = showPlayNextInLists,
                            selectionMode = selectionMode,
                            selected = selected,
                            onClick = {
                                if (selectionMode) {
                                    toggleSelection(song)
                                } else {
                                    playerViewModel.setPlaylist(sortedArtistSongs, index)
                                    if (openPlayerOnPlay) onNavigateToPlayer()
                                }
                            },
                            onLongClick = {
                                selectionMode = true
                                selectedIds = selectedIds + song.id
                            },
                            onPlayNext = { playerViewModel.playNext(song) },
                            onMore = { actionSong = song }
                        )
                    }
                }

                ArtistTab.ParticipatedAlbums -> {
                    item {
                        Text(
                            text = stringResource(R.string.artist_participated_album_count_sorted, sortedParticipatedAlbums.size, stringResource(albumSortMode.labelRes)),
                            fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                    items(
                        items = sortedParticipatedAlbums,
                        key = { it.id }
                    ) { album ->
                        ArtistAlbumRow(
                            album = album,
                            duration = albumDurations[album.id] ?: 0L,
                            albumArtUri = albumArtUrisByAlbumId[album.id],
                            onClick = { onAlbumClick(album.id) }
                        )
                    }
                }

                ArtistTab.ReleaseAlbums -> {
                    item {
                        Text(
                            text = stringResource(R.string.artist_release_album_count_sorted, sortedReleaseAlbums.size, stringResource(albumSortMode.labelRes)),
                            fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                    items(
                        items = sortedReleaseAlbums,
                        key = { it.id }
                    ) { album ->
                        ArtistAlbumRow(
                            album = album,
                            duration = albumDurations[album.id] ?: 0L,
                            albumArtUri = albumArtUrisByAlbumId[album.id],
                            onClick = { onAlbumClick(album.id) }
                        )
                    }
                }
            }

            if (selectedArtistTab != ArtistTab.Songs && (selectedArtistTab == ArtistTab.ParticipatedAlbums && participatedAlbums.isEmpty() || selectedArtistTab == ArtistTab.ReleaseAlbums && releaseAlbums.isEmpty())) {
                item {
                    Text(
                        text = stringResource(R.string.artist_no_albums),
                        fontSize = 14.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp)
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        IconButton(
            onClick = onBack,
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(start = 8.dp, top = 8.dp)
                .size(48.dp)
                .align(Alignment.TopStart)
        ) {
            Icon(
                imageVector = MiuixIcons.Regular.Back,
                contentDescription = stringResource(R.string.common_back),
                tint = Color.White,
                modifier = Modifier.size(26.dp)
            )
        }

        IconButton(
            onClick = {
                if (selectionMode) {
                    val selected = selectedSongs()
                    if (selected.isNotEmpty()) playlistPickerSongs = selected
                } else {
                    selectionMode = true
                    selectedIds = sortedArtistSongs.mapTo(mutableSetOf()) { it.id }
                }
            },
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(end = 56.dp, top = 8.dp)
                .size(48.dp)
                .align(Alignment.TopEnd)
        ) {
            Icon(
                imageVector = if (selectionMode) MiuixIcons.Regular.Add else MiuixIcons.Regular.SelectAll,
                contentDescription = stringResource(if (selectionMode) R.string.player_add_to_playlist else R.string.common_multi_select),
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }

        IconButton(
            onClick = { sortExpanded = !sortExpanded },
            enabled = !selectionMode,
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(end = 8.dp, top = 8.dp)
                .size(48.dp)
                .align(Alignment.TopEnd)
        ) {
            Icon(
                imageVector = MiuixIcons.Regular.Sort,
                contentDescription = stringResource(R.string.common_sort),
                tint = if (selectionMode) Color.White.copy(alpha = 0.36f) else Color.White,
                modifier = Modifier.size(24.dp)
            )
        }

        DoubleTapScrollOverlay(
            onDoubleTap = { scrollToTopRequest++ },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.statusBars)
                .fillMaxWidth()
                .height(56.dp),
            startPadding = 64.dp,
            endPadding = 104.dp
        )

        if (selectionMode) {
            Text(
                text = stringResource(R.string.library_selected_count, selectedIds.size),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(top = 22.dp)
            )
        }

        AnimatedVisibility(
            visible = sortExpanded,
            enter = expandVertically(),
            exit = shrinkVertically(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = 60.dp, end = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.94f))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                if (selectedArtistTab == ArtistTab.Songs) {
                    ArtistDetailSongSortMode.entries.forEach { mode ->
                        Text(
                            text = stringResource(mode.labelRes),
                            fontSize = 14.sp,
                            fontWeight = if (sortMode == mode) FontWeight.Bold else FontWeight.Normal,
                            color = if (sortMode == mode) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    LibrarySortUiState.artistDetailSongSortIndex = mode.ordinal
                                    scope.launch { mainViewModel.settingsManager.setArtistDetailSongSortIndex(mode.ordinal) }
                                    scrollToTopRequest++
                                    sortExpanded = false
                                }
                                .padding(vertical = 10.dp)
                        )
                    }
                } else {
                    ArtistDetailAlbumSortMode.entries.forEach { mode ->
                        Text(
                            text = stringResource(mode.labelRes),
                            fontSize = 14.sp,
                            fontWeight = if (albumSortMode == mode) FontWeight.Bold else FontWeight.Normal,
                            color = if (albumSortMode == mode) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    albumSortMode = mode
                                    scrollToTopRequest++
                                    sortExpanded = false
                                }
                                .padding(vertical = 10.dp)
                        )
                    }
                }
            }
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

        playlistPickerSong?.let { song ->
            EllaMiuixBottomSheet(
                show = true,
                enableNestedScroll = false,
                title = stringResource(R.string.player_add_to_playlist),
                onDismissRequest = { playlistPickerSong = null }
            ) {
                AddToPlaylistSheet(
                    playlists = playlists
                        .sortedWith(compareByDescending<com.ella.music.data.model.UserPlaylist> { it.id == FAVORITES_PLAYLIST_ID }.thenByDescending { it.createdAt }),
                    onDismiss = { playlistPickerSong = null },
                    onCreatePlaylist = {
                        createPlaylistSong = song
                        playlistPickerSong = null
                    },
                    onPlaylistsConfirm = { selectedPlaylists, appendToEnd ->
                        selectedPlaylists.forEach { playlist ->
                            mainViewModel.addSongsToPlaylist(playlist.id, listOf(song), appendToEnd)
                        }
                        Toast.makeText(context, context.getString(R.string.player_added_to_playlists, selectedPlaylists.size), Toast.LENGTH_SHORT).show()
                        playlistPickerSong = null
                    }
                )
            }
        }

        createPlaylistSong?.let { song ->
            ArtistCreatePlaylistSheet(
                onDismiss = { createPlaylistSong = null },
                onCreate = { name ->
                    mainViewModel.createPlaylist(name) { playlist ->
                        if (playlist != null) {
                            mainViewModel.addSongsToPlaylist(playlist.id, listOf(song))
                        }
                    }
                    createPlaylistSong = null
                }
            )
        }

        playlistPickerSongs?.let { songsToAdd ->
            EllaMiuixBottomSheet(
                show = true,
                enableNestedScroll = false,
                title = stringResource(R.string.player_add_to_playlist),
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
                        finishSelectionMode()
                    }
                )
            }
        }

        createPlaylistSongs?.let { songsToAdd ->
            ArtistCreatePlaylistSheet(
                onDismiss = { createPlaylistSongs = null },
                onCreate = { name ->
                    mainViewModel.createPlaylist(name) { playlist ->
                        if (playlist != null) mainViewModel.addSongsToPlaylist(playlist.id, songsToAdd)
                    }
                    createPlaylistSongs = null
                    finishSelectionMode()
                }
            )
        }

        tagEditorSong?.let { song ->
            EllaMiuixBottomSheet(
                show = true,
                enableNestedScroll = false,
                title = stringResource(R.string.song_more_edit_tags_title),
                onDismissRequest = { tagEditorSong = null }
            ) {
                ArtistTagEditorMenu(
                    song = song,
                    onDismiss = { tagEditorSong = null },
                    onOptionClick = { option ->
                        launchTagEditorOption(context, option)
                        tagEditorSong = null
                    }
                )
            }
        }

        songInfoSheetSong?.let { song ->
            EllaMiuixBottomSheet(
                show = true,
                enableNestedScroll = false,
                title = stringResource(R.string.player_song_info),
                onDismissRequest = { songInfoSheetSong = null }
            ) {
                ArtistSongInfoMenu(
                    song = song,
                    mainViewModel = mainViewModel,
                    onAiInterpret = {
                        songInfoSheetSong = null
                        aiInterpretationSong = song
                    },
                    onDismiss = { songInfoSheetSong = null }
                )
            }
        }

        aiInterpretationSong?.let { song ->
            EllaMiuixBottomSheet(
                show = true,
                enableNestedScroll = false,
                title = stringResource(R.string.song_more_ai_title),
                onDismissRequest = { aiInterpretationSong = null }
            ) {
                ArtistAiInterpretationMenu(
                    song = song,
                    mainViewModel = mainViewModel,
                    onDismiss = { aiInterpretationSong = null }
                )
            }
        }
    }
}
