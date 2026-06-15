package com.ella.music.ui.search

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ella.music.R
import com.ella.music.data.SettingsManager
import com.ella.music.data.model.Artist
import com.ella.music.data.model.Song
import com.ella.music.data.model.albumIdentityId
import com.ella.music.data.model.matchesFullTagSearch
import com.ella.music.ui.components.EllaSearchBar
import com.ella.music.ui.components.ConfirmDangerDialog
import com.ella.music.ui.components.SongItem
import com.ella.music.ui.components.SongMoreActionHost
import com.ella.music.ui.components.ellaPageBackground
import com.ella.music.viewmodel.MainViewModel
import com.ella.music.viewmodel.PlayerViewModel
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun LibrarySearchScreen(
    mainViewModel: MainViewModel,
    playerViewModel: PlayerViewModel,
    initialFilterType: String? = null,
    initialQuery: String? = null,
    showBackButton: Boolean = true,
    onBack: () -> Unit,
    onNavigateToAlbum: (Long) -> Unit,
    onNavigateToArtist: (String) -> Unit,
    onNavigateToPlaylist: (String) -> Unit,
    onNavigateToMetadataCategory: (String, String) -> Unit,
    onNavigateToPlayer: () -> Unit
) {
    val context = LocalContext.current
    val songs by mainViewModel.songs.collectAsState()
    val albums by mainViewModel.albums.collectAsState()
    val playlists by mainViewModel.playlists.collectAsState()
    val currentSong by playerViewModel.currentSong.collectAsState()
    val lyricSourceMode by mainViewModel.settingsManager.lyricSourceMode.collectAsState(initial = SettingsManager.LYRIC_SOURCE_AUTO)
    val showPlayNextInLists by mainViewModel.settingsManager.showPlayNextInLists.collectAsState(initial = false)
    var query by remember(initialQuery) { mutableStateOf(initialQuery.orEmpty()) }
    var filter by remember(initialFilterType) { mutableStateOf(SearchFilter.fromRouteType(initialFilterType)) }
    var duplicatesOnly by remember { mutableStateOf(false) }
    var actionSong by remember { mutableStateOf<Song?>(null) }
    var history by remember { mutableStateOf(loadSearchHistory(context)) }
    var showClearHistoryConfirm by remember { mutableStateOf(false) }

    val trimmedQuery = query.trim()
    val duplicateSongs = remember(songs) { songs.duplicateTitleAlbumSongs() }
    val duplicatesOnlyActive = duplicatesOnly && filter.supportsDuplicateFilter
    val songSearchSource = remember(songs, duplicateSongs, duplicatesOnlyActive) {
        if (duplicatesOnlyActive) duplicateSongs else songs
    }
    val immediateSongResults = remember(songSearchSource, trimmedQuery, filter, duplicatesOnlyActive) {
        when {
            !filter.acceptsSongResults || filter == SearchFilter.Lyrics -> emptyList()
            trimmedQuery.isBlank() && !duplicatesOnlyActive -> emptyList()
            trimmedQuery.isBlank() -> songSearchSource
                .asSequence()
                .take(80)
                .map { SongSearchResult(it) }
                .toList()
            else -> songSearchSource
                .asSequence()
                .filter { it.matchesFullTagSearch(trimmedQuery) }
                .take(80)
                .map { SongSearchResult(it) }
                .toList()
        }
    }
    val cachedSongResults = remember(context, songs, trimmedQuery, filter, duplicatesOnlyActive) {
        if (duplicatesOnlyActive) emptyList() else loadCachedSongSearchResults(context, songs, trimmedQuery, filter)
    }
    val songResults by produceState(
        initialValue = cachedSongResults.ifEmpty { immediateSongResults },
        songSearchSource,
        trimmedQuery,
        filter,
        duplicateSongs,
        duplicatesOnlyActive,
        cachedSongResults,
        lyricSourceMode
    ) {
        value = cachedSongResults.ifEmpty { immediateSongResults }
        if (!filter.acceptsSongResults || trimmedQuery.isBlank()) {
            return@produceState
        }
        if (filter == SearchFilter.Lyrics) {
            val current = mutableListOf<SongSearchResult>()
            for (song in songSearchSource) {
                if (current.size >= 80) break
                val snippet = mainViewModel.repository
                    .getLyrics(song, lyricSourceMode)
                    .firstMatchingLyricSnippet(trimmedQuery)
                    ?: continue
                current += SongSearchResult(song = song, lyricSnippet = snippet)
                value = current.toList()
            }
            return@produceState
        }
        if (duplicatesOnlyActive) {
            return@produceState
        }
        val current = cachedSongResults.ifEmpty { immediateSongResults }.toMutableList()
        val seenKeys = current.map { it.song.searchIdentityKey() }.toMutableSet()
        val remainingSongs = songSearchSource.filter { it.searchIdentityKey() !in seenKeys }
        val snapshotMatches = mainViewModel
            .filterSongsBySearchSnapshot(remainingSongs, trimmedQuery)
            .asSequence()
            .filter { it.searchIdentityKey() !in seenKeys }
            .take(80 - current.size)
            .toList()
        snapshotMatches.forEach { song ->
            current += SongSearchResult(song = song)
            seenKeys += song.searchIdentityKey()
        }
        if (snapshotMatches.isNotEmpty()) value = current.toList()
        for (song in remainingSongs) {
            if (current.size >= 80) break
            if (song.searchIdentityKey() in seenKeys) continue
            val snippet = mainViewModel.repository
                .getLyrics(song, lyricSourceMode)
                .firstMatchingLyricSnippet(trimmedQuery)
                ?: continue
            current += SongSearchResult(song = song, lyricSnippet = snippet)
            seenKeys += song.searchIdentityKey()
            value = current.toList()
        }
        saveCachedSongSearchResults(context, trimmedQuery, filter, current)
    }
    val albumResults = remember(albums, trimmedQuery, filter, duplicatesOnlyActive) {
        if (duplicatesOnlyActive || filter !in listOf(SearchFilter.All, SearchFilter.Albums) || trimmedQuery.isBlank()) emptyList()
        else albums.filter { it.matchesLibrarySearch(trimmedQuery) }.take(24)
    }
    val artistResults = remember(songs, trimmedQuery, filter, duplicatesOnlyActive) {
        if (duplicatesOnlyActive || filter !in listOf(SearchFilter.All, SearchFilter.Artists) || trimmedQuery.isBlank()) {
            emptyList()
        } else {
            songs.asSequence()
                .flatMap { song -> com.ella.music.data.splitArtistNames(song.artist).map { it to song } }
                .filter { (artist, _) -> artist.isNotBlank() && artist.contains(trimmedQuery, ignoreCase = true) }
                .groupBy({ it.first }, { it.second })
                .entries
                .sortedBy { it.key.lowercase() }
                .take(24)
                .map { (artist, artistSongs) ->
                    ArtistSearchResult(
                        artist = Artist(
                            name = artist,
                            songCount = artistSongs.size,
                            albumCount = artistSongs.map { it.album }.distinct().size
                        ),
                        representativeSong = artistSongs.firstOrNull(),
                        participatedAlbumCount = artistSongs.map { it.albumIdentityId() }.distinct().size
                    )
                }
        }
    }
    val playlistResults = remember(playlists, trimmedQuery, filter, duplicatesOnlyActive) {
        if (duplicatesOnlyActive || filter !in listOf(SearchFilter.All, SearchFilter.Playlists) || trimmedQuery.isBlank()) {
            emptyList()
        } else {
            playlists.filter { playlist ->
                playlist.name.contains(trimmedQuery, ignoreCase = true) ||
                    playlist.songs.any { song ->
                        song.title.contains(trimmedQuery, ignoreCase = true) ||
                            song.artist.contains(trimmedQuery, ignoreCase = true) ||
                            song.album.contains(trimmedQuery, ignoreCase = true)
                    }
            }.take(24)
        }
    }
    val categoryFilterType = when (filter) {
        SearchFilter.Folders -> "folder"
        SearchFilter.Composers -> "composer"
        SearchFilter.Lyricists -> "lyricist"
        SearchFilter.Genres -> "genre"
        SearchFilter.Years -> "year"
        else -> null
    }
    val categoryResults = remember(songs, trimmedQuery, filter, categoryFilterType, duplicatesOnlyActive) {
        if (duplicatesOnlyActive || categoryFilterType == null || trimmedQuery.isBlank()) {
            emptyList()
        } else {
            mainViewModel.getMetadataCategoryItems(categoryFilterType)
                .filter { it.name.contains(trimmedQuery, ignoreCase = true) }
                .take(24)
        }
    }

    fun commitSearch(text: String = query) {
        val value = text.trim()
        if (value.isBlank()) return
        history = saveSearchHistory(context, value)
    }

    BackHandler(onBack = onBack)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ellaPageBackground())
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
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
            EllaSearchBar(
                query = query,
                onQueryChange = {
                    query = it
                },
                onSearch = { commitSearch() },
                placeholder = stringResource(R.string.library_search_page_placeholder),
                modifier = Modifier.weight(1f),
                autoFocus = false
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SearchFilter.entries.forEach { item ->
                SearchPill(
                    text = stringResource(item.labelRes()),
                    selected = filter == item,
                    onClick = {
                        filter = item
                        if (!item.supportsDuplicateFilter) duplicatesOnly = false
                    }
                )
            }
        }

        if (filter.supportsDuplicateFilter) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                SearchPill(
                    text = stringResource(R.string.library_search_duplicates),
                    selected = duplicatesOnly,
                    onClick = { duplicatesOnly = !duplicatesOnly }
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 128.dp)
        ) {
            if (trimmedQuery.isBlank() && !duplicatesOnlyActive) {
                if (history.isNotEmpty()) {
                    item {
                        SearchSectionHeader(
                            text = stringResource(R.string.library_search_history),
                            actionText = stringResource(R.string.library_search_clear_history),
                            onActionClick = {
                                showClearHistoryConfirm = true
                            }
                        )
                    }
                    items(history, key = { it }) { item ->
                        HistoryRow(
                            text = item,
                            onClick = {
                                query = item
                                filter = SearchFilter.All
                                duplicatesOnly = false
                            },
                            onDelete = {
                                history = history - item
                                saveSearchHistory(context, history)
                            }
                        )
                    }
                } else {
                    item { EmptySearchHint(stringResource(R.string.library_search_empty_hint)) }
                }
            } else {
                if (duplicatesOnlyActive) {
                    item { SearchSectionHeader(stringResource(R.string.library_search_duplicates)) }
                }
                if (songResults.isNotEmpty()) {
                    item {
                        SearchSectionHeader(
                            if (filter == SearchFilter.Lyrics) stringResource(R.string.library_search_lyrics)
                            else stringResource(R.string.library_search_songs)
                        )
                    }
                    items(songResults, key = { "${it.song.id}:${it.song.path}:${it.lyricSnippet.orEmpty()}" }) { result ->
                        Column {
                            SongItem(
                                song = result.song,
                                isCurrent = currentSong?.id == result.song.id,
                                loadCoverArt = mainViewModel::getCoverArtBitmap,
                                loadAudioInfo = mainViewModel::getAudioInfo,
                                showPlayNextInLists = showPlayNextInLists,
                                onPlayNext = {
                                    playerViewModel.playNext(result.song)
                                    Toast.makeText(context, context.getString(R.string.song_more_added_to_play_next), Toast.LENGTH_SHORT).show()
                                },
                                onClick = {
                                    val playbackSongs = songResults.map { it.song }
                                    val index = playbackSongs.indexOfFirst { it.id == result.song.id && it.path == result.song.path }.coerceAtLeast(0)
                                    playerViewModel.setPlaylist(playbackSongs, index)
                                    commitSearch()
                                    onNavigateToPlayer()
                                },
                                onLongClick = { actionSong = result.song },
                                onMore = { actionSong = result.song }
                            )
                            result.lyricSnippet?.let { snippet ->
                                LyricSearchMatchLine(snippet = snippet, query = trimmedQuery)
                            }
                        }
                    }
                }
                if (albumResults.isNotEmpty()) {
                    item { SearchSectionHeader(stringResource(R.string.library_search_albums)) }
                    items(albumResults, key = { it.id }) { album ->
                        AlbumResultRow(
                            album = album,
                            coverModel = mainViewModel.getAlbumArtUri(album.artAlbumId),
                            onClick = {
                                commitSearch()
                                onNavigateToAlbum(album.id)
                            }
                        )
                    }
                }
                if (artistResults.isNotEmpty()) {
                    item { SearchSectionHeader(stringResource(R.string.library_search_artists)) }
                    items(artistResults, key = { it.artist.name }) { result ->
                        ArtistResultRow(
                            result = result,
                            coverModel = result.representativeSong?.coverUrl?.takeIf { it.isNotBlank() }
                                ?: result.representativeSong?.let { mainViewModel.getAlbumArtUri(it.albumId) },
                            onClick = {
                                commitSearch()
                                onNavigateToArtist(result.artist.name)
                            }
                        )
                    }
                }
                if (playlistResults.isNotEmpty()) {
                    item { SearchSectionHeader(stringResource(R.string.library_search_playlists)) }
                    items(playlistResults, key = { it.id }) { playlist ->
                        val playlistSongs = remember(playlist, songs) { mainViewModel.playlistSongs(playlist) }
                        val coverSong = playlistSongs.firstOrNull()
                        PlaylistResultRow(
                            playlist = playlist,
                            coverModel = coverSong?.coverUrl?.takeIf { it.isNotBlank() }
                                ?: coverSong?.albumId?.takeIf { it > 0L }?.let(mainViewModel::getAlbumArtUri),
                            onClick = {
                                commitSearch()
                                onNavigateToPlaylist(playlist.id)
                            }
                        )
                    }
                }
                if (categoryResults.isNotEmpty() && categoryFilterType != null) {
                    item { SearchSectionHeader(stringResource(filter.labelRes())) }
                    items(categoryResults, key = { "$categoryFilterType:${it.name}" }) { item ->
                        MetadataCategoryResultRow(
                            item = item,
                            displayName = if (categoryFilterType == "folder") item.name.substringAfterLast('/').ifBlank { item.name } else item.name,
                            coverModel = item.representativeSong?.coverUrl?.takeIf { it.isNotBlank() }
                                ?: item.coverAlbumIds.firstOrNull()?.let(mainViewModel::getAlbumArtUri),
                            roundCover = categoryFilterType in listOf("composer", "lyricist"),
                            onClick = {
                                commitSearch()
                                onNavigateToMetadataCategory(categoryFilterType, item.name)
                            }
                        )
                    }
                }
                if (
                    songResults.isEmpty() &&
                    albumResults.isEmpty() &&
                    artistResults.isEmpty() &&
                    playlistResults.isEmpty() &&
                    categoryResults.isEmpty()
                ) {
                    item {
                        EmptySearchHint(
                            if (duplicatesOnlyActive) stringResource(R.string.library_search_no_duplicates)
                            else stringResource(R.string.library_search_no_results)
                        )
                    }
                }
            }
        }
    }

    SongMoreActionHost(
        actionSong = actionSong,
        mainViewModel = mainViewModel,
        playerViewModel = playerViewModel,
        onDismissAction = { actionSong = null },
        onNavigateToAlbum = onNavigateToAlbum,
        onNavigateToArtist = onNavigateToArtist
    )

    ConfirmDangerDialog(
        show = showClearHistoryConfirm,
        title = stringResource(R.string.library_search_clear_history_title),
        message = stringResource(R.string.library_search_clear_history_message),
        confirmText = stringResource(R.string.common_clear),
        onDismiss = { showClearHistoryConfirm = false },
        onConfirm = {
            history = emptyList()
            saveSearchHistory(context, emptyList())
            showClearHistoryConfirm = false
        }
    )
}

private fun SearchFilter.labelRes(): Int = when (this) {
    SearchFilter.All -> R.string.library_search_all
    SearchFilter.Songs -> R.string.library_search_songs
    SearchFilter.Artists -> R.string.library_search_artists
    SearchFilter.Albums -> R.string.library_search_albums
    SearchFilter.Playlists -> R.string.library_search_playlists
    SearchFilter.Folders -> R.string.library_search_folders
    SearchFilter.Composers -> R.string.library_search_composers
    SearchFilter.Lyricists -> R.string.library_search_lyricists
    SearchFilter.Lyrics -> R.string.library_search_lyrics
    SearchFilter.Genres -> R.string.library_search_genres
    SearchFilter.Years -> R.string.library_search_years
}
