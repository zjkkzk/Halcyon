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
import androidx.compose.runtime.LaunchedEffect
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
import com.ella.music.data.model.Album
import com.ella.music.data.model.Artist
import com.ella.music.data.model.Song
import com.ella.music.data.model.UserPlaylist
import com.ella.music.data.model.albumIdentityId
import com.ella.music.data.model.matchesFullTagSearch
import com.ella.music.data.tagIdentityKey
import com.ella.music.ui.components.AddToPlaylistSheet
import com.ella.music.ui.components.EllaSearchBar
import com.ella.music.ui.components.ConfirmDangerDialog
import com.ella.music.ui.components.EllaMiuixBottomSheet
import com.ella.music.ui.components.EllaMiuixMenuItem
import com.ella.music.ui.components.SongItem
import com.ella.music.ui.components.SongMoreActionHost
import com.ella.music.ui.components.SongSelectionActionRow
import com.ella.music.ui.components.ellaPageBackground
import com.ella.music.ui.components.requestPinnedEllaShortcut
import com.ella.music.ui.components.shareLocalSongs
import com.ella.music.ui.navigation.Screen
import com.ella.music.ui.playlist.CreatePlaylistDialog
import com.ella.music.viewmodel.MainViewModel
import com.ella.music.viewmodel.MetadataCategoryItem
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
    autoFocusSearch: Boolean = false,
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
    var actionTarget by remember { mutableStateOf<SearchActionTarget?>(null) }
    var playlistPickerSongs by remember { mutableStateOf<List<Song>?>(null) }
    var createPlaylistSongs by remember { mutableStateOf<List<Song>?>(null) }
    var history by remember { mutableStateOf(loadSearchHistory(context)) }
    var showClearHistoryConfirm by remember { mutableStateOf(false) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedSongKeys by remember { mutableStateOf<Set<String>>(emptySet()) }

    val trimmedQuery = query.trim()
    val songSelectionAvailable = filter in listOf(SearchFilter.Songs, SearchFilter.Lyrics)
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
                .map { SongSearchResult(it, matches = it.directSearchMatches(trimmedQuery)) }
                .toList()
            else -> songSearchSource
                .asSequence()
                .filter { it.matchesFullTagSearch(trimmedQuery) }
                .map { SongSearchResult(it, matches = it.directSearchMatches(trimmedQuery)) }
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
            .toList()
        snapshotMatches.forEach { song ->
            val tagInfo = mainViewModel.getSongTagInfo(song)
            current += SongSearchResult(
                song = song,
                matches = song.directSearchMatches(trimmedQuery, tagInfo = tagInfo, includeSnapshotTag = true)
            )
            seenKeys += song.searchIdentityKey()
        }
        if (snapshotMatches.isNotEmpty()) value = current.toList()
        for (song in remainingSongs) {
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
    val albumResults = remember(albums, trimmedQuery, duplicatesOnlyActive) {
        if (duplicatesOnlyActive || trimmedQuery.isBlank()) emptyList()
        else albums.filter { it.matchesLibrarySearch(trimmedQuery) }
    }
    val artistResults = remember(songs, trimmedQuery, duplicatesOnlyActive) {
        if (duplicatesOnlyActive || trimmedQuery.isBlank()) {
            emptyList()
        } else {
            songs.asSequence()
                .flatMap { song -> com.ella.music.data.splitArtistNames(song.artist).map { it to song } }
                .filter { (artist, _) -> artist.isNotBlank() && artist.contains(trimmedQuery, ignoreCase = true) }
                .groupBy({ it.first }, { it.second })
                .entries
                .sortedBy { it.key.lowercase() }
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
    val playlistResults = remember(playlists, trimmedQuery, duplicatesOnlyActive) {
        if (duplicatesOnlyActive || trimmedQuery.isBlank()) {
            emptyList()
        } else {
            playlists.filter { playlist ->
                playlist.name.contains(trimmedQuery, ignoreCase = true) ||
                    playlist.songs.any { song ->
                        song.title.contains(trimmedQuery, ignoreCase = true) ||
                            song.artist.contains(trimmedQuery, ignoreCase = true) ||
                            song.album.contains(trimmedQuery, ignoreCase = true)
                    }
            }
        }
    }
    val allCategoryTypes = remember { listOf("folder", "composer", "lyricist", "genre", "year") }
    val allCategoryResultsByType = remember(songs, trimmedQuery, duplicatesOnlyActive) {
        if (duplicatesOnlyActive || trimmedQuery.isBlank()) {
            emptyMap()
        } else {
            allCategoryTypes.associateWith { type ->
                mainViewModel.getMetadataCategoryItems(type)
                    .filter { it.name.contains(trimmedQuery, ignoreCase = true) }
            }
        }
    }
    val categoryResultsByType = remember(filter, allCategoryResultsByType) {
        when (filter) {
            SearchFilter.All -> allCategoryResultsByType
            SearchFilter.Folders -> allCategoryResultsByType.filterKeys { it == "folder" }
            SearchFilter.Composers -> allCategoryResultsByType.filterKeys { it == "composer" }
            SearchFilter.Lyricists -> allCategoryResultsByType.filterKeys { it == "lyricist" }
            SearchFilter.Genres -> allCategoryResultsByType.filterKeys { it == "genre" }
            SearchFilter.Years -> allCategoryResultsByType.filterKeys { it == "year" }
            else -> emptyMap()
        }
    }
    val categoryResultsCount = remember(categoryResultsByType) { categoryResultsByType.values.sumOf { it.size } }
    val visibleAlbumCount = if (filter in listOf(SearchFilter.All, SearchFilter.Albums)) albumResults.size else 0
    val visibleArtistCount = if (filter in listOf(SearchFilter.All, SearchFilter.Artists)) artistResults.size else 0
    val visiblePlaylistCount = if (filter in listOf(SearchFilter.All, SearchFilter.Playlists)) playlistResults.size else 0
    val visibleResultCount = songResults.size + visibleAlbumCount + visibleArtistCount + visiblePlaylistCount + categoryResultsCount

    val songResultGroups = remember(songResults, filter) {
        songResults
            .flatMap { it.toSearchGroupEntries(filter) }
            .groupBy({ it.first }, { it.second })
            .map { it.key to it.value }
    }

    LaunchedEffect(filter, trimmedQuery) {
        selectionMode = false
        selectedSongKeys = emptySet()
    }

    fun toggleSongSelection(song: Song) {
        val key = song.searchIdentityKey()
        selectedSongKeys = if (key in selectedSongKeys) {
            selectedSongKeys - key
        } else {
            selectedSongKeys + key
        }
    }

    fun toggleSelectAllSongResults() {
        val allKeys = songResults.mapTo(mutableSetOf()) { it.song.searchIdentityKey() }
        selectedSongKeys = if (allKeys.isNotEmpty() && allKeys.all { it in selectedSongKeys }) {
            emptySet()
        } else {
            allKeys
        }
    }

    fun commitSearch(text: String = query) {
        val value = text.trim()
        if (value.isBlank()) return
        history = saveSearchHistory(context, value)
    }

    fun songsForActionTarget(target: SearchActionTarget): List<Song> = when (target) {
        is SearchActionTarget.AlbumTarget -> mainViewModel.getSongsForAlbum(target.album.id)
        is SearchActionTarget.ArtistTarget -> mainViewModel.getSongsForArtist(target.artist.name)
        is SearchActionTarget.PlaylistTarget -> mainViewModel.playlistSongs(target.playlist)
        is SearchActionTarget.CategoryTarget -> mainViewModel.getSongsForMetadataCategory(target.type, target.item.name)
    }

    fun shortcutRouteForActionTarget(target: SearchActionTarget): String = when (target) {
        is SearchActionTarget.AlbumTarget -> Screen.AlbumDetail.createRoute(target.album.id)
        is SearchActionTarget.ArtistTarget -> Screen.ArtistDetail.createRoute(target.artist.name)
        is SearchActionTarget.PlaylistTarget -> Screen.PlaylistDetail.createRoute(target.playlist.id)
        is SearchActionTarget.CategoryTarget -> Screen.MetadataCategoryDetail.createRoute(target.type, target.item.name)
    }

    fun shortcutIdForActionTarget(target: SearchActionTarget): String = when (target) {
        is SearchActionTarget.AlbumTarget -> "album_${target.album.id}"
        is SearchActionTarget.ArtistTarget -> "artist_${target.artist.name.tagIdentityKey()}"
        is SearchActionTarget.PlaylistTarget -> "playlist_${target.playlist.id}"
        is SearchActionTarget.CategoryTarget -> "category_${target.type}_${target.item.name.tagIdentityKey()}"
    }

    LaunchedEffect(initialQuery) {
        initialQuery?.trim()?.takeIf { it.isNotBlank() }?.let { value ->
            history = saveSearchHistory(context, value)
        }
    }

    BackHandler {
        if (selectionMode) {
            selectionMode = false
            selectedSongKeys = emptySet()
        } else {
            onBack()
        }
    }

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
                autoFocus = autoFocusSearch
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
                val baseLabel = stringResource(item.labelRes())
                val itemCount = when (item) {
                    SearchFilter.All -> songResults.size + albumResults.size + artistResults.size + playlistResults.size + allCategoryResultsByType.values.sumOf { it.size }
                    SearchFilter.Songs -> if (filter in listOf(SearchFilter.All, SearchFilter.Songs) || duplicatesOnlyActive) songResults.size else 0
                    SearchFilter.Lyrics -> if (filter == SearchFilter.Lyrics) songResults.size else 0
                    SearchFilter.Albums -> albumResults.size
                    SearchFilter.Artists -> artistResults.size
                    SearchFilter.Playlists -> playlistResults.size
                    SearchFilter.Folders -> allCategoryResultsByType["folder"].orEmpty().size
                    SearchFilter.Composers -> allCategoryResultsByType["composer"].orEmpty().size
                    SearchFilter.Lyricists -> allCategoryResultsByType["lyricist"].orEmpty().size
                    SearchFilter.Genres -> allCategoryResultsByType["genre"].orEmpty().size
                    SearchFilter.Years -> allCategoryResultsByType["year"].orEmpty().size
                }
                SearchPill(
                    text = if ((trimmedQuery.isNotBlank() || duplicatesOnlyActive) && itemCount > 0) "$baseLabel ($itemCount)" else baseLabel,
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

        if (selectionMode) {
            SongSelectionActionRow(
                selectedCount = selectedSongKeys.size,
                totalCount = songResults.size,
                rangeEnabled = false,
                allSelected = songResults.isNotEmpty() && songResults.all { it.song.searchIdentityKey() in selectedSongKeys },
                onRangeSelect = {},
                onSelectAll = ::toggleSelectAllSongResults,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
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
                    songResultGroups.forEach { (labelRes, entries) ->
                        item {
                            SearchSectionHeader(
                                stringResource(labelRes) + " (${entries.size})"
                            )
                        }
                        items(entries, key = { entry ->
                            val result = entry.result
                            "${result.song.id}:${result.song.path}:${result.lyricSnippet.orEmpty()}:$labelRes:${entry.keySuffix}"
                        }) { entry ->
                            val result = entry.result
                            val selected = result.song.searchIdentityKey() in selectedSongKeys
                            Column {
                                SongItem(
                                    song = result.song,
                                    isCurrent = currentSong?.id == result.song.id,
                                    loadCoverArt = mainViewModel::getCoverArtBitmap,
                                    loadAudioInfo = mainViewModel::getAudioInfo,
                                    showPlayNextInLists = showPlayNextInLists,
                                    selectionMode = selectionMode,
                                    selected = selected,
                                    onPlayNext = {
                                        playerViewModel.playNext(result.song)
                                        Toast.makeText(context, context.getString(R.string.song_more_added_to_play_next), Toast.LENGTH_SHORT).show()
                                    },
                                    onClick = {
                                        if (selectionMode) {
                                            toggleSongSelection(result.song)
                                        } else {
                                            val playbackSongs = songResults.map { it.song }
                                            val index = playbackSongs.indexOfFirst { it.id == result.song.id && it.path == result.song.path }.coerceAtLeast(0)
                                            playerViewModel.setPlaylist(playbackSongs, index)
                                            commitSearch()
                                            onNavigateToPlayer()
                                        }
                                    },
                                    onLongClick = {
                                        if (songSelectionAvailable) {
                                            selectionMode = true
                                            selectedSongKeys = selectedSongKeys + result.song.searchIdentityKey()
                                        } else {
                                            actionSong = result.song
                                        }
                                    },
                                    onMore = { actionSong = result.song }
                                )
                                result.lyricSnippet?.let { snippet ->
                                    LyricSearchMatchLine(snippet = snippet, query = trimmedQuery)
                                } ?: entry.match?.let { match ->
                                    SongSearchMatchLine(match = match, query = trimmedQuery)
                                }
                            }
                        }
                    }
                }
                if (albumResults.isNotEmpty() && filter in listOf(SearchFilter.All, SearchFilter.Albums)) {
                    item { SearchSectionHeader(stringResource(R.string.library_search_albums) + " (${albumResults.size})") }
                    items(albumResults, key = { it.id }) { album ->
                        AlbumResultRow(
                            album = album,
                            coverModel = mainViewModel.getAlbumArtUri(album.artAlbumId),
                            query = trimmedQuery,
                            onClick = {
                                commitSearch()
                                onNavigateToAlbum(album.id)
                            },
                            onLongClick = { actionTarget = SearchActionTarget.AlbumTarget(album) }
                        )
                    }
                }
                if (artistResults.isNotEmpty() && filter in listOf(SearchFilter.All, SearchFilter.Artists)) {
                    item { SearchSectionHeader(stringResource(R.string.library_search_artists) + " (${artistResults.size})") }
                    items(artistResults, key = { it.artist.name }) { result ->
                        ArtistResultRow(
                            result = result,
                            coverModel = result.representativeSong?.coverUrl?.takeIf { it.isNotBlank() }
                                ?: result.representativeSong?.let { mainViewModel.getAlbumArtUri(it.albumId) },
                            query = trimmedQuery,
                            onClick = {
                                commitSearch()
                                onNavigateToArtist(result.artist.name)
                            },
                            onLongClick = { actionTarget = SearchActionTarget.ArtistTarget(result.artist) }
                        )
                    }
                }
                if (playlistResults.isNotEmpty() && filter in listOf(SearchFilter.All, SearchFilter.Playlists)) {
                    item { SearchSectionHeader(stringResource(R.string.library_search_playlists) + " (${playlistResults.size})") }
                    items(playlistResults, key = { it.id }) { playlist ->
                        val playlistSongs = remember(playlist, songs) { mainViewModel.playlistSongs(playlist) }
                        val coverSong = playlistSongs.firstOrNull()
                        PlaylistResultRow(
                            playlist = playlist,
                            coverModel = coverSong?.coverUrl?.takeIf { it.isNotBlank() }
                                ?: coverSong?.albumId?.takeIf { it > 0L }?.let(mainViewModel::getAlbumArtUri),
                            query = trimmedQuery,
                            onClick = {
                                commitSearch()
                                onNavigateToPlaylist(playlist.id)
                            },
                            onLongClick = { actionTarget = SearchActionTarget.PlaylistTarget(playlist) }
                        )
                    }
                }
                categoryResultsByType.forEach { (categoryType, results) ->
                    if (results.isNotEmpty()) {
                        item { SearchSectionHeader(stringResource(categoryType.labelRes()) + " (${results.size})") }
                        items(results, key = { "$categoryType:${it.name}" }) { item ->
                            MetadataCategoryResultRow(
                                item = item,
                                displayName = if (categoryType == "folder") item.name.substringAfterLast('/').ifBlank { item.name } else item.name,
                                coverModel = item.representativeSong?.coverUrl?.takeIf { it.isNotBlank() }
                                    ?: item.coverAlbumIds.firstOrNull()?.let(mainViewModel::getAlbumArtUri),
                                roundCover = categoryType in listOf("composer", "lyricist"),
                                query = trimmedQuery,
                                onClick = {
                                    commitSearch()
                                    onNavigateToMetadataCategory(categoryType, item.name)
                                },
                                onLongClick = { actionTarget = SearchActionTarget.CategoryTarget(categoryType, item) }
                            )
                        }
                    }
                }
                if (
                    songResults.isEmpty() &&
                    visibleResultCount == 0
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

    actionTarget?.let { target ->
        EllaMiuixBottomSheet(
            show = true,
            enableNestedScroll = false,
            title = target.title,
            onDismissRequest = { actionTarget = null }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                EllaMiuixMenuItem(
                    text = stringResource(R.string.common_share),
                    onClick = {
                        shareLocalSongs(context, songsForActionTarget(target))
                        actionTarget = null
                    }
                )
                EllaMiuixMenuItem(
                    text = stringResource(R.string.song_more_add_to_playlist),
                    onClick = {
                        playlistPickerSongs = songsForActionTarget(target)
                        actionTarget = null
                    }
                )
                EllaMiuixMenuItem(
                    text = stringResource(R.string.common_add_to_queue),
                    onClick = {
                        playerViewModel.addToPlaylist(songsForActionTarget(target))
                        Toast.makeText(context, context.getString(R.string.song_more_added_to_queue), Toast.LENGTH_SHORT).show()
                        actionTarget = null
                    }
                )
                EllaMiuixMenuItem(
                    text = stringResource(R.string.song_more_play_next),
                    onClick = {
                        playerViewModel.playNext(songsForActionTarget(target))
                        Toast.makeText(context, context.getString(R.string.song_more_added_to_play_next), Toast.LENGTH_SHORT).show()
                        actionTarget = null
                    }
                )
                EllaMiuixMenuItem(
                    text = stringResource(R.string.common_add_desktop_shortcut),
                    onClick = {
                        val ok = requestPinnedEllaShortcut(
                            context = context,
                            id = shortcutIdForActionTarget(target),
                            label = target.title,
                            route = shortcutRouteForActionTarget(target)
                        )
                        Toast.makeText(
                            context,
                            if (ok) context.getString(R.string.playlist_shortcut_requested, target.title) else context.getString(R.string.playlist_shortcut_unsupported),
                            Toast.LENGTH_SHORT
                        ).show()
                        actionTarget = null
                    }
                )
            }
        }
    }

    playlistPickerSongs?.let { songsToAdd ->
        EllaMiuixBottomSheet(
            show = true,
            enableNestedScroll = false,
            title = stringResource(R.string.song_more_add_to_playlist_title),
            onDismissRequest = { playlistPickerSongs = null }
        ) {
            AddToPlaylistSheet(
                playlists = playlists,
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
                    playlistPickerSongs = null
                    Toast.makeText(context, context.getString(R.string.player_added_to_playlists, selectedPlaylists.size), Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    createPlaylistSongs?.let { songsToAdd ->
        CreatePlaylistDialog(
            onDismiss = { createPlaylistSongs = null },
            onCreate = { playlistName ->
                mainViewModel.createPlaylist(playlistName) { playlist ->
                    if (playlist != null) mainViewModel.addSongsToPlaylist(playlist.id, songsToAdd)
                }
                createPlaylistSongs = null
            }
        )
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

private fun String.labelRes(): Int = when (this) {
    "folder" -> R.string.library_search_folders
    "composer" -> R.string.library_search_composers
    "lyricist" -> R.string.library_search_lyricists
    "genre" -> R.string.library_search_genres
    "year" -> R.string.library_search_years
    else -> R.string.library_search_all
}

private sealed interface SearchActionTarget {
    val title: String

    data class AlbumTarget(val album: Album) : SearchActionTarget {
        override val title: String = album.name
    }

    data class ArtistTarget(val artist: Artist) : SearchActionTarget {
        override val title: String = artist.name
    }

    data class PlaylistTarget(val playlist: UserPlaylist) : SearchActionTarget {
        override val title: String = playlist.name
    }

    data class CategoryTarget(val type: String, val item: MetadataCategoryItem) : SearchActionTarget {
        override val title: String = item.name.substringAfterLast('/').ifBlank { item.name }
    }
}
