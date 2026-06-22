package com.ella.music.ui.folder

import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.data.model.formatPlaybackDuration
import com.ella.music.data.model.FolderPlaylist
import com.ella.music.data.model.Song
import com.ella.music.data.model.playlistIdentityKey
import com.ella.music.ui.components.ConfirmDangerDialog
import com.ella.music.ui.components.EllaMiuixBottomSheet
import com.ella.music.ui.components.EllaMiuixMenuItem
import com.ella.music.ui.components.EllaMiuixTextField
import com.ella.music.ui.components.EllaSmallTopAppBar
import com.ella.music.ui.components.FolderOutlineIcon
import com.ella.music.ui.components.SongItem
import com.ella.music.ui.components.SortDropdownItem
import com.ella.music.ui.components.SortDropdownMenu
import com.ella.music.ui.components.ellaPageBackground
import com.ella.music.viewmodel.MainViewModel
import com.ella.music.viewmodel.PlayerViewModel
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun FolderPlaylistsScreen(
    mainViewModel: MainViewModel,
    onBack: () -> Unit,
    onOpenPlaylist: (String) -> Unit,
    showBackButton: Boolean = true
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val songs by mainViewModel.songs.collectAsState()
    val playlists by mainViewModel.settingsManager.folderPlaylists.collectAsState(initial = emptyList())
    val availableFolders = remember(songs) { songs.availableFolderPlaylistFolders() }
    var editorTarget by remember { mutableStateOf<FolderPlaylist?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<FolderPlaylist?>(null) }
    var sortMode by remember { mutableStateOf(FolderPlaylistSortMode.Name) }
    var pinnedId by remember { mutableStateOf<String?>(null) }
    var moreMenuTarget by remember { mutableStateOf<FolderPlaylist?>(null) }

    val songCountMap = remember(playlists, songs) {
        playlists.associateWith { playlist -> songs.songsForFolderPlaylist(playlist.folders).size }
    }
    val durationMap = remember(playlists, songs) {
        playlists.associateWith { playlist -> songs.songsForFolderPlaylist(playlist.folders).sumOf { it.duration } }
    }
    val sortedPlaylists = remember(playlists, sortMode, pinnedId, songCountMap, durationMap) {
        playlists.sortedForFolderPlaylists(
            mode = sortMode,
            songCountProvider = { songCountMap[it] ?: 0 },
            durationProvider = { durationMap[it] ?: 0L },
            pinnedId = pinnedId
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ellaPageBackground())
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        EllaSmallTopAppBar(
            title = stringResource(R.string.folder_playlist_title),
            color = ellaPageBackground(),
            navigationIcon = {
                if (showBackButton) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = MiuixIcons.Regular.Back,
                            contentDescription = stringResource(R.string.common_back),
                            tint = MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            },
            actions = {
                SortDropdownMenu(
                    items = FolderPlaylistSortMode.entries.map { mode ->
                        SortDropdownItem(
                            text = stringResource(mode.labelRes),
                            selected = sortMode == mode,
                            onClick = { sortMode = mode }
                        )
                    }
                )
                IconButton(onClick = {
                    editorTarget = null
                    showEditor = true
                }) {
                    Icon(
                        imageVector = MiuixIcons.Regular.Add,
                        contentDescription = stringResource(R.string.folder_playlist_create),
                        tint = MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        )

        if (playlists.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.folder_playlist_empty),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = {
                        editorTarget = null
                        showEditor = true
                    }) {
                        Text(text = stringResource(R.string.folder_playlist_create))
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 130.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(sortedPlaylists, key = { it.id }) { playlist ->
                    val songCount = songCountMap[playlist] ?: 0
                    FolderPlaylistCard(
                        playlist = playlist,
                        songCount = songCount,
                        isPinned = playlist.id == pinnedId,
                        onClick = { onOpenPlaylist(playlist.id) },
                        onMore = { moreMenuTarget = playlist }
                    )
                }
            }
        }
    }

    moreMenuTarget?.let { playlist ->
        EllaMiuixBottomSheet(
            show = true,
            enableNestedScroll = false,
            title = playlist.name,
            onDismissRequest = { moreMenuTarget = null }
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
                EllaMiuixMenuItem(
                    text = stringResource(R.string.folder_playlist_more_pin),
                    onClick = {
                        pinnedId = if (pinnedId == playlist.id) null else playlist.id
                        moreMenuTarget = null
                    }
                )
                EllaMiuixMenuItem(
                    text = stringResource(R.string.folder_playlist_more_refresh),
                    onClick = {
                        scope.launch { mainViewModel.scanMusic() }
                        moreMenuTarget = null
                    }
                )
                EllaMiuixMenuItem(
                    text = stringResource(R.string.folder_playlist_more_share),
                    onClick = {
                        moreMenuTarget = null
                    }
                )
                EllaMiuixMenuItem(
                    text = stringResource(R.string.folder_playlist_edit),
                    onClick = {
                        editorTarget = playlist
                        showEditor = true
                        moreMenuTarget = null
                    }
                )
                EllaMiuixMenuItem(
                    text = stringResource(R.string.common_delete),
                    danger = true,
                    onClick = {
                        pendingDelete = playlist
                        moreMenuTarget = null
                    }
                )
            }
        }
    }

    FolderPlaylistEditorSheet(
        show = showEditor,
        target = editorTarget,
        availableFolders = availableFolders,
        onDismiss = { showEditor = false },
        onSave = { target, name, folders ->
            scope.launch {
                val safeName = name.trim()
                val nameExists = playlists.any { playlist ->
                    playlist.id != target?.id && playlist.name.trim().equals(safeName, ignoreCase = true)
                }
                if (nameExists) {
                    Toast.makeText(context, R.string.playlist_name_exists, Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val saved = mainViewModel.settingsManager.upsertFolderPlaylist(target?.id, name, folders)
                if (saved == null) {
                    Toast.makeText(context, R.string.folder_playlist_save_failed, Toast.LENGTH_SHORT).show()
                } else {
                    showEditor = false
                }
            }
        }
    )

    pendingDelete?.let { playlist ->
        ConfirmDangerDialog(
            show = true,
            title = stringResource(R.string.folder_playlist_delete_title),
            message = stringResource(R.string.folder_playlist_delete_message, playlist.name),
            confirmText = stringResource(R.string.common_delete),
            onDismiss = { pendingDelete = null },
            onConfirm = {
                scope.launch { mainViewModel.settingsManager.deleteFolderPlaylist(playlist.id) }
                pendingDelete = null
            }
        )
    }
}

@Composable
fun FolderPlaylistDetailScreen(
    playlistId: String,
    mainViewModel: MainViewModel,
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit,
    onNavigateToPlayer: () -> Unit,
    onNavigateToFolder: (String) -> Unit = {}
) {
    val songs by mainViewModel.songs.collectAsState()
    val playlists by mainViewModel.settingsManager.folderPlaylists.collectAsState(initial = emptyList())
    val openPlayerOnPlay by mainViewModel.settingsManager.openPlayerOnPlay.collectAsState(initial = false)
    val showPlayNextInLists by mainViewModel.settingsManager.showPlayNextInLists.collectAsState(initial = false)
    val currentSong by playerViewModel.currentSong.collectAsState()
    val favoriteSongKeys by playerViewModel.favoriteSongKeys.collectAsState()
    val playlist = remember(playlists, playlistId) { playlists.firstOrNull { it.id == playlistId } }
    val playlistSongs = remember(playlist, songs) {
        playlist?.let { songs.songsForFolderPlaylist(it.folders) }.orEmpty()
    }
    var selectedTab by rememberSaveable(playlistId) { mutableStateOf(FolderPlaylistTab.Songs) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ellaPageBackground())
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        EllaSmallTopAppBar(
            title = playlist?.name ?: stringResource(R.string.folder_playlist_title),
            color = ellaPageBackground(),
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
                IconButton(
                    onClick = {
                        if (playlistSongs.isNotEmpty()) {
                            playerViewModel.setPlaylist(playlistSongs, 0)
                            if (openPlayerOnPlay) onNavigateToPlayer()
                        }
                    }
                ) {
                    Icon(
                        imageVector = MiuixIcons.Regular.Play,
                        contentDescription = stringResource(R.string.playlist_play_all),
                        tint = MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        )

        if (playlist == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.playlist_not_found),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            }
            return@Column
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FolderPlaylistTab.entries.forEach { tab ->
                val selected = tab == selectedTab
                Text(
                    text = stringResource(tab.labelRes),
                    fontSize = 14.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (selected) MiuixTheme.colorScheme.onPrimary else MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(
                            if (selected) MiuixTheme.colorScheme.primary
                            else MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.72f)
                        )
                        .clickable { selectedTab = tab }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }

        when (selectedTab) {
            FolderPlaylistTab.Songs -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 130.dp)
                ) {
                    item {
                        Text(
                            text = stringResource(R.string.folder_playlist_detail_summary, playlist.folders.size, playlistSongs.size),
                            fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
                        )
                    }
                    if (playlistSongs.isEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.folder_playlist_empty_songs),
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 24.dp)
                            )
                        }
                    }
                    items(playlistSongs, key = { it.playlistIdentityKey() }) { song ->
                        val index = playlistSongs.indexOf(song)
                        val albumArtUri = remember(song.albumId) {
                            song.albumId.takeIf { it > 0L }?.let(mainViewModel::getAlbumArtUri)
                        }
                        SongItem(
                            song = song,
                            isCurrent = currentSong?.playlistIdentityKey() == song.playlistIdentityKey(),
                            albumArtUri = albumArtUri,
                            loadCoverArt = mainViewModel::getCoverArtBitmap,
                            loadAudioInfo = mainViewModel::getAudioInfo,
                            showPlayNextInLists = showPlayNextInLists,
                            isFavorite = song.playlistIdentityKey() in favoriteSongKeys,
                            loadSongRating = mainViewModel::getSongRating,
                            onClick = {
                                playerViewModel.setPlaylist(playlistSongs, index)
                                if (openPlayerOnPlay) onNavigateToPlayer()
                            },
                            onPlayNext = { playerViewModel.playNext(song) }
                        )
                    }
                }
            }
            FolderPlaylistTab.Folders -> {
                val folderEntries = remember(playlist, songs) {
                    playlist?.folders?.mapNotNull { folderPath ->
                        val normalized = folderPath.normalizeFolderPath()
                        val folderSongs = songs.filter { it.folderPath().normalizeFolderPath().startsWith(normalized) }
                        if (folderSongs.isEmpty()) return@mapNotNull null
                        val songCount = folderSongs.size
                        val albumCount = folderSongs.map { it.album }.distinct().size
                        val duration = folderSongs.sumOf { it.duration }
                        FolderPlaylistFolderEntry(
                            path = folderPath,
                            displayName = folderPath.substringAfterLast('/').ifBlank { folderPath },
                            songCount = songCount,
                            albumCount = albumCount,
                            duration = duration
                        )
                    }.orEmpty()
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 130.dp)
                ) {
                    item {
                        Text(
                            text = stringResource(R.string.folder_playlist_detail_summary, playlist.folders.size, playlistSongs.size),
                            fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
                        )
                    }
                    items(folderEntries, key = { it.path }) { entry ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            cornerRadius = 12.dp,
                            onClick = { onNavigateToFolder(entry.path) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                FolderOutlineIcon(
                                    tint = MiuixTheme.colorScheme.primary,
                                    modifier = Modifier.size(28.dp)
                                )
                                Column(modifier = Modifier.padding(start = 14.dp)) {
                                    Text(
                                        text = entry.displayName,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 15.sp,
                                        color = MiuixTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${entry.songCount} songs · ${entry.albumCount} albums · ${entry.duration.formatPlaybackDuration()}",
                                        fontSize = 12.sp,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private enum class FolderPlaylistTab(@param:StringRes val labelRes: Int) {
    Songs(R.string.folder_playlist_tab_songs),
    Folders(R.string.folder_playlist_tab_folders)
}

private data class FolderPlaylistFolderEntry(
    val path: String,
    val displayName: String,
    val songCount: Int,
    val albumCount: Int,
    val duration: Long
)

@Composable
fun LinkToFolderPlaylistSheet(
    show: Boolean,
    songs: List<Song>,
    folderPlaylists: List<FolderPlaylist>,
    onDismiss: () -> Unit,
    onLink: (FolderPlaylist) -> Unit
) {
    if (!show) return
    EllaMiuixBottomSheet(
        show = true,
        enableNestedScroll = false,
        title = stringResource(R.string.folder_playlist_associate),
        onDismissRequest = onDismiss
    ) {
        if (folderPlaylists.isEmpty()) {
            Text(
                text = stringResource(R.string.folder_playlist_empty),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(20.dp)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
            ) {
                items(folderPlaylists, key = { it.id }) { playlist ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onLink(playlist) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FolderOutlineIcon(
                            tint = MiuixTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Column(modifier = Modifier.padding(start = 14.dp)) {
                            Text(
                                text = playlist.name,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = MiuixTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.folder_playlist_card_summary, playlist.folders.size, 0),
                                fontSize = 12.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FolderPlaylistCard(
    playlist: FolderPlaylist,
    songCount: Int,
    isPinned: Boolean,
    onClick: () -> Unit,
    onMore: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 16.dp,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FolderOutlineIcon(
                tint = if (isPinned) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.size(34.dp)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 14.dp)
            ) {
                Text(
                    text = playlist.name,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(R.string.folder_playlist_card_summary, playlist.folders.size, songCount),
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onMore) {
                Icon(
                    imageVector = MiuixIcons.Regular.More,
                    contentDescription = stringResource(R.string.player_more_actions),
                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
private fun FolderPlaylistEditorSheet(
    show: Boolean,
    target: FolderPlaylist?,
    availableFolders: List<String>,
    onDismiss: () -> Unit,
    onSave: (FolderPlaylist?, String, List<String>) -> Unit
) {
    if (!show) return
    var draftName by remember(target?.id, show) { mutableStateOf(target?.name.orEmpty()) }
    var selectedFolders by remember(target?.id, show) { mutableStateOf(target?.folders.orEmpty().toSet()) }
    var searchQuery by remember(show) { mutableStateOf("") }
    var editorSort by remember(show) { mutableStateOf(EditorFolderSort.Name) }

    val filteredFolders = remember(availableFolders, searchQuery) {
        if (searchQuery.isBlank()) availableFolders
        else availableFolders.filter { folder ->
            folder.contains(searchQuery, ignoreCase = true) ||
                folder.substringAfterLast('/').contains(searchQuery, ignoreCase = true)
        }
    }

    val sortedFilteredFolders = remember(filteredFolders, editorSort) {
        when (editorSort) {
            EditorFolderSort.Name -> filteredFolders.sortedBy { it.substringAfterLast('/').lowercase() }
            EditorFolderSort.ModifiedTime -> filteredFolders.sortedByDescending { it }
            EditorFolderSort.SongCount -> filteredFolders
        }
    }

    EllaMiuixBottomSheet(
        show = true,
        enableNestedScroll = false,
        title = if (target == null) {
            stringResource(R.string.folder_playlist_create)
        } else {
            stringResource(R.string.folder_playlist_edit)
        },
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp)
        ) {
            EllaMiuixTextField(
                value = draftName,
                onValueChange = { draftName = it },
                label = stringResource(R.string.playlist_name_label),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            if (availableFolders.size > 6) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    EllaMiuixTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = stringResource(R.string.common_search),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    SortDropdownMenu(
                        items = EditorFolderSort.entries.map { mode ->
                            SortDropdownItem(
                                text = stringResource(mode.labelRes),
                                selected = editorSort == mode,
                                onClick = { editorSort = mode }
                            )
                        }
                    )
                }
            }
            Text(
                text = stringResource(R.string.folder_playlist_selected_count, selectedFolders.size),
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 430.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                sortedFilteredFolders.forEach { folder ->
                    SwitchPreference(
                        title = folder.folderDisplayName(stringResource(R.string.folder_root)),
                        summary = folder,
                        checked = folder in selectedFolders,
                        onCheckedChange = { checked ->
                            selectedFolders = if (checked) selectedFolders + folder else selectedFolders - folder
                        }
                    )
                }
            }
            Button(
                onClick = { onSave(target, draftName, selectedFolders.toList()) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp)
            ) {
                Text(text = stringResource(R.string.common_save))
            }
        }
    }
}

private enum class EditorFolderSort(val labelRes: Int) {
    ModifiedTime(R.string.playlist_song_sort_date_modified),
    Name(R.string.playlist_sort_name),
    SongCount(R.string.playlist_sort_song_count)
}

private fun List<Song>.availableFolderPlaylistFolders(): List<String> =
    map { it.folderPath() }
        .distinctBy { it.lowercase() }
        .sortedWith(compareBy<String> { it.substringAfterLast('/').musicSortKey() }.thenBy { it.musicSortKey() })

private fun List<Song>.songsForFolderPlaylist(folders: List<String>): List<Song> {
    val normalizedFolders = folders.map { it.normalizeFolderPath() }.filter { it.isNotBlank() }
    if (normalizedFolders.isEmpty()) return emptyList()
    return filter { song ->
        val songFolder = song.folderPath()
        normalizedFolders.any { folder ->
            songFolder.equals(folder, ignoreCase = true) ||
                songFolder.startsWith("${folder.trimEnd('/')}/", ignoreCase = true)
        }
    }
        .distinctBy { it.playlistIdentityKey() }
        .sortedWith(compareBy<Song> { it.folderPath().musicSortKey() }.thenBy { it.title.musicSortKey() })
}
