package com.ella.music.ui.home

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.data.SettingsManager
import com.ella.music.data.splitArtistNames
import com.ella.music.data.tagIdentityKey
import com.ella.music.ui.components.EllaSmallTopAppBar
import com.ella.music.ui.components.ellaPageBackground
import com.ella.music.ui.navigation.Screen
import com.ella.music.viewmodel.MainViewModel
import com.ella.music.viewmodel.PlayerViewModel
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    mainViewModel: MainViewModel,
    playerViewModel: PlayerViewModel,
    onNavigateToLibrary: () -> Unit,
    onNavigateToArtist: () -> Unit,
    onNavigateToAlbum: () -> Unit,
    onNavigateToFolder: () -> Unit,
    onNavigateToPlaylists: () -> Unit,
    onNavigateToLxOnline: () -> Unit,
    onNavigateToWebDav: () -> Unit,
    onNavigateToAnalytics: () -> Unit,
    onNavigateToAiChat: () -> Unit = {},
    onNavigateToMetadataCategory: (String) -> Unit,
    onNavigateToPlayer: () -> Unit,
    onNavigateToSettings: () -> Unit = {}
) {
    val songs by mainViewModel.songs.collectAsState()
    val albums by mainViewModel.albums.collectAsState()
    val playlists by mainViewModel.playlists.collectAsState()
    val history by mainViewModel.playbackHistory.collectAsState()
    val currentSong by playerViewModel.currentSong.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsManager = remember(context) { SettingsManager.getInstance(context) }
    val openPlayerOnPlay by settingsManager.openPlayerOnPlay.collectAsState(initial = false)
    val showAlbumArtists by settingsManager.showAlbumArtists.collectAsState(initial = false)
    val tagIgnoreCase by settingsManager.tagIgnoreCase.collectAsState(initial = false)
    val homeDailyMixVisible by settingsManager.homeDailyMixVisible.collectAsState(initial = true)
    val homeAiMixVisible by settingsManager.homeAiMixVisible.collectAsState(initial = true)
    val homeSectionOrder by settingsManager.homeSectionOrder.collectAsState(initial = SettingsManager.DEFAULT_HOME_SECTION_ORDER)
    val homeHiddenSections by settingsManager.homeHiddenSections.collectAsState(initial = "")
    val homeLibraryTileOrder by settingsManager.homeLibraryTileOrder.collectAsState(initial = SettingsManager.DEFAULT_HOME_LIBRARY_TILE_ORDER)
    val homeHiddenLibraryTiles by settingsManager.homeHiddenLibraryTiles.collectAsState(initial = "")
    val homeTilePinButtonsVisible by settingsManager.homeTilePinButtonsVisible.collectAsState(initial = false)
    val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    var aiPlaylistLoading by remember { mutableStateOf(false) }
    val pageBackground = ellaPageBackground()
    val cardText = if (isDark) Color.White else Color(0xFF15151A)
    val featuredSongs = remember(songs) {
        when {
            songs.size <= 3 -> songs
            else -> listOf(songs.first(), songs[songs.size / 2], songs.last())
        }
    }
    val artistCount = remember(songs, showAlbumArtists, tagIgnoreCase) {
        songs
            .flatMap {
                if (showAlbumArtists) splitArtistNames(it.artist) + splitArtistNames(it.albumArtist)
                else splitArtistNames(it.artist)
            }
            .distinctBy { it.tagIdentityKey() }
            .size
    }
    val folderCount = remember(songs) { mainViewModel.getMetadataCategoryItems("folder").size }
    val genreCount = remember(songs) { mainViewModel.getMetadataCategoryItems("genre").size }
    val yearCount = remember(songs) { mainViewModel.getMetadataCategoryItems("year").size }
    val composerCount = remember(songs) { mainViewModel.getMetadataCategoryItems("composer").size }
    val lyricistCount = remember(songs) { mainViewModel.getMetadataCategoryItems("lyricist").size }
    val songsById = remember(songs) { songs.associateBy { it.id } }
    val recentSongs = remember(history, songsById) {
        history
            .distinctBy { it.songId }
            .take(5)
            .mapNotNull { entry -> songsById[entry.songId] }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(pageBackground)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        EllaSmallTopAppBar(
            title = stringResource(R.string.home_title),
            color = pageBackground,
            centeredTitle = true,
            actions = {
                IconButton(onClick = onNavigateToSettings) {
                    Icon(
                        imageVector = MiuixIcons.Regular.Settings,
                        contentDescription = stringResource(R.string.tab_settings),
                        tint = MiuixTheme.colorScheme.onSurface
                    )
                }
            }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            if (homeDailyMixVisible) {
                DailyMixCard(
                    songs = songs,
                    featuredSongs = featuredSongs,
                    currentSongTitle = currentSong?.title,
                    mainViewModel = mainViewModel,
                    onPlay = {
                        val randomSong = songs.randomOrNull()
                        if (randomSong != null) {
                            playerViewModel.setPlaylist(songs, songs.indexOf(randomSong))
                            if (openPlayerOnPlay) onNavigateToPlayer()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )
            }

            if (homeAiMixVisible) {
                SectionTitle(stringResource(R.string.home_ai_section))
                AiMixCard(
                    songCount = songs.size,
                    isLoading = aiPlaylistLoading,
                    onChat = onNavigateToAiChat,
                    onPlay = {
                        if (aiPlaylistLoading) return@AiMixCard
                        if (songs.isEmpty()) {
                            Toast.makeText(context, context.getString(R.string.no_songs_found), Toast.LENGTH_SHORT).show()
                            return@AiMixCard
                        }
                        scope.launch {
                            aiPlaylistLoading = true
                            try {
                                runCatching { mainViewModel.recommendPlaylistWithOpenAi() }
                                    .onSuccess { recommendation ->
                                        playerViewModel.setPlaylist(recommendation.songs, 0)
                                        Toast.makeText(
                                            context,
                                            context.getString(
                                                R.string.home_ai_playlist_started,
                                                recommendation.title,
                                                recommendation.songs.size
                                            ),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        if (openPlayerOnPlay) onNavigateToPlayer()
                                    }
                                    .onFailure { error ->
                                        Toast.makeText(
                                            context,
                                            context.getString(
                                                R.string.home_ai_playlist_failed,
                                                error.message ?: context.getString(R.string.common_unknown)
                                            ),
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                            } finally {
                                aiPlaylistLoading = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            val hiddenSections = remember(homeHiddenSections) { homeHiddenSections.csvIdSet() }
            val sectionOrder = remember(homeSectionOrder) {
                homeSectionOrder.csvIds(SettingsManager.DEFAULT_HOME_SECTION_ORDER)
            }
            val hiddenTiles = remember(homeHiddenLibraryTiles) { homeHiddenLibraryTiles.csvIdSet() }
            val tileOrder = remember(homeLibraryTileOrder) {
                homeLibraryTileOrder.csvIds(SettingsManager.DEFAULT_HOME_LIBRARY_TILE_ORDER)
            }
            val libraryTiles = remember(
                context,
                tileOrder,
                hiddenTiles,
                artistCount,
                albums.size,
                folderCount,
                playlists.size,
                genreCount,
                yearCount,
                composerCount,
                lyricistCount
            ) {
                val all = mapOf(
                    "artist" to HomeTileSpec("artist", context.getString(R.string.category_artist), context.getString(R.string.home_count_artists, artistCount), Color(0xFF118AB2), Screen.Artist.route, onNavigateToArtist),
                    "album" to HomeTileSpec("album", context.getString(R.string.category_album), context.getString(R.string.home_count_albums, albums.size), Color(0xFFFF9F1C), Screen.Album.route, onNavigateToAlbum),
                    "folder" to HomeTileSpec("folder", context.getString(R.string.category_folder), context.getString(R.string.home_count_folders, folderCount), Color(0xFF5E60CE), Screen.MetadataCategory.createRoute("folder")) { onNavigateToMetadataCategory("folder") },
                    "folder_tree" to HomeTileSpec("folder_tree", context.getString(R.string.category_folder_tree), context.getString(R.string.home_browse_nested_folders), Color(0xFF8338EC), Screen.Folder.route, onNavigateToFolder),
                    "playlist" to HomeTileSpec("playlist", context.getString(R.string.category_playlist), context.getString(R.string.home_count_playlists, playlists.size), Color(0xFFEF476F), Screen.Playlists.route, onNavigateToPlaylists),
                    "analytics" to HomeTileSpec("analytics", context.getString(R.string.category_analytics), context.getString(R.string.home_analytics_summary), Color(0xFFE71D36), Screen.Analytics.route, onNavigateToAnalytics),
                    "genre" to HomeTileSpec("genre", context.getString(R.string.category_genre), context.getString(R.string.home_count_genres, genreCount), Color(0xFF06D6A0), Screen.MetadataCategory.createRoute("genre")) { onNavigateToMetadataCategory("genre") },
                    "year" to HomeTileSpec("year", context.getString(R.string.category_year), context.getString(R.string.home_count_folders, yearCount), Color(0xFF4CC9F0), Screen.MetadataCategory.createRoute("year")) { onNavigateToMetadataCategory("year") },
                    "composer" to HomeTileSpec("composer", context.getString(R.string.category_composer), context.getString(R.string.home_count_artists, composerCount), Color(0xFFB5179E), Screen.MetadataCategory.createRoute("composer")) { onNavigateToMetadataCategory("composer") },
                    "lyricist" to HomeTileSpec("lyricist", context.getString(R.string.category_lyricist), context.getString(R.string.home_count_artists, lyricistCount), Color(0xFFFF6D00), Screen.MetadataCategory.createRoute("lyricist")) { onNavigateToMetadataCategory("lyricist") }
                )
                tileOrder.mapNotNull { all[it] }.filterNot { it.id in hiddenTiles }
            }

            sectionOrder.filterNot { it in hiddenSections }.forEach { section ->
                when (section) {
                    "library" -> HomeTileSection(stringResource(R.string.home_library), libraryTiles, context, homeTilePinButtonsVisible)
                    "online" -> {
                        SectionTitle(stringResource(R.string.home_online_music))
                        HomeTileGrid(
                            tiles = listOf(
                                HomeTileSpec("lx", "LX Music", stringResource(R.string.home_import_api_source), Color(0xFF00A896), Screen.LxOnline.route, onNavigateToLxOnline),
                                HomeTileSpec("webdav", "WebDAV", stringResource(R.string.home_connect_cloud_music), Color(0xFF5E60CE), Screen.WebDav.route, onNavigateToWebDav)
                            ),
                            context = context,
                            showPinButtons = homeTilePinButtonsVisible
                        )
                    }
                    "recent" -> {
                        SectionTitle(stringResource(R.string.home_recent))
                        if (recentSongs.isEmpty()) {
                            Text(
                                text = stringResource(R.string.home_no_history),
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                        } else {
                            recentSongs.forEach { song ->
                                CompactRecentSongRow(
                                    song = song,
                                    mainViewModel = mainViewModel,
                                    cardText = cardText,
                                    onClick = {
                                        playerViewModel.playSong(song)
                                        if (openPlayerOnPlay) onNavigateToPlayer()
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(160.dp))
        }
    }
}
