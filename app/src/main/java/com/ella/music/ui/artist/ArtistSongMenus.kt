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
internal fun ArtistSongActionMenu(
    song: Song,
    onDismiss: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onPlayNext: () -> Unit,
    onShare: () -> Unit,
    onSpectrum: () -> Unit,
    onInfo: () -> Unit,
    onAiInterpret: () -> Unit,
    onArtist: () -> Unit,
    onAlbum: () -> Unit,
    onEditTag: () -> Unit,
    onDelete: () -> Unit
) {
    ArtistSheetColumn {
        ArtistSheetHandle()
        Text(
            text = song.title,
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MiuixTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
        )
        ArtistMenuItem(stringResource(R.string.player_add_to_playlist), onAddToPlaylist)
        ArtistMenuItem(stringResource(R.string.song_more_play_next), onPlayNext)
        ArtistMenuItem(stringResource(R.string.common_share), onShare)
        ArtistMenuItem(stringResource(R.string.song_more_view_spectrum), onSpectrum)
        ArtistMenuItem(stringResource(R.string.song_more_ai_title), onAiInterpret)
        ArtistMenuItem(stringResource(R.string.song_more_view_song_info), onInfo)
        ArtistMenuItem(stringResource(R.string.song_more_artist_entry, song.artist.ifBlank { stringResource(R.string.player_unknown_artist) }), onArtist)
        ArtistMenuItem(stringResource(R.string.song_more_album_entry, song.album.ifBlank { stringResource(R.string.player_unknown_album) }), onAlbum)
        ArtistMenuItem(stringResource(R.string.song_more_edit_tags_title), onEditTag)
        ArtistMenuItem(stringResource(R.string.song_more_delete_permanently), onDelete, danger = true)
        ArtistMenuItem(stringResource(R.string.common_cancel), onDismiss)
    }
}

@Composable
internal fun ArtistAddToPlaylistMenu(
    playlists: List<UserPlaylist>,
    onDismiss: () -> Unit,
    onCreatePlaylist: () -> Unit,
    onPlaylistsConfirm: (List<UserPlaylist>, Boolean) -> Unit
) {
    var selectedPlaylistIds by remember(playlists) { mutableStateOf(emptySet<String>()) }
    val selectedPlaylists = playlists.filter { it.id in selectedPlaylistIds }
    ArtistSheetColumn {
        ArtistSheetHandle()
        Text(
            text = stringResource(R.string.player_add_to_playlist),
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MiuixTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
        )
        ArtistMenuItem(stringResource(R.string.song_more_create_playlist), onCreatePlaylist)
        if (playlists.isEmpty()) {
            Text(
                text = stringResource(R.string.song_more_no_custom_playlists),
                fontSize = 14.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 18.dp)
            )
        } else {
            playlists.forEach { playlist ->
                val selected = playlist.id in selectedPlaylistIds
                ArtistMenuItem(stringResource(R.string.song_more_playlist_item_summary, if (selected) "✓ " else "", playlist.name, playlist.songs.size), onClick = {
                    selectedPlaylistIds = if (selected) {
                        selectedPlaylistIds - playlist.id
                    } else {
                        selectedPlaylistIds + playlist.id
                    }
                })
            }
        }
        if (playlists.isNotEmpty()) {
            ArtistMenuItem(stringResource(R.string.song_more_done_selected, selectedPlaylistIds.size), onClick = {
                if (selectedPlaylists.isNotEmpty()) {
                    onPlaylistsConfirm(selectedPlaylists, false)
                }
            })
        }
        ArtistMenuItem(stringResource(R.string.common_cancel), onDismiss)
    }
}

@Composable
internal fun ArtistCreatePlaylistSheet(
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
            modifier = Modifier.padding(bottom = 18.dp),
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
internal fun ArtistTagEditorMenu(
    song: Song,
    onDismiss: () -> Unit,
    onOptionClick: (com.ella.music.ui.components.TagEditorOption) -> Unit
) {
    val context = LocalContext.current
    val options = remember(song) {
        buildTagEditorOptions(context, song).filter { it.kind == TagEditorOptionKind.Metadata }
    }
    ArtistSheetColumn {
        ArtistSheetHandle()
        Text(
            text = stringResource(R.string.song_more_edit_tags_title),
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MiuixTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
        )
        Text(
            text = song.title,
            fontSize = 13.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        options.forEach { option ->
            ArtistMenuItem(option.label, onClick = { onOptionClick(option) })
        }
        ArtistMenuItem(stringResource(R.string.common_cancel), onDismiss)
    }
}

@Composable
internal fun ArtistSongInfoMenu(
    song: Song,
    mainViewModel: MainViewModel,
    onAiInterpret: () -> Unit,
    onDismiss: () -> Unit
) {
    val audioInfo by produceState<AudioInfo?>(initialValue = null, song.id, song.dateModified, song.fileSize) {
        value = withContext(Dispatchers.IO) { mainViewModel.getAudioInfo(song) }
    }
    val tagInfo by produceState<SongTagInfo?>(initialValue = null, song.id, song.dateModified, song.fileSize) {
        value = withContext(Dispatchers.IO) { mainViewModel.getSongTagInfo(song) }
    }
    ArtistSheetColumn {
        ArtistSheetHandle()
        Text(
            text = stringResource(R.string.player_song_info),
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MiuixTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
        )
        ArtistMenuItem(stringResource(R.string.song_more_ai_title), onAiInterpret)
        ArtistInfoRow(stringResource(R.string.song_more_metadata_title), tagInfo?.title?.ifBlank { song.title } ?: song.title)
        ArtistInfoRow(stringResource(R.string.song_more_metadata_artist), tagInfo?.artist?.ifBlank { song.artist } ?: song.artist)
        ArtistInfoRow(stringResource(R.string.song_more_metadata_album), tagInfo?.album?.ifBlank { song.album } ?: song.album)
        ArtistInfoRow(stringResource(R.string.song_more_metadata_album_artist), tagInfo?.albumArtist?.ifBlank { song.albumArtist }.orEmpty())
        ArtistInfoRow(stringResource(R.string.song_more_metadata_comment), tagInfo?.displayComment.orEmpty())
        ArtistInfoRow(stringResource(R.string.artist_info_audio), audioInfo?.let { detailedAudioInfo(it) }.orEmpty())
        ArtistInfoRow(stringResource(R.string.song_more_detail_path), song.path)
    }
}

@Composable
internal fun ArtistAiInterpretationMenu(
    song: Song,
    mainViewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val result by produceState<Result<String>?>(initialValue = null, song.id) {
        value = runCatching { mainViewModel.interpretSongWithOpenAi(song) }
    }
    ArtistSheetColumn {
        ArtistSheetHandle()
        Text(
            text = stringResource(R.string.song_more_ai_title),
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MiuixTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
        )
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
        ArtistMenuItem(stringResource(R.string.common_close), onDismiss)
    }
}

@Composable
internal fun ArtistSheetColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(MiuixTheme.colorScheme.background.copy(alpha = 0.98f))
            .heightIn(max = 400.dp)
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        content = content
    )
}

@Composable
internal fun ArtistSheetHandle() {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(52.dp)
                .height(5.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.28f))
        )
    }
}

@Composable
internal fun ArtistMenuItem(
    text: String,
    onClick: () -> Unit,
    danger: Boolean = false
) {
    Text(
        text = text,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        color = if (danger) Color(0xFFE5484D) else MiuixTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 13.dp)
    )
}

@Composable
internal fun ArtistInfoRow(label: String, value: String) {
    if (value.isBlank()) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
        )
        Text(
            text = value,
            fontSize = 14.sp,
            color = MiuixTheme.colorScheme.onSurface,
            maxLines = if (label == stringResource(R.string.song_more_detail_path) || label == stringResource(R.string.artist_info_audio)) 4 else 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}
