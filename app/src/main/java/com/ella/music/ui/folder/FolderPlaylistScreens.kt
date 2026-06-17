package com.ella.music.ui.folder

import android.widget.Toast
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.data.model.FolderPlaylist
import com.ella.music.data.model.Song
import com.ella.music.data.model.playlistIdentityKey
import com.ella.music.ui.components.ConfirmDangerDialog
import com.ella.music.ui.components.EllaMiuixBottomSheet
import com.ella.music.ui.components.EllaMiuixTextField
import com.ella.music.ui.components.EllaSmallTopAppBar
import com.ella.music.ui.components.FolderOutlineIcon
import com.ella.music.ui.components.SongItem
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
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Edit
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
                items(playlists.sortedBy { it.name.musicSortKey() }, key = { it.id }) { playlist ->
                    val playlistSongs = remember(playlist, songs) { songs.songsForFolderPlaylist(playlist.folders) }
                    FolderPlaylistCard(
                        playlist = playlist,
                        songCount = playlistSongs.size,
                        onClick = { onOpenPlaylist(playlist.id) },
                        onEdit = {
                            editorTarget = playlist
                            showEditor = true
                        },
                        onDelete = { pendingDelete = playlist }
                    )
                }
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
    onNavigateToPlayer: () -> Unit
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
            items(playlistSongs, key = { it.id }) { song ->
                val index = playlistSongs.indexOf(song)
                val albumArtUri = remember(song.albumId) {
                    song.albumId.takeIf { it > 0L }?.let(mainViewModel::getAlbumArtUri)
                }
                SongItem(
                    song = song,
                    isCurrent = currentSong?.id == song.id,
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
}

@Composable
private fun FolderPlaylistCard(
    playlist: FolderPlaylist,
    songCount: Int,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
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
                tint = MiuixTheme.colorScheme.primary,
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
            IconButton(onClick = onEdit) {
                Icon(
                    imageVector = MiuixIcons.Regular.Edit,
                    contentDescription = stringResource(R.string.folder_playlist_edit),
                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.size(22.dp)
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = MiuixIcons.Regular.Delete,
                    contentDescription = stringResource(R.string.common_delete),
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
                availableFolders.forEach { folder ->
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
