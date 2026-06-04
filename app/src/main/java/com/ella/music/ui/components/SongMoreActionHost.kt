package com.ella.music.ui.components

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.data.decodeNeteaseKey
import com.ella.music.data.exception.WritePermissionRequiredException
import com.ella.music.data.metadata.AudioTagInfo
import com.lonx.audiotag.model.AudioTagKeys
import com.ella.music.data.detailedAudioInfo
import com.ella.music.data.model.AudioInfo
import com.ella.music.data.model.FAVORITES_PLAYLIST_ID
import com.ella.music.data.model.Song
import com.ella.music.data.model.SongTagInfo
import com.ella.music.data.model.UserPlaylist
import com.ella.music.data.model.albumIdentityId
import com.ella.music.data.neteaseAlbumUrl
import com.ella.music.data.neteaseArtistUrl
import com.ella.music.data.neteaseSongUrl
import com.ella.music.data.splitArtistNames
import com.ella.music.data.tagIdentityKey
import com.ella.music.viewmodel.MainViewModel
import com.ella.music.viewmodel.PlayerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.ella.music.data.NeteaseArtist

@Composable
fun SongMoreActionHost(
    actionSong: Song?,
    mainViewModel: MainViewModel,
    playerViewModel: PlayerViewModel,
    onDismissAction: () -> Unit,
    onNavigateToAlbum: (Long) -> Unit = {},
    onNavigateToArtist: (String) -> Unit = {},
    onSongRemovedFromPlaylist: ((Song) -> Unit)? = null,
    deleteFromLibrary: Boolean = true,
    showDelete: Boolean = true,
    showLocalFileActions: Boolean = true,
    showAddToQueue: Boolean = true,
    resolveSongForAction: (suspend (Song) -> Song)? = null,
    onDeleteSong: ((Song) -> Unit)? = null,
    extraTopContent: (@Composable ColumnScope.() -> Unit)? = null
) {
    val context = LocalContext.current
    val defaultDangerText = stringResource(R.string.common_delete)
    val actionSheetTitle = stringResource(R.string.song_more_actions_title)
    val addToPlaylistFailed = stringResource(R.string.song_more_add_to_playlist_failed)
    val addToQueueFailed = stringResource(R.string.song_more_add_to_queue_failed)
    val playNextFailed = stringResource(R.string.song_more_play_next_failed)
    val shareFailed = stringResource(R.string.song_more_share_failed)
    val addedToPlayNext = stringResource(R.string.song_more_added_to_play_next)
    val addedToQueue = stringResource(R.string.song_more_added_to_queue)
    val noArtistJump = stringResource(R.string.song_more_no_artist_jump)
    val noAlbumJump = stringResource(R.string.song_more_no_album_jump)
    val selectArtistTitle = stringResource(R.string.song_more_select_artist)
    val addToPlaylistTitle = stringResource(R.string.song_more_add_to_playlist_title)
    val editTagTitle = stringResource(R.string.song_more_edit_tags_title)
    val lyricTimingTitle = stringResource(R.string.song_more_lyric_timing)
    val aiInterpretTitle = stringResource(R.string.song_more_ai_title)
    val playlists by mainViewModel.playlists.collectAsState(initial = emptyList())
    val metadataEditorId by mainViewModel.settingsManager.metadataEditorId.collectAsState(initial = TagEditorOptionIds.ASK_EACH_TIME)
    val lyricTimingEditorId by mainViewModel.settingsManager.lyricTimingEditorId.collectAsState(initial = TagEditorOptionIds.ASK_EACH_TIME)
    val scope = rememberCoroutineScope()
    var playlistSong by remember { mutableStateOf<Song?>(null) }
    var createPlaylistSong by remember { mutableStateOf<Song?>(null) }
    var tagEditorSong by remember { mutableStateOf<Song?>(null) }
    var tagEditorKind by remember { mutableStateOf(TagEditorOptionKind.Metadata) }
    var metadataEditorSong by remember { mutableStateOf<Song?>(null) }
    var ratingSong by remember { mutableStateOf<Song?>(null) }
    var infoSong by remember { mutableStateOf<Song?>(null) }
    var aiSong by remember { mutableStateOf<Song?>(null) }
    var artistChoices by remember { mutableStateOf<List<String>>(emptyList()) }
    var dangerConfirmTitle by remember { mutableStateOf("") }
    var dangerConfirmMessage by remember { mutableStateOf("") }
    var dangerConfirmText by remember { mutableStateOf("") }
    var dangerConfirmAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingWriteRetry by remember { mutableStateOf<(suspend () -> Unit)?>(null) }
    val writePermissionNeeded = stringResource(R.string.song_more_metadata_write_permission_needed)

    val writePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            pendingWriteRetry?.let { retry ->
                scope.launch { retry() }
                pendingWriteRetry = null
            }
        } else {
            pendingWriteRetry = null
            Toast.makeText(context, writePermissionNeeded, Toast.LENGTH_SHORT).show()
        }
    }

    fun closeAction() = onDismissAction()

    fun requestDangerConfirm(
        title: String,
        message: String,
        confirmText: String,
        action: () -> Unit
    ) {
        dangerConfirmTitle = title
        dangerConfirmMessage = message
        dangerConfirmText = confirmText
        dangerConfirmAction = action
    }

    fun runResolvedSongAction(
        sourceSong: Song,
        failureMessage: String,
        action: (Song) -> Unit
    ) {
        scope.launch {
            runCatching {
                resolveSongForAction?.invoke(sourceSong) ?: sourceSong
            }.onSuccess { resolvedSong ->
                action(resolvedSong)
            }.onFailure { error ->
                Toast.makeText(context, error.localizedMessage ?: failureMessage, Toast.LENGTH_SHORT).show()
            }
        }
    }

    actionSong?.let { song ->
        WindowBottomSheet(
            show = true,
            enableNestedScroll = false,
            title = song.title.ifBlank { actionSheetTitle },
            onDismissRequest = ::closeAction
        ) {
            SongMoreActionSheet(
                song = song,
                extraTopContent = extraTopContent,
                onDismiss = ::closeAction,
                onAddToPlaylist = {
                    runResolvedSongAction(song, addToPlaylistFailed) { resolvedSong ->
                        playlistSong = resolvedSong
                        closeAction()
                    }
                },
                onAddToQueue = {
                    runResolvedSongAction(song, addToQueueFailed) { resolvedSong ->
                        playerViewModel.addToPlaylist(resolvedSong)
                        Toast.makeText(context, addedToQueue, Toast.LENGTH_SHORT).show()
                        closeAction()
                    }
                },
                onPlayNext = {
                    runResolvedSongAction(song, playNextFailed) { resolvedSong ->
                        playerViewModel.playNext(resolvedSong)
                        Toast.makeText(context, addedToPlayNext, Toast.LENGTH_SHORT).show()
                        closeAction()
                    }
                },
                onShare = {
                    runResolvedSongAction(song, shareFailed) { resolvedSong ->
                        shareLocalSong(context, resolvedSong)
                        closeAction()
                    }
                },
                onSpectrum = {
                    openSongSpectrumWithAspectPro(context, song)
                    closeAction()
                },
                onInfo = {
                    infoSong = song
                    closeAction()
                },
                onRating = {
                    ratingSong = song
                    closeAction()
                },
                onAiInterpret = {
                    aiSong = song
                    closeAction()
                },
                onArtist = {
                    val artists = splitArtistNames(song.artist)
                        .filterNot { it.equals("Unknown", ignoreCase = true) }
                        .distinctBy { it.tagIdentityKey() }
                    when (artists.size) {
                        0 -> Toast.makeText(context, noArtistJump, Toast.LENGTH_SHORT).show()
                        1 -> onNavigateToArtist(artists.first())
                        else -> artistChoices = artists
                    }
                    closeAction()
                },
                onAlbum = {
                    val albumId = song.albumIdentityId()
                    if (albumId > 0L) {
                        onNavigateToAlbum(albumId)
                    } else {
                        Toast.makeText(context, noAlbumJump, Toast.LENGTH_SHORT).show()
                    }
                    closeAction()
                },
                onEditTag = if (showLocalFileActions) {
                    {
                        tagEditorKind = TagEditorOptionKind.Metadata
                        tagEditorSong = song
                        closeAction()
                    }
                } else null,
                onLyricTiming = if (showLocalFileActions) {
                    {
                        tagEditorKind = TagEditorOptionKind.LyricTiming
                        tagEditorSong = song
                        closeAction()
                    }
                } else null,
                onRemoveFromPlaylist = onSongRemovedFromPlaylist?.let {
                    {
                        closeAction()
                        requestDangerConfirm(
                            title = context.getString(R.string.playlist_remove_song_title),
                            message = context.getString(
                                R.string.song_more_remove_from_playlist_message,
                                song.title.ifBlank { song.fileName.ifBlank { context.getString(R.string.common_this_song) } }
                            ),
                            confirmText = context.getString(R.string.common_remove)
                        ) {
                            it(song)
                        }
                    }
                },
                onDelete = if (showDelete) {
                    {
                        closeAction()
                        requestDangerConfirm(
                            title = if (deleteFromLibrary) {
                                context.getString(R.string.song_more_delete_song_title)
                            } else {
                                context.getString(R.string.song_more_remove_from_library_title)
                            },
                            message = if (deleteFromLibrary) {
                                context.getString(
                                    R.string.song_more_delete_song_message,
                                    song.title.ifBlank { song.fileName.ifBlank { context.getString(R.string.common_this_song) } }
                                )
                            } else {
                                context.getString(
                                    R.string.song_more_remove_from_library_message,
                                    song.title.ifBlank { song.fileName.ifBlank { context.getString(R.string.common_this_song) } }
                                )
                            },
                            confirmText = if (deleteFromLibrary) {
                                context.getString(R.string.song_more_delete_permanently)
                            } else {
                                context.getString(R.string.common_remove)
                            }
                        ) {
                            if (onDeleteSong != null) {
                                onDeleteSong(song)
                            } else if (deleteFromLibrary) {
                                scope.launch {
                                    val result = mainViewModel.deleteSongsResult(listOf(song))
                                    if (result.isSuccess) {
                                        Toast.makeText(context, context.getString(R.string.library_deleted_songs, 1), Toast.LENGTH_SHORT).show()
                                    } else {
                                        val error = result.exceptionOrNull()
                                        if (error is WritePermissionRequiredException) {
                                            pendingWriteRetry = {
                                                mainViewModel.removeSongsFromLibrary(listOf(song))
                                                Toast.makeText(context, context.getString(R.string.library_deleted_songs, 1), Toast.LENGTH_SHORT).show()
                                            }
                                            writePermissionLauncher.launch(
                                                IntentSenderRequest.Builder(error.intentSender).build()
                                            )
                                        } else {
                                            Toast.makeText(context, error?.localizedMessage ?: context.getString(R.string.song_more_metadata_save_failed), Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            } else {
                                mainViewModel.removeSongsFromLibrary(listOf(song))
                            }
                        }
                    }
                } else null,
                showSpectrum = showLocalFileActions,
                showAddToQueue = showAddToQueue
            )
        }
    }

    ConfirmDangerDialog(
        show = dangerConfirmAction != null,
        title = dangerConfirmTitle,
        message = dangerConfirmMessage,
        confirmText = dangerConfirmText,
        onDismiss = { dangerConfirmAction = null },
        onConfirm = {
            val action = dangerConfirmAction
            dangerConfirmAction = null
            action?.invoke()
        }
    )

    if (artistChoices.isNotEmpty()) {
        WindowBottomSheet(
            show = true,
            enableNestedScroll = false,
            title = selectArtistTitle,
            onDismissRequest = { artistChoices = emptyList() }
        ) {
            ArtistPickerContent(
                artists = artistChoices,
                onArtistSelected = { artist ->
                    artistChoices = emptyList()
                    onNavigateToArtist(artist)
                },
                onDismiss = { artistChoices = emptyList() }
            )
        }
    }

    playlistSong?.let { song ->
        WindowBottomSheet(
            show = true,
            enableNestedScroll = false,
            title = addToPlaylistTitle,
            onDismissRequest = { playlistSong = null }
        ) {
            AddToPlaylistSheet(
                playlists = playlists
                    .sortedWith(compareByDescending<com.ella.music.data.model.UserPlaylist> { it.id == FAVORITES_PLAYLIST_ID }.thenByDescending { it.createdAt }),
                onDismiss = { playlistSong = null },
                onCreatePlaylist = {
                    createPlaylistSong = song
                    playlistSong = null
                },
                onPlaylistsConfirm = { selectedPlaylists, appendToEnd ->
                    selectedPlaylists.forEach { playlist ->
                        mainViewModel.addSongsToPlaylist(playlist.id, listOf(song), appendToEnd)
                    }
                    Toast.makeText(
                        context,
                        context.getString(R.string.player_added_to_playlists, selectedPlaylists.size),
                        Toast.LENGTH_SHORT
                    ).show()
                    playlistSong = null
                }
            )
        }
    }

    createPlaylistSong?.let { song ->
        CreatePlaylistAndAddSheet(
            onDismiss = { createPlaylistSong = null },
            onCreate = { name ->
                mainViewModel.createPlaylist(name) { playlist ->
                    if (playlist != null) {
                        mainViewModel.addSongsToPlaylist(playlist.id, listOf(song))
                        Toast.makeText(
                            context,
                            context.getString(R.string.player_added_to_playlist_named, playlist.name),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                createPlaylistSong = null
            }
        )
    }

    tagEditorSong?.let { song ->
        val builtinOption = remember(song.id, tagEditorKind) {
            TagEditorOption(
                id = TagEditorOptionIds.BUILTIN_CUSTOM_TAG,
                label = context.getString(R.string.settings_editor_builtin_custom_tag),
                summary = context.getString(R.string.tag_editor_builtin_custom_tag_summary),
                kind = TagEditorOptionKind.Metadata,
                intents = emptyList(),
                sourceSong = song
            )
        }
        val tagOptions = remember(song.id, song.path, song.mimeType, tagEditorKind, builtinOption) {
            val external = buildTagEditorOptions(context, song)
                .filter { it.kind == tagEditorKind }
            if (tagEditorKind == TagEditorOptionKind.Metadata) listOf(builtinOption) + external else external
        }
        val preferredEditorId = if (tagEditorKind == TagEditorOptionKind.LyricTiming) {
            lyricTimingEditorId
        } else {
            metadataEditorId
        }
        val preferredOption = remember(tagOptions, preferredEditorId) {
            tagOptions.firstOrNull { it.id == preferredEditorId }
        }
        LaunchedEffect(song.id, preferredEditorId, preferredOption, tagEditorKind) {
            if (preferredEditorId.isNotBlank() && preferredOption != null) {
                if (preferredOption.id == TagEditorOptionIds.BUILTIN_CUSTOM_TAG) {
                    metadataEditorSong = song
                } else {
                    launchTagEditorOption(context, preferredOption)
                }
                tagEditorSong = null
            }
        }
        WindowBottomSheet(
            show = true,
            enableNestedScroll = false,
            title = if (tagEditorKind == TagEditorOptionKind.LyricTiming) lyricTimingTitle else editTagTitle,
            onDismissRequest = { tagEditorSong = null }
        ) {
            SongTagEditorSheet(
                song = song,
                options = tagOptions,
                onDismiss = { tagEditorSong = null },
                onOptionClick = { option ->
                    if (option.id == TagEditorOptionIds.BUILTIN_CUSTOM_TAG) {
                        metadataEditorSong = song
                    } else {
                        launchTagEditorOption(context, option)
                    }
                    tagEditorSong = null
                }
            )
        }
    }

    metadataEditorSong?.let { song ->
        WindowBottomSheet(
            show = true,
            enableNestedScroll = false,
            title = stringResource(R.string.song_more_metadata_editor_title),
            onDismissRequest = { metadataEditorSong = null }
        ) {
            SongMetadataEditorSheet(
                song = song,
                mainViewModel = mainViewModel,
                onDismiss = { metadataEditorSong = null },
                onSave = { tags ->
                    scope.launch {
                        val result = mainViewModel.writeSongMetadata(song, tags)
                        if (result.isSuccess) {
                            Toast.makeText(context, context.getString(R.string.song_more_metadata_saved), Toast.LENGTH_SHORT).show()
                            metadataEditorSong = null
                        } else {
                            val error = result.exceptionOrNull()
                            if (error is WritePermissionRequiredException) {
                                pendingWriteRetry = {
                                    val retryResult = mainViewModel.writeSongMetadata(song, tags)
                                    if (retryResult.isSuccess) {
                                        Toast.makeText(context, context.getString(R.string.song_more_metadata_saved), Toast.LENGTH_SHORT).show()
                                        metadataEditorSong = null
                                    } else {
                                        Toast.makeText(context, retryResult.exceptionOrNull()?.localizedMessage ?: context.getString(R.string.song_more_metadata_save_failed), Toast.LENGTH_SHORT).show()
                                    }
                                }
                                writePermissionLauncher.launch(
                                    IntentSenderRequest.Builder(error.intentSender).build()
                                )
                            } else {
                                Toast.makeText(context, error?.localizedMessage ?: context.getString(R.string.song_more_metadata_save_failed), Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            )
        }
    }

    ratingSong?.let { song ->
        WindowBottomSheet(
            show = true,
            enableNestedScroll = false,
            title = stringResource(R.string.song_more_rating_title),
            onDismissRequest = { ratingSong = null }
        ) {
            RatingSheet(
                currentRating = mainViewModel.getSongRating(song),
                onDismiss = { ratingSong = null },
                onRatingSelected = { rating ->
                    scope.launch {
                        val result = mainViewModel.writeSongRating(song, rating)
                        if (result.isSuccess) {
                            Toast.makeText(context, context.getString(R.string.song_more_rating_saved), Toast.LENGTH_SHORT).show()
                            ratingSong = null
                        } else {
                            val error = result.exceptionOrNull()
                            if (error is WritePermissionRequiredException) {
                                pendingWriteRetry = {
                                    val retryResult = mainViewModel.writeSongRating(song, rating)
                                    if (retryResult.isSuccess) {
                                        Toast.makeText(context, context.getString(R.string.song_more_rating_saved), Toast.LENGTH_SHORT).show()
                                        ratingSong = null
                                    } else {
                                        Toast.makeText(context, retryResult.exceptionOrNull()?.localizedMessage ?: context.getString(R.string.song_more_rating_failed), Toast.LENGTH_SHORT).show()
                                    }
                                }
                                writePermissionLauncher.launch(
                                    IntentSenderRequest.Builder(error.intentSender).build()
                                )
                            } else {
                                Toast.makeText(context, error?.localizedMessage ?: context.getString(R.string.song_more_rating_failed), Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            )
        }
    }

    infoSong?.let { song ->
        WindowBottomSheet(
            show = true,
            enableNestedScroll = false,
            title = stringResource(R.string.player_song_details),
            onDismissRequest = { infoSong = null }
        ) {
            SongInfoSheet(
                song = song,
                audioInfoLoader = mainViewModel::getAudioInfo,
                tagInfoLoader = mainViewModel::getSongTagInfo,
                onDismiss = { infoSong = null }
            )
        }
    }

    aiSong?.let { song ->
        WindowBottomSheet(
            show = true,
            enableNestedScroll = false,
            title = aiInterpretTitle,
            onDismissRequest = { aiSong = null }
        ) {
            SongAiInterpretationSheet(
                song = song,
                mainViewModel = mainViewModel,
                onDismiss = { aiSong = null }
            )
        }
    }
}

@Composable
private fun SongMoreActionSheet(
    song: Song,
    extraTopContent: (@Composable ColumnScope.() -> Unit)?,
    onDismiss: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onAddToQueue: () -> Unit,
    onPlayNext: () -> Unit,
    onShare: () -> Unit,
    onSpectrum: () -> Unit,
    onInfo: () -> Unit,
    onRating: () -> Unit,
    onAiInterpret: () -> Unit,
    onArtist: () -> Unit,
    onAlbum: () -> Unit,
    onEditTag: (() -> Unit)?,
    onLyricTiming: (() -> Unit)?,
    onRemoveFromPlaylist: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    showSpectrum: Boolean,
    showAddToQueue: Boolean
) {
    SongSheetColumn {
        extraTopContent?.invoke(this)
        SongMenuItem(stringResource(R.string.song_more_add_to_playlist), onAddToPlaylist)
        if (showAddToQueue) {
            SongMenuItem(stringResource(R.string.common_add_to_queue), onAddToQueue)
        }
        SongMenuItem(stringResource(R.string.song_more_play_next), onPlayNext)
        SongMenuItem(stringResource(R.string.common_share), onShare)
        if (showSpectrum) {
            SongMenuItem(stringResource(R.string.song_more_view_spectrum), onSpectrum)
        }
        SongMenuItem(stringResource(R.string.song_more_ai_title), onAiInterpret)
        SongMenuItem(stringResource(R.string.song_more_view_song_info), onInfo)
        SongMenuItem(stringResource(R.string.song_more_set_rating), onRating)
        SongMenuItem(
            stringResource(
                R.string.song_more_artist_entry,
                song.artist.ifBlank { stringResource(R.string.player_unknown_artist) }
            ),
            onArtist
        )
        SongMenuItem(
            stringResource(
                R.string.song_more_album_entry,
                song.album.ifBlank { stringResource(R.string.player_unknown_album) }
            ),
            onAlbum
        )
        if (onEditTag != null) {
            SongMenuItem(stringResource(R.string.song_more_edit_tags_title), onEditTag)
        }
        if (onLyricTiming != null) {
            SongMenuItem(stringResource(R.string.song_more_lyric_timing), onLyricTiming)
        }
        if (onRemoveFromPlaylist != null) {
            SongMenuItem(stringResource(R.string.playlist_remove_song_title), onRemoveFromPlaylist, danger = true)
        }
        if (onDelete != null) {
            SongMenuItem(stringResource(R.string.song_more_delete_permanently), onDelete, danger = true)
        }
        SongMenuItem(stringResource(R.string.common_cancel), onDismiss)
    }
}

@Composable
fun AddToPlaylistSheet(
    playlists: List<UserPlaylist>,
    songCount: Int? = null,
    onDismiss: () -> Unit,
    onCreatePlaylist: () -> Unit,
    onPlaylistsConfirm: (List<UserPlaylist>, Boolean) -> Unit
) {
    var selectedIds by remember(playlists) { mutableStateOf(emptySet<String>()) }
    var query by remember { mutableStateOf("") }
    var multiSelect by remember { mutableStateOf(songCount != null && songCount > 1) }
    var appendToEnd by remember { mutableStateOf(false) }
    var sortMode by remember { mutableStateOf(AddPlaylistSortMode.Custom) }
    val sortedPlaylists = remember(playlists, sortMode) {
        when (sortMode) {
            AddPlaylistSortMode.Custom -> playlists
            AddPlaylistSortMode.UpdatedAt -> playlists.sortedByDescending { it.updatedAt }
            AddPlaylistSortMode.Name -> playlists.sortedBy { it.name.lowercase(Locale.getDefault()) }
            AddPlaylistSortMode.SongCount -> playlists.sortedByDescending { it.songs.size }
        }
    }
    val visiblePlaylists = remember(sortedPlaylists, query) {
        query.trim().takeIf { it.isNotBlank() }?.let { q ->
            sortedPlaylists.filter { it.name.contains(q, ignoreCase = true) }
        } ?: sortedPlaylists
    }
    val selectedPlaylists = playlists.filter { it.id in selectedIds }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .background(MiuixTheme.colorScheme.background)
            .padding(horizontal = 18.dp, vertical = 8.dp)
            .heightIn(max = 560.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        songCount?.let { count ->
            Text(
                text = stringResource(R.string.library_selected_count, count),
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
            )
        }
        EllaMiuixTextField(
            value = query,
            onValueChange = { query = it },
            label = stringResource(R.string.common_search),
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            AddPlaylistChip(
                text = stringResource(R.string.common_sort) + ": " + stringResource(sortMode.labelRes),
                onClick = { sortMode = sortMode.next() },
                modifier = Modifier.weight(1f)
            )
            AddPlaylistChip(
                text = if (appendToEnd) stringResource(R.string.song_more_add_position_end) else stringResource(R.string.song_more_add_position_start),
                onClick = { appendToEnd = !appendToEnd },
                modifier = Modifier.weight(1f)
            )
            AddPlaylistChip(
                text = stringResource(R.string.common_multi_select),
                selected = multiSelect,
                onClick = {
                    multiSelect = !multiSelect
                    if (!multiSelect) selectedIds = emptySet()
                },
                modifier = Modifier.weight(1f)
            )
        }
        SongMenuItem(stringResource(R.string.song_more_create_playlist), onCreatePlaylist)
        if (playlists.isEmpty()) {
            Text(
                text = stringResource(R.string.song_more_no_custom_playlists),
                fontSize = 14.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 18.dp)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
            ) {
                items(visiblePlaylists, key = { it.id }) { playlist ->
                    val selected = playlist.id in selectedIds
                    SongMenuItem(
                        stringResource(
                            R.string.song_more_playlist_item_summary,
                            if (selected) "\u2713 " else "",
                            playlist.name,
                            playlist.songs.size
                        ),
                        onClick = {
                            if (multiSelect) {
                                selectedIds = if (selected) {
                                    selectedIds - playlist.id
                                } else {
                                    selectedIds + playlist.id
                                }
                            } else {
                                onPlaylistsConfirm(listOf(playlist), appendToEnd)
                            }
                        }
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            Button(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
            if (multiSelect) {
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (selectedPlaylists.isNotEmpty()) {
                            onPlaylistsConfirm(selectedPlaylists, appendToEnd)
                        }
                    }
                ) {
                    Text(stringResource(R.string.song_more_done_selected, selectedIds.size))
                }
            }
        }
    }
}

private enum class AddPlaylistSortMode(val labelRes: Int) {
    Custom(R.string.playlist_sort_custom),
    UpdatedAt(R.string.playlist_sort_updated_at),
    Name(R.string.playlist_sort_name),
    SongCount(R.string.playlist_sort_song_count);

    fun next(): AddPlaylistSortMode = entries[(ordinal + 1) % entries.size]
}

@Composable
private fun AddPlaylistChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(
                if (selected) MiuixTheme.colorScheme.primary.copy(alpha = 0.16f)
                else MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.72f)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp)
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ArtistPickerContent(
    artists: List<String>,
    onArtistSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    SongSheetColumn {
        artists.distinctBy { it.tagIdentityKey() }.forEach { artist ->
            BasicComponent(
                title = artist,
                onClick = { onArtistSelected(artist) }
            )
        }
        BasicComponent(
            title = stringResource(R.string.common_cancel),
            onClick = onDismiss
        )
    }
}

@Composable
fun CreatePlaylistAndAddSheet(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        delay(220L)
        focusRequester.requestFocus()
        keyboardController?.show()
    }
    EllaMiuixBottomSheet(
        show = true,
        title = stringResource(R.string.playlist_create_title),
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            EllaMiuixTextField(
                value = name,
                onValueChange = { name = it },
                label = stringResource(R.string.playlist_name_label),
                focusRequester = focusRequester
            )
            EllaMiuixSheetActions(
                cancelText = stringResource(R.string.common_cancel),
                confirmText = stringResource(R.string.common_create),
                onCancel = onDismiss,
                onConfirm = { onCreate(name) }
            )
        }
    }
}

@Composable
private fun SongTagEditorSheet(
    song: Song,
    options: List<TagEditorOption>,
    onDismiss: () -> Unit,
    onOptionClick: (TagEditorOption) -> Unit
) {
    SongSheetColumn {
        Text(
            text = song.title,
            fontSize = 13.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        options.forEach { option -> SongMenuItem(option.label, onClick = { onOptionClick(option) }) }
        SongMenuItem(stringResource(R.string.common_cancel), onDismiss)
    }
}

@Composable
private fun RatingSheet(
    currentRating: Int,
    onDismiss: () -> Unit,
    onRatingSelected: (Int) -> Unit
) {
    SongSheetColumn {
        SongMenuItem(
            title = if (currentRating <= 0) {
                "\u2713 ${stringResource(R.string.song_more_rating_none)}"
            } else {
                stringResource(R.string.song_more_rating_none)
            },
            onClick = { onRatingSelected(0) }
        )
        (1..5).forEach { rating ->
            val stars = "\u2605".repeat(rating) + "\u2606".repeat(5 - rating)
            SongMenuItem(
                title = if (currentRating == rating) "\u2713 $stars" else stars,
                onClick = { onRatingSelected(rating) }
            )
        }
        SongMenuItem(stringResource(R.string.common_cancel), onDismiss)
    }
}

@Composable
private fun BuiltInCustomTagSheet(
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var key by remember { mutableStateOf("") }
    var value by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        delay(220L)
        focusRequester.requestFocus()
        keyboardController?.show()
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .background(MiuixTheme.colorScheme.background)
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        EllaMiuixTextField(
            value = key,
            onValueChange = { key = it },
            label = stringResource(R.string.song_more_custom_tag_name),
            focusRequester = focusRequester
        )
        EllaMiuixTextField(
            value = value,
            onValueChange = { value = it },
            label = stringResource(R.string.song_more_custom_tag_value),
            singleLine = false,
            modifier = Modifier.fillMaxWidth()
        )
        EllaMiuixSheetActions(
            cancelText = stringResource(R.string.common_cancel),
            confirmText = stringResource(R.string.common_save),
            onCancel = onDismiss,
            onConfirm = { onSave(key, value) }
        )
    }
}

@Composable
private fun SongMetadataEditorSheet(
    song: Song,
    mainViewModel: MainViewModel,
    onDismiss: () -> Unit,
    onSave: (AudioTagInfo) -> Unit
) {
    val tagInfo by produceState<SongTagInfo?>(initialValue = null, song.id, song.dateModified, song.fileSize) {
        value = withContext(Dispatchers.IO) { mainViewModel.getSongTagInfo(song) }
    }
    val fullTagInfo by produceState<AudioTagInfo?>(initialValue = null, song.id, song.dateModified, song.fileSize) {
        value = withContext(Dispatchers.IO) { mainViewModel.getFullAudioTagInfo(song) }
    }

    var title by remember(tagInfo) { mutableStateOf(tagInfo?.title.orEmpty()) }
    var artist by remember(tagInfo) { mutableStateOf(tagInfo?.artist.orEmpty()) }
    var album by remember(tagInfo) { mutableStateOf(tagInfo?.album.orEmpty()) }
    var albumArtist by remember(tagInfo) { mutableStateOf(tagInfo?.albumArtist.orEmpty()) }
    var genre by remember(tagInfo) { mutableStateOf(tagInfo?.genre.orEmpty()) }
    var year by remember(tagInfo) { mutableStateOf(tagInfo?.year.orEmpty()) }
    var trackNumber by remember(tagInfo) { mutableStateOf(tagInfo?.track.orEmpty()) }
    var discNumber by remember(fullTagInfo) { mutableStateOf(fullTagInfo?.discNumber?.toString().orEmpty()) }
    var composer by remember(tagInfo) { mutableStateOf(tagInfo?.composer.orEmpty()) }
    var lyricist by remember(tagInfo) { mutableStateOf(tagInfo?.lyricist.orEmpty()) }
    var copyright by remember(tagInfo) { mutableStateOf(tagInfo?.copyright.orEmpty()) }
    var comment by remember(tagInfo) { mutableStateOf(tagInfo?.comment.orEmpty()) }
    var rating by remember(tagInfo) { mutableStateOf(tagInfo?.rating ?: 0) }
    var customTags: MutableList<Pair<String, String>> by remember(fullTagInfo) {
        val initial: MutableList<Pair<String, String>> = fullTagInfo?.customTags
            ?.filter { entry -> !AudioTagKeys.isReserved(entry.key) }
            ?.map { entry -> entry.key to entry.value.joinToString("; ") }
            ?.toMutableList()
            ?: mutableListOf()
        mutableStateOf(initial)
    }
    var showAddTag by remember { mutableStateOf(false) }

    SongSheetColumn {
        // Basic Info section
        SectionHeader(stringResource(R.string.song_more_metadata_section_basic))
        MetadataField(stringResource(R.string.song_more_metadata_title), title) { title = it }
        MetadataField(stringResource(R.string.song_more_metadata_artist), artist) { artist = it }
        MetadataField(stringResource(R.string.song_more_metadata_album), album) { album = it }
        MetadataField(stringResource(R.string.song_more_metadata_album_artist), albumArtist) { albumArtist = it }
        MetadataField(stringResource(R.string.song_more_metadata_genre), genre) { genre = it }
        MetadataField(stringResource(R.string.song_more_metadata_year), year) { year = it }

        // Track Details section
        SectionHeader(stringResource(R.string.song_more_metadata_section_track))
        MetadataField(stringResource(R.string.song_more_metadata_track_number), trackNumber) { trackNumber = it }
        MetadataField(stringResource(R.string.song_more_metadata_disc_number), discNumber) { discNumber = it }

        // Credits section
        SectionHeader(stringResource(R.string.song_more_metadata_section_credits))
        MetadataField(stringResource(R.string.song_more_metadata_composer), composer) { composer = it }
        MetadataField(stringResource(R.string.song_more_metadata_lyricist), lyricist) { lyricist = it }
        MetadataField(stringResource(R.string.song_more_metadata_copyright), copyright) { copyright = it }
        MetadataField(stringResource(R.string.song_more_metadata_comment), comment) { comment = it }

        // Rating section
        SectionHeader(stringResource(R.string.song_more_metadata_section_rating))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            (1..5).forEach { star ->
                val starChar = if (star <= rating) "★" else "☆"
                Text(
                    text = starChar,
                    fontSize = 28.sp,
                    color = if (star <= rating) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { rating = if (rating == star) 0 else star }
                        .padding(4.dp)
                )
            }
            if (rating > 0) {
                Text(
                    text = "✕",
                    fontSize = 16.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier
                        .padding(start = 8.dp, top = 8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { rating = 0 }
                        .padding(4.dp)
                )
            }
        }

        // Custom Tags section
        SectionHeader(stringResource(R.string.song_more_metadata_section_custom_tags))
        for (index in customTags.indices) {
            val pair = customTags[index]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EllaMiuixTextField(
                    value = pair.first,
                    onValueChange = { newKey -> customTags = customTags.toMutableList().apply { set(index, newKey to pair.second) } },
                    label = stringResource(R.string.song_more_custom_tag_name),
                    modifier = Modifier.weight(1f)
                )
                EllaMiuixTextField(
                    value = pair.second,
                    onValueChange = { newValue -> customTags = customTags.toMutableList().apply { set(index, pair.first to newValue) } },
                    label = stringResource(R.string.song_more_custom_tag_value),
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "✕",
                    fontSize = 16.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier
                        .padding(top = 14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { customTags = customTags.toMutableList().apply { removeAt(index) } }
                        .padding(4.dp)
                )
            }
        }
        if (showAddTag) {
            var newKey by remember { mutableStateOf("") }
            var newValue by remember { mutableStateOf("") }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EllaMiuixTextField(
                    value = newKey,
                    onValueChange = { newKey = it },
                    label = stringResource(R.string.song_more_custom_tag_name),
                    modifier = Modifier.weight(1f)
                )
                EllaMiuixTextField(
                    value = newValue,
                    onValueChange = { newValue = it },
                    label = stringResource(R.string.song_more_custom_tag_value),
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "✓",
                    fontSize = 16.sp,
                    color = MiuixTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(top = 14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .clickable {
                            if (newKey.isNotBlank()) {
                                customTags = customTags.toMutableList().apply { add(newKey to newValue) }
                                newKey = ""
                                newValue = ""
                                showAddTag = false
                            }
                        }
                        .padding(4.dp)
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 4.dp)
        ) {
            Button(onClick = { showAddTag = !showAddTag }) {
                Text(stringResource(R.string.song_more_metadata_add_custom_tag))
            }
        }

        // Save / Cancel buttons
        Spacer(modifier = Modifier.padding(vertical = 8.dp))
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp), horizontalArrangement = Arrangement.End) {
            Button(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = {
                val ctMap: MutableMap<String, MutableList<String>> = mutableMapOf()
                for (pair in customTags) {
                    if (pair.first.isNotBlank()) {
                        ctMap.getOrPut(pair.first) { mutableListOf() }.add(pair.second)
                    }
                }
                val tags = AudioTagInfo(
                    title = title.takeIf { v -> v != tagInfo?.title },
                    artist = artist.takeIf { v -> v != tagInfo?.artist },
                    album = album.takeIf { v -> v != tagInfo?.album },
                    albumArtist = albumArtist.takeIf { v -> v != tagInfo?.albumArtist },
                    genre = genre.takeIf { v -> v != tagInfo?.genre },
                    year = year.takeIf { v -> v != tagInfo?.year },
                    trackNumber = trackNumber.toIntOrNull()?.takeIf { v -> v.toString() != tagInfo?.track },
                    discNumber = discNumber.toIntOrNull()?.takeIf { v -> v != fullTagInfo?.discNumber },
                    composer = composer.takeIf { v -> v != tagInfo?.composer },
                    lyricist = lyricist.takeIf { v -> v != tagInfo?.lyricist },
                    copyright = copyright.takeIf { v -> v != tagInfo?.copyright },
                    comment = comment.takeIf { v -> v != tagInfo?.comment },
                    rating = rating.takeIf { v -> v != tagInfo?.rating },
                    customTags = ctMap
                )
                onSave(tags)
            }) {
                Text(stringResource(R.string.common_save))
            }
        }
        Spacer(modifier = Modifier.padding(bottom = 16.dp))
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
    )
}

@Composable
private fun MetadataField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    EllaMiuixTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 2.dp)
    )
}

@Composable
fun SongInfoSheet(
    song: Song,
    audioInfoLoader: (Song) -> AudioInfo,
    tagInfoLoader: (Song) -> SongTagInfo,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var showNeteaseKeyInfo by remember(song.id) { mutableStateOf(false) }
    var showNeteaseArtistPicker by remember(song.id) { mutableStateOf(false) }
    val audioInfo by produceState<AudioInfo?>(initialValue = null, song.id, song.dateModified, song.fileSize) {
        value = withContext(Dispatchers.IO) { audioInfoLoader(song) }
    }
    val tagInfo by produceState<SongTagInfo?>(initialValue = null, song.id, song.dateModified, song.fileSize) {
        value = withContext(Dispatchers.IO) { tagInfoLoader(song) }
    }
    val neteaseInfo = remember(tagInfo?.neteaseKey) { decodeNeteaseKey(tagInfo?.neteaseKey.orEmpty()) }
    val neteaseArtists = remember(neteaseInfo) {
        neteaseInfo?.artists.orEmpty().filter { it.id.isNotBlank() }
    }

    if (showNeteaseArtistPicker && neteaseArtists.isNotEmpty()) {
        SongSheetColumn {
            Text(
                text = stringResource(R.string.player_choose_netease_artist),
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MiuixTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
            )
            neteaseArtists.forEach { artist ->
                SongMenuItem(artist.name.ifBlank { "ID ${artist.id}" }, onClick = {
                    openUrl(context, neteaseArtistUrl(artist.id))
                })
            }
            SongMenuItem(stringResource(R.string.song_more_back_to_netease_key), onClick = { showNeteaseArtistPicker = false })
        }
        return
    }

    if (showNeteaseKeyInfo && neteaseInfo != null) {
        SongSheetColumn {
            Text(
                text = stringResource(R.string.song_more_netease_key),
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MiuixTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
            )
            neteaseInfo.musicName.takeIf { it.isNotBlank() }?.let { SongInfoRow(stringResource(R.string.player_detail_song), it) }
            neteaseInfo.aliases
                .joinToString(" / ")
                .takeIf { it.isNotBlank() }
                ?.let { SongInfoRow(stringResource(R.string.song_more_alias), it) }
            neteaseInfo.artists
                .joinToString(" / ") { it.name.ifBlank { it.id } }
                .takeIf { it.isNotBlank() }
                ?.let { SongInfoRow(stringResource(R.string.player_detail_artist), it) }
            neteaseInfo.albumName.takeIf { it.isNotBlank() }?.let { SongInfoRow(stringResource(R.string.player_detail_album), it) }
            neteaseInfo.comment.takeIf { it.isNotBlank() }?.let { SongInfoRow(stringResource(R.string.player_detail_comment), it) }
            neteaseInfo.musicId.takeIf { it.isNotBlank() }?.let { id ->
                SongMenuItem(stringResource(R.string.player_netease_song_page), onClick = { openUrl(context, neteaseSongUrl(id)) })
            }
            if (neteaseArtists.isNotEmpty()) {
                SongMenuItem(
                    title = stringResource(R.string.player_netease_artist_page),
                    onClick = {
                        if (neteaseArtists.size == 1) {
                            openUrl(context, neteaseArtistUrl(neteaseArtists.first().id))
                        } else {
                            showNeteaseArtistPicker = true
                        }
                    }
                )
            }
            neteaseInfo.albumId.takeIf { it.isNotBlank() }?.let { id ->
                SongMenuItem(stringResource(R.string.player_netease_album_page), onClick = { openUrl(context, neteaseAlbumUrl(id)) })
            }
            SongInfoRow(stringResource(R.string.song_more_raw_netease_key), neteaseInfo.raw)
            SongMenuItem(stringResource(R.string.common_back), onClick = { showNeteaseKeyInfo = false })
        }
        return
    }

    SongSheetColumn {
        SongInfoRow(stringResource(R.string.player_detail_song), tagInfo?.title?.ifBlank { song.title } ?: song.title)
        SongInfoRow(stringResource(R.string.player_detail_artist), tagInfo?.artist?.ifBlank { song.artist } ?: song.artist)
        SongInfoRow(stringResource(R.string.player_detail_album), tagInfo?.album?.ifBlank { song.album } ?: song.album)
        SongInfoRow(stringResource(R.string.song_more_detail_album_artist), tagInfo?.albumArtist?.ifBlank { song.albumArtist }.orEmpty())
        SongInfoRow(stringResource(R.string.song_more_detail_genre), tagInfo?.genre?.ifBlank { song.genre }.orEmpty())
        SongInfoRow(stringResource(R.string.song_more_detail_year), tagInfo?.year?.ifBlank { song.year }.orEmpty())
        SongInfoRow(stringResource(R.string.player_detail_composer), tagInfo?.composer?.ifBlank { song.composer }.orEmpty())
        SongInfoRow(stringResource(R.string.player_detail_lyricist), tagInfo?.lyricist?.ifBlank { song.lyricist }.orEmpty())
        SongInfoRow(stringResource(R.string.player_detail_comment), tagInfo?.displayComment.orEmpty())
        if (!tagInfo?.neteaseKey.isNullOrBlank()) {
            SongInfoActionRow(
                label = stringResource(R.string.song_more_netease_key),
                value = neteaseInfo?.musicName?.ifBlank { null }
                    ?: neteaseInfo?.musicId?.takeIf { it.isNotBlank() }?.let {
                        context.getString(R.string.song_more_netease_song_id, it)
                    }
                    ?: stringResource(R.string.song_more_view_netease_info),
                onClick = { showNeteaseKeyInfo = true }
            )
        }
        SongInfoRow(stringResource(R.string.song_more_detail_format), audioInfo?.let { detailedAudioInfo(it) }.orEmpty())
        SongInfoRow(stringResource(R.string.song_more_detail_duration), song.durationText)
        SongInfoRow(stringResource(R.string.song_more_detail_size), formatFileSize(song.fileSize))
        SongInfoRow(stringResource(R.string.song_more_detail_modified_time), song.dateModified.formatSongDateTime())
        SongInfoRow(stringResource(R.string.song_more_detail_added_time), song.dateAdded.formatSongDateTime())
        SongInfoRow(stringResource(R.string.song_more_detail_file_name), song.fileName.ifBlank { song.path.substringAfterLast('/') })
        SongInfoRow(stringResource(R.string.song_more_detail_path), song.path)
    }
}

@Composable
private fun SongAiInterpretationSheet(
    song: Song,
    mainViewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val result by produceState<Result<String>?>(initialValue = null, song.id) {
        value = runCatching { mainViewModel.interpretSongWithOpenAi(song) }
    }
    SongSheetColumn {
        Text(
            text = when {
                result == null -> stringResource(R.string.song_more_loading_ai)
                result?.isSuccess == true -> result?.getOrNull().orEmpty()
                else -> result?.exceptionOrNull()?.message ?: stringResource(R.string.song_more_ai_failed)
            },
            fontSize = 14.sp,
            lineHeight = 22.sp,
            color = MiuixTheme.colorScheme.onSurface,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.72f))
                .padding(horizontal = 16.dp, vertical = 14.dp)
        )
        SongMenuItem(stringResource(R.string.common_close), onDismiss)
    }
}

@Composable
private fun SongSheetColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .background(MiuixTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        content = content
    )
}

@Composable
private fun SongMenuItem(
    title: String,
    onClick: () -> Unit,
    danger: Boolean = false
) {
    Text(
        text = title,
        fontSize = 15.sp,
        fontWeight = FontWeight.Medium,
        color = if (danger) Color(0xFFE5484D) else MiuixTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 14.dp)
    )
}

@Composable
private fun SongInfoRow(label: String, value: String) {
    if (value.isBlank()) return
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.38f))
            .combinedClickable(
                onClick = {},
                onLongClick = { copySongInfoValue(context, label, value) }
            )
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
        )
        Text(
            text = value,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            color = MiuixTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun SongInfoActionRow(label: String, value: String, onClick: () -> Unit) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.18f))
            .combinedClickable(
                onClick = onClick,
                onLongClick = { copySongInfoValue(context, label, value) }
            )
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = MiuixTheme.colorScheme.primary
        )
        Text(
            text = value,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            color = MiuixTheme.colorScheme.onSurface
        )
    }
}

private fun copySongInfoValue(context: Context, label: String, value: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
    Toast.makeText(context, "已复制 $label", Toast.LENGTH_SHORT).show()
}

private fun openUrl(context: Context, url: String) {
    if (url.isBlank()) return
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0L) return ""
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1 -> String.format(Locale.ROOT, "%.2f GB", gb)
        mb >= 1 -> String.format(Locale.ROOT, "%.2f MB", mb)
        else -> String.format(Locale.ROOT, "%.0f KB", kb)
    }
}

private fun Long.formatSongDateTime(): String {
    if (this <= 0L) return ""
    val millis = if (this < 10_000_000_000L) this * 1000L else this
    return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(millis))
}
