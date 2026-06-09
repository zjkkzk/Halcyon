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
    onBack: () -> Unit,
    onNavigateToAlbum: (Long) -> Unit,
    onNavigateToArtist: (String) -> Unit,
    onNavigateToPlayer: () -> Unit
) {
    val context = LocalContext.current
    val songs by mainViewModel.songs.collectAsState()
    val albums by mainViewModel.albums.collectAsState()
    val currentSong by playerViewModel.currentSong.collectAsState()
    val lyricSourceMode by mainViewModel.settingsManager.lyricSourceMode.collectAsState(initial = SettingsManager.LYRIC_SOURCE_AUTO)
    val showPlayNextInLists by mainViewModel.settingsManager.showPlayNextInLists.collectAsState(initial = false)
    var query by remember(initialQuery) { mutableStateOf(initialQuery.orEmpty()) }
    var filter by remember(initialFilterType) { mutableStateOf(SearchFilter.fromRouteType(initialFilterType)) }
    var actionSong by remember { mutableStateOf<Song?>(null) }
    var history by remember { mutableStateOf(loadSearchHistory(context)) }

    val trimmedQuery = query.trim()
    val duplicateSongs = remember(songs) { songs.duplicateTitleAlbumSongs() }
    val immediateSongResults = remember(songs, trimmedQuery, filter, duplicateSongs) {
        when {
            filter == SearchFilter.Duplicates -> duplicateSongs.map { SongSearchResult(it) }
            trimmedQuery.isBlank() || filter !in listOf(SearchFilter.All, SearchFilter.Songs) -> emptyList()
            else -> songs
                .filter { it.matchesFullTagSearch(trimmedQuery) }
                .take(80)
                .map { SongSearchResult(it) }
        }
    }
    val cachedSongResults = remember(context, songs, trimmedQuery, filter) {
        loadCachedSongSearchResults(context, songs, trimmedQuery, filter)
    }
    val songResults by produceState(
        initialValue = cachedSongResults.ifEmpty { immediateSongResults },
        songs,
        trimmedQuery,
        filter,
        duplicateSongs,
        cachedSongResults,
        lyricSourceMode
    ) {
        value = cachedSongResults.ifEmpty { immediateSongResults }
        if (trimmedQuery.isBlank() || filter !in listOf(SearchFilter.All, SearchFilter.Songs) || filter == SearchFilter.Duplicates) {
            return@produceState
        }
        val current = cachedSongResults.ifEmpty { immediateSongResults }.toMutableList()
        val seenKeys = current.map { it.song.searchIdentityKey() }.toMutableSet()
        for (song in songs) {
            if (current.size >= 80) break
            if (song.searchIdentityKey() in seenKeys) continue
            if (mainViewModel.songMatchesSearchSnapshot(song, trimmedQuery)) {
                current += SongSearchResult(song = song)
                seenKeys += song.searchIdentityKey()
                value = current.toList()
                continue
            }
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
    val albumResults = remember(albums, trimmedQuery, filter) {
        if (filter !in listOf(SearchFilter.All, SearchFilter.Albums) || trimmedQuery.isBlank()) emptyList()
        else albums.filter { it.matchesLibrarySearch(trimmedQuery) }.take(24)
    }
    val artistResults = remember(songs, trimmedQuery, filter) {
        if (filter !in listOf(SearchFilter.All, SearchFilter.Artists) || trimmedQuery.isBlank()) {
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
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = MiuixIcons.Regular.Back,
                    contentDescription = stringResource(R.string.common_back),
                    tint = MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp)
                )
            }
            EllaSearchBar(
                query = query,
                onQueryChange = {
                    query = it
                    if (filter == SearchFilter.Duplicates) filter = SearchFilter.All
                },
                onSearch = { commitSearch() },
                placeholder = stringResource(R.string.library_search_page_placeholder),
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SearchPill(
                text = stringResource(R.string.library_search_all),
                selected = filter == SearchFilter.All,
                onClick = { filter = SearchFilter.All }
            )
            SearchPill(
                text = stringResource(R.string.library_search_songs),
                selected = filter == SearchFilter.Songs,
                onClick = { filter = SearchFilter.Songs }
            )
            SearchPill(
                text = stringResource(R.string.library_search_albums),
                selected = filter == SearchFilter.Albums,
                onClick = { filter = SearchFilter.Albums }
            )
            SearchPill(
                text = stringResource(R.string.library_search_artists),
                selected = filter == SearchFilter.Artists,
                onClick = { filter = SearchFilter.Artists }
            )
            SearchPill(
                text = stringResource(R.string.library_search_duplicates),
                selected = filter == SearchFilter.Duplicates,
                onClick = {
                    filter = if (filter == SearchFilter.Duplicates) SearchFilter.All else SearchFilter.Duplicates
                    if (filter == SearchFilter.Duplicates) query = ""
                }
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 128.dp)
        ) {
            if (trimmedQuery.isBlank() && filter != SearchFilter.Duplicates) {
                if (history.isNotEmpty()) {
                    item {
                        SearchSectionHeader(
                            text = stringResource(R.string.library_search_history),
                            actionText = stringResource(R.string.library_search_clear_history),
                            onActionClick = {
                                history = emptyList()
                                saveSearchHistory(context, emptyList())
                            }
                        )
                    }
                    items(history, key = { it }) { item ->
                        HistoryRow(
                            text = item,
                            onClick = {
                                query = item
                                filter = SearchFilter.All
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
                if (filter == SearchFilter.Duplicates) {
                    item { SearchSectionHeader(stringResource(R.string.library_search_duplicates)) }
                }
                if (songResults.isNotEmpty()) {
                    item { SearchSectionHeader(stringResource(R.string.library_search_songs)) }
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
                if (songResults.isEmpty() && albumResults.isEmpty() && artistResults.isEmpty()) {
                    item {
                        EmptySearchHint(
                            if (filter == SearchFilter.Duplicates) stringResource(R.string.library_search_no_duplicates)
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
}
