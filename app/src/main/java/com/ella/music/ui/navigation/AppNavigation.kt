package com.ella.music.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.ella.music.data.SettingsManager
import com.ella.music.data.remote.RemoteMusicProvider
import com.ella.music.ui.about.AboutScreen
import com.ella.music.ui.about.UpdateScreen
import com.ella.music.ui.analytics.AnalyticsScreen
import com.ella.music.ui.analytics.LibraryAnalysisScreen
import com.ella.music.ui.analytics.PlaybackHistoryScreen
import com.ella.music.ui.ai.AiChatScreen
import com.ella.music.ui.album.AlbumDetailScreen
import com.ella.music.ui.album.AlbumScreen
import com.ella.music.ui.artist.ArtistListScreen
import com.ella.music.ui.artist.ArtistScreen
import com.ella.music.ui.category.MetadataCategoryDetailScreen
import com.ella.music.ui.category.MetadataCategoryScreen
import com.ella.music.ui.folder.FolderDetailScreen
import com.ella.music.ui.folder.FolderScreen
import com.ella.music.ui.folder.ScanSettingsScreen
import com.ella.music.ui.folder.WebDavScreen
import com.ella.music.ui.home.HomeScreen
import com.ella.music.ui.home.LibraryScreen
import com.ella.music.ui.online.LxOnlineScreen
import com.ella.music.ui.online.LxSourceSettingsScreen
import com.ella.music.ui.online.RemoteDirectoryScreen
import com.ella.music.ui.playlist.PlaylistDetailScreen
import com.ella.music.ui.playlist.PlaylistScreen
import com.ella.music.ui.search.LibrarySearchScreen
import com.ella.music.ui.settings.AudioSettingsScreen
import com.ella.music.ui.settings.EqualizerScreen
import com.ella.music.ui.settings.BackupSettingsScreen
import com.ella.music.ui.settings.LyricFontScreen
import com.ella.music.ui.settings.LyricPluginSourceSettingsScreen
import com.ella.music.ui.settings.LogScreen
import com.ella.music.ui.settings.SettingsDetailScreen
import com.ella.music.ui.settings.SettingsDetailMode
import com.ella.music.ui.settings.SettingsScreen
import com.ella.music.viewmodel.MainViewModel
import com.ella.music.viewmodel.PlayerViewModel

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Library : Screen("library")
    data object LibrarySearch : Screen("library_search?type={type}&keyword={keyword}&focus={focus}") {
        const val baseRoute = "library_search"
        fun createRoute(type: String? = null, keyword: String? = null, focus: Boolean = false): String {
            val params = buildList {
                type?.trim()?.takeIf { it.isNotEmpty() }?.let {
                    add("type=${java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20")}")
                }
                keyword?.trim()?.takeIf { it.isNotEmpty() }?.let {
                    add("keyword=${java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20")}")
                }
                if (focus) add("focus=true")
            }
            return if (params.isEmpty()) baseRoute else "$baseRoute?${params.joinToString("&")}"
        }
    }
    data object Album : Screen("album?fromDock={fromDock}") {
        const val baseRoute = "album"
        fun createRoute(fromDock: Boolean = false) = "$baseRoute?fromDock=$fromDock"
    }
    data object Artist : Screen("artist?fromDock={fromDock}") {
        const val baseRoute = "artist"
        fun createRoute(fromDock: Boolean = false) = "$baseRoute?fromDock=$fromDock"
    }
    data object AlbumDetail : Screen("album/{albumId}") {
        fun createRoute(albumId: Long) = "album/$albumId"
    }
    data object ArtistDetail : Screen("artist/{artistName}") {
        fun createRoute(artistName: String) = "artist/${java.net.URLEncoder.encode(artistName, "UTF-8")}"
    }
    data object Folder : Screen("folder?fromDock={fromDock}") {
        const val baseRoute = "folder"
        fun createRoute(fromDock: Boolean = false) = "$baseRoute?fromDock=$fromDock"
    }
    data object ScanSettings : Screen("scan_settings")
    data object MetadataCategory : Screen("category/{type}") {
        fun createRoute(type: String) = "category/${java.net.URLEncoder.encode(type, "UTF-8")}"
    }
    data object MetadataCategoryDetail : Screen("category/{type}/{name}") {
        fun createRoute(type: String, name: String) =
            "category/${java.net.URLEncoder.encode(type, "UTF-8")}/${java.net.URLEncoder.encode(name, "UTF-8")}"
    }
    data object Playlists : Screen("playlists?fromDock={fromDock}") {
        const val baseRoute = "playlists"
        fun createRoute(fromDock: Boolean = false) = "$baseRoute?fromDock=$fromDock"
    }
    data object PlaylistDetail : Screen("playlist/{playlistId}") {
        fun createRoute(playlistId: String) = "playlist/${java.net.URLEncoder.encode(playlistId, "UTF-8")}"
    }
    data object WebDav : Screen("webdav")
    data object FolderDetail : Screen("folder/{folderPath}") {
        fun createRoute(folderPath: String) = "folder/${java.net.URLEncoder.encode(folderPath, "UTF-8")}"
    }
    data object LibraryAnalysis : Screen("library_analysis")
    data object Settings : Screen("settings")
    data object SettingsDetail : Screen("settings_detail")
    data object LibrarySettings : Screen("library_settings")
    data object IntegrationSettings : Screen("integration_settings")
    data object LyricSettings : Screen("lyric_settings")
    data object LyricPluginSources : Screen("lyric_plugin_sources")
    data object AudioSettings : Screen("audio_settings")
    data object Equalizer : Screen("equalizer")
    data object BackupSettings : Screen("backup_settings")
    data object LyricFont : Screen("lyric_font")
    data object Logs : Screen("logs")
    data object LxOnline : Screen("lx_online")
    data object NavidromeOnline : Screen("navidrome_online")
    data object EmbyOnline : Screen("emby_online")
    data object LxSourceSettings : Screen("lx_source_settings")
    data object Analytics : Screen("analytics")
    data object AiChat : Screen("ai_chat")
    data object PlaybackHistory : Screen("playback_history")
    data object About : Screen("about")
    data object Update : Screen("update")
    data object Player : Screen("player")
}

@Composable
fun AppNavigation(
    navController: NavHostController,
    mainViewModel: MainViewModel,
    playerViewModel: PlayerViewModel,
    modifier: Modifier = Modifier,
    onNavigateToPlayer: () -> Unit = {}
) {
    val bottomDockItems by mainViewModel.settingsManager.bottomDockItems.collectAsState(
        initial = SettingsManager.DEFAULT_BOTTOM_DOCK_ITEMS.split(',')
    )
    fun isDockItem(itemId: String): Boolean = itemId in bottomDockItems

    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = modifier,
        enterTransition = {
            fadeIn(animationSpec = tween(300)) + slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.Start, tween(300)
            )
        },
        exitTransition = { fadeOut(animationSpec = tween(300)) },
        popEnterTransition = { fadeIn(animationSpec = tween(300)) },
        popExitTransition = {
            fadeOut(animationSpec = tween(300)) + slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.End, tween(300)
            )
        }
    ) {
        fun navigateTopLevel(route: String) {
            navController.navigate(route) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }

        composable(Screen.Home.route) {
            HomeScreen(
                mainViewModel = mainViewModel,
                playerViewModel = playerViewModel,
                onNavigateToLibrary = { navigateTopLevel(Screen.Library.route) },
                onNavigateToArtist = { navController.navigate(Screen.Artist.createRoute()) },
                onNavigateToAlbum = { navController.navigate(Screen.Album.createRoute()) },
                onNavigateToFolder = { navController.navigate(Screen.Folder.createRoute()) },
                onNavigateToPlaylists = { navController.navigate(Screen.Playlists.createRoute()) },
                onNavigateToLxOnline = { navController.navigate(Screen.LxOnline.route) },
                onNavigateToNavidrome = { navController.navigate(Screen.NavidromeOnline.route) },
                onNavigateToEmby = { navController.navigate(Screen.EmbyOnline.route) },
                onNavigateToWebDav = { navController.navigate(Screen.WebDav.route) },
                onNavigateToAnalytics = { navController.navigate(Screen.Analytics.route) },
                onNavigateToAiChat = { navController.navigate(Screen.AiChat.route) },
                onNavigateToMetadataCategory = { type -> navController.navigate(Screen.MetadataCategory.createRoute(type)) },
                onNavigateToPlayer = onNavigateToPlayer,
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) }
            )
        }

        composable(Screen.Library.route) {
            LibraryScreen(
                mainViewModel = mainViewModel,
                playerViewModel = playerViewModel,
                onNavigateToPlayer = onNavigateToPlayer,
                onNavigateToAbout = { navController.navigate(Screen.About.route) },
                onNavigateToSearch = { navController.navigate(Screen.LibrarySearch.createRoute()) },
                onNavigateToAlbum = { albumId -> navController.navigate(Screen.AlbumDetail.createRoute(albumId)) },
                onNavigateToArtist = { artistName -> navController.navigate(Screen.ArtistDetail.createRoute(artistName)) }
            )
        }

        composable(
            route = Screen.LibrarySearch.route,
            arguments = listOf(
                navArgument("type") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("keyword") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("focus") {
                    type = NavType.BoolType
                    defaultValue = false
                }
            )
        ) { backStackEntry ->
            LibrarySearchScreen(
                mainViewModel = mainViewModel,
                playerViewModel = playerViewModel,
                initialFilterType = backStackEntry.arguments?.getString("type"),
                initialQuery = backStackEntry.arguments?.getString("keyword"),
                autoFocusSearch = backStackEntry.arguments?.getBoolean("focus") == true,
                showBackButton = false,
                onBack = { navController.popBackStack() },
                onNavigateToAlbum = { albumId -> navController.navigate(Screen.AlbumDetail.createRoute(albumId)) },
                onNavigateToArtist = { artistName -> navController.navigate(Screen.ArtistDetail.createRoute(artistName)) },
                onNavigateToPlaylist = { playlistId -> navController.navigate(Screen.PlaylistDetail.createRoute(playlistId)) },
                onNavigateToMetadataCategory = { type, name ->
                    navController.navigate(Screen.MetadataCategoryDetail.createRoute(type, name))
                },
                onNavigateToPlayer = onNavigateToPlayer
            )
        }

        composable(
            route = Screen.Album.route,
            arguments = listOf(navArgument("fromDock") { type = NavType.BoolType; defaultValue = false })
        ) { backStackEntry ->
            val fromDock = backStackEntry.arguments?.getBoolean("fromDock") == true
            AlbumScreen(
                mainViewModel = mainViewModel,
                playerViewModel = playerViewModel,
                showBackButton = !(fromDock && isDockItem(SettingsManager.BOTTOM_DOCK_ITEM_ALBUM)),
                onBack = { navController.popBackStack() },
                onAlbumClick = { albumId ->
                    navController.navigate(Screen.AlbumDetail.createRoute(albumId))
                }
            )
        }

        composable(
            route = Screen.Artist.route,
            arguments = listOf(navArgument("fromDock") { type = NavType.BoolType; defaultValue = false })
        ) { backStackEntry ->
            val fromDock = backStackEntry.arguments?.getBoolean("fromDock") == true
            ArtistListScreen(
                mainViewModel = mainViewModel,
                playerViewModel = playerViewModel,
                showBackButton = !(fromDock && isDockItem(SettingsManager.BOTTOM_DOCK_ITEM_ARTIST)),
                onBack = { navController.popBackStack() },
                onArtistClick = { artistName ->
                    navController.navigate(Screen.ArtistDetail.createRoute(artistName))
                }
            )
        }

        composable(
            route = Screen.AlbumDetail.route,
            arguments = listOf(navArgument("albumId") { type = NavType.LongType })
        ) { backStackEntry ->
            val albumId = backStackEntry.arguments?.getLong("albumId") ?: 0L
            AlbumDetailScreen(
                albumId = albumId,
                mainViewModel = mainViewModel,
                playerViewModel = playerViewModel,
                onBack = { navController.popBackStack() },
                onNavigateToAlbum = { targetAlbumId ->
                    navController.navigate(Screen.AlbumDetail.createRoute(targetAlbumId))
                },
                onNavigateToArtist = { artistName ->
                    navController.navigate(Screen.ArtistDetail.createRoute(artistName))
                },
                onNavigateToMetadataCategory = { type, name ->
                    navController.navigate(Screen.MetadataCategoryDetail.createRoute(type, name))
                },
                onNavigateToPlayer = onNavigateToPlayer
            )
        }

        composable(
            route = Screen.Folder.route,
            arguments = listOf(navArgument("fromDock") { type = NavType.BoolType; defaultValue = false })
        ) { backStackEntry ->
            val fromDock = backStackEntry.arguments?.getBoolean("fromDock") == true
            FolderScreen(
                mainViewModel = mainViewModel,
                playerViewModel = playerViewModel,
                showBackButton = !(fromDock && isDockItem(SettingsManager.BOTTOM_DOCK_ITEM_FOLDER)),
                onBack = { navController.popBackStack() },
                onNavigateToPlayer = onNavigateToPlayer,
                onNavigateToLibraryAnalysis = { navController.navigate(Screen.LibraryAnalysis.route) },
                onNavigateToScanSettings = { navController.navigate(Screen.ScanSettings.route) },
                onFolderClick = { folderPath ->
                    navController.navigate(Screen.FolderDetail.createRoute(folderPath))
                }
            )
        }

        composable(Screen.ScanSettings.route) {
            ScanSettingsScreen(
                mainViewModel = mainViewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.MetadataCategory.route,
            arguments = listOf(navArgument("type") { type = NavType.StringType })
        ) { backStackEntry ->
            val type = java.net.URLDecoder.decode(
                backStackEntry.arguments?.getString("type").orEmpty(),
                "UTF-8"
            )
            MetadataCategoryScreen(
                type = type,
                mainViewModel = mainViewModel,
                playerViewModel = playerViewModel,
                onBack = { navController.popBackStack() },
                onCategoryClick = { name ->
                    navController.navigate(Screen.MetadataCategoryDetail.createRoute(type, name))
                }
            )
        }

        composable(
            route = Screen.MetadataCategoryDetail.route,
            arguments = listOf(
                navArgument("type") { type = NavType.StringType },
                navArgument("name") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val type = java.net.URLDecoder.decode(
                backStackEntry.arguments?.getString("type").orEmpty(),
                "UTF-8"
            )
            val name = java.net.URLDecoder.decode(
                backStackEntry.arguments?.getString("name").orEmpty(),
                "UTF-8"
            )
            MetadataCategoryDetailScreen(
                type = type,
                name = name,
                mainViewModel = mainViewModel,
                playerViewModel = playerViewModel,
                onBack = { navController.popBackStack() },
                onAlbumClick = { albumId ->
                    navController.navigate(Screen.AlbumDetail.createRoute(albumId))
                },
                onArtistClick = { artistName ->
                    navController.navigate(Screen.ArtistDetail.createRoute(artistName))
                },
                onMetadataCategoryClick = { categoryType, categoryName ->
                    navController.navigate(Screen.MetadataCategoryDetail.createRoute(categoryType, categoryName))
                },
                onNavigateToPlayer = onNavigateToPlayer
            )
        }

        composable(
            route = Screen.Playlists.route,
            arguments = listOf(navArgument("fromDock") { type = NavType.BoolType; defaultValue = false })
        ) { backStackEntry ->
            val fromDock = backStackEntry.arguments?.getBoolean("fromDock") == true
            PlaylistScreen(
                mainViewModel = mainViewModel,
                playerViewModel = playerViewModel,
                showBackButton = !(fromDock && isDockItem(SettingsManager.BOTTOM_DOCK_ITEM_PLAYLISTS)),
                onBack = { navController.popBackStack() },
                onPlaylistClick = { playlistId ->
                    navController.navigate(Screen.PlaylistDetail.createRoute(playlistId))
                }
            )
        }

        composable(
            route = Screen.PlaylistDetail.route,
            arguments = listOf(navArgument("playlistId") { type = NavType.StringType })
        ) { backStackEntry ->
            val playlistId = java.net.URLDecoder.decode(
                backStackEntry.arguments?.getString("playlistId").orEmpty(),
                "UTF-8"
            )
            PlaylistDetailScreen(
                playlistId = playlistId,
                mainViewModel = mainViewModel,
                playerViewModel = playerViewModel,
                onBack = { navController.popBackStack() },
                onNavigateToAlbum = { albumId ->
                    navController.navigate(Screen.AlbumDetail.createRoute(albumId))
                },
                onNavigateToArtist = { artistName ->
                    navController.navigate(Screen.ArtistDetail.createRoute(artistName))
                },
                onNavigateToPlayer = onNavigateToPlayer
            )
        }

        composable(Screen.WebDav.route) {
            WebDavScreen(
                mainViewModel = mainViewModel,
                playerViewModel = playerViewModel,
                onBack = { navController.popBackStack() },
                onNavigateToPlayer = onNavigateToPlayer
            )
        }

        composable(
            route = Screen.ArtistDetail.route,
            arguments = listOf(navArgument("artistName") { type = NavType.StringType })
        ) { backStackEntry ->
            val artistName = java.net.URLDecoder.decode(
                backStackEntry.arguments?.getString("artistName") ?: "",
                "UTF-8"
            )
            ArtistScreen(
                artistName = artistName,
                mainViewModel = mainViewModel,
                playerViewModel = playerViewModel,
                onBack = { navController.popBackStack() },
                onAlbumClick = { albumId -> navController.navigate(Screen.AlbumDetail.createRoute(albumId)) },
                onArtistClick = { targetArtist -> navController.navigate(Screen.ArtistDetail.createRoute(targetArtist)) },
                onMetadataCategoryClick = { type, name ->
                    navController.navigate(Screen.MetadataCategoryDetail.createRoute(type, name))
                },
                onNavigateToPlayer = onNavigateToPlayer
            )
        }

        composable(
            route = Screen.FolderDetail.route,
            arguments = listOf(navArgument("folderPath") { type = NavType.StringType })
        ) { backStackEntry ->
            val folderPath = java.net.URLDecoder.decode(
                backStackEntry.arguments?.getString("folderPath") ?: "",
                "UTF-8"
            )
            FolderDetailScreen(
                folderPath = folderPath,
                mainViewModel = mainViewModel,
                playerViewModel = playerViewModel,
                onBack = { navController.popBackStack() },
                onNavigateToAlbum = { albumId ->
                    navController.navigate(Screen.AlbumDetail.createRoute(albumId))
                },
                onNavigateToArtist = { artistName ->
                    navController.navigate(Screen.ArtistDetail.createRoute(artistName))
                },
                onFolderClick = { childFolderPath ->
                    navController.navigate(Screen.FolderDetail.createRoute(childFolderPath))
                },
                onNavigateToPlayer = onNavigateToPlayer
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateToAbout = { navController.navigate(Screen.About.route) },
                onNavigateToAppearanceSettings = { navController.navigate(Screen.SettingsDetail.route) },
                onNavigateToLibrarySettings = { navController.navigate(Screen.LibrarySettings.route) },
                onNavigateToIntegrationSettings = { navController.navigate(Screen.IntegrationSettings.route) },
                onNavigateToLyricSettings = { navController.navigate(Screen.LyricSettings.route) },
                onNavigateToAudioSettings = { navController.navigate(Screen.AudioSettings.route) },
                onNavigateToBackupSettings = { navController.navigate(Screen.BackupSettings.route) },
                onNavigateToLogs = { navController.navigate(Screen.Logs.route) },
                onBack = { navController.popBackStack() },
                mainViewModel = mainViewModel,
                playerViewModel = playerViewModel
            )
        }

        composable(Screen.AudioSettings.route) {
            AudioSettingsScreen(
                onBack = { navController.popBackStack() },
                playerViewModel = playerViewModel,
                onNavigateToEqualizer = { navController.navigate(Screen.Equalizer.route) }
            )
        }

        composable(Screen.Equalizer.route) {
            EqualizerScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.BackupSettings.route) {
            BackupSettingsScreen(
                onBack = { navController.popBackStack() },
                mainViewModel = mainViewModel
            )
        }

        composable(Screen.SettingsDetail.route) {
            SettingsDetailScreen(
                onBack = { navController.popBackStack() },
                onNavigateToLyricFont = { navController.navigate(Screen.LyricFont.route) },
                mode = SettingsDetailMode.AppearanceHome
            )
        }

        composable(Screen.LibrarySettings.route) {
            SettingsDetailScreen(
                onBack = { navController.popBackStack() },
                onNavigateToLyricFont = { navController.navigate(Screen.LyricFont.route) },
                mode = SettingsDetailMode.LibraryScanning,
                onNavigateToScanFolders = { navController.navigate(Screen.ScanSettings.route) }
            )
        }

        composable(Screen.IntegrationSettings.route) {
            SettingsDetailScreen(
                onBack = { navController.popBackStack() },
                onNavigateToLyricFont = { navController.navigate(Screen.LyricFont.route) },
                mode = SettingsDetailMode.Integrations
            )
        }

        composable(Screen.LyricSettings.route) {
            SettingsDetailScreen(
                onBack = { navController.popBackStack() },
                onNavigateToLyricFont = { navController.navigate(Screen.LyricFont.route) },
                onNavigateToLyricPluginSources = { navController.navigate(Screen.LyricPluginSources.route) },
                playerViewModel = playerViewModel,
                showOnlyLyrics = true
            )
        }

        composable(Screen.LyricPluginSources.route) {
            LyricPluginSourceSettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.LyricFont.route) {
            LyricFontScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Logs.route) {
            LogScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.LxOnline.route) {
            LxOnlineScreen(
                mainViewModel = mainViewModel,
                playerViewModel = playerViewModel,
                providerOverride = RemoteMusicProvider.Lx,
                titleOverride = "LX Music",
                onBack = { navController.popBackStack() },
                onNavigateToPlayer = onNavigateToPlayer,
                onNavigateToSourceSettings = { navController.navigate(Screen.LxSourceSettings.route) },
                onNavigateToAlbum = { albumId -> navController.navigate(Screen.AlbumDetail.createRoute(albumId)) },
                onNavigateToArtist = { artistName -> navController.navigate(Screen.ArtistDetail.createRoute(artistName)) }
            )
        }

        composable(Screen.NavidromeOnline.route) {
            RemoteDirectoryScreen(
                provider = RemoteMusicProvider.Navidrome,
                title = "Navidrome",
                playerViewModel = playerViewModel,
                onBack = { navController.popBackStack() },
                onNavigateToPlayer = onNavigateToPlayer
            )
        }

        composable(Screen.EmbyOnline.route) {
            RemoteDirectoryScreen(
                provider = RemoteMusicProvider.Emby,
                title = "Emby",
                playerViewModel = playerViewModel,
                onBack = { navController.popBackStack() },
                onNavigateToPlayer = onNavigateToPlayer
            )
        }

        composable(Screen.LxSourceSettings.route) {
            LxSourceSettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Analytics.route) {
            AnalyticsScreen(
                mainViewModel = mainViewModel,
                onBack = { navController.popBackStack() },
                onNavigateToHistory = { navController.navigate(Screen.PlaybackHistory.route) }
            )
        }

        composable(Screen.AiChat.route) {
            AiChatScreen(
                mainViewModel = mainViewModel,
                playerViewModel = playerViewModel,
                onBack = { navController.popBackStack() },
                onNavigateToPlayer = onNavigateToPlayer
            )
        }

        composable(Screen.PlaybackHistory.route) {
            PlaybackHistoryScreen(
                mainViewModel = mainViewModel,
                playerViewModel = playerViewModel,
                onBack = { navController.popBackStack() },
                onNavigateToAlbum = { albumId ->
                    navController.navigate(Screen.AlbumDetail.createRoute(albumId))
                },
                onNavigateToArtist = { artistName ->
                    navController.navigate(Screen.ArtistDetail.createRoute(artistName))
                }
            )
        }

        composable(Screen.LibraryAnalysis.route) {
            LibraryAnalysisScreen(
                mainViewModel = mainViewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.About.route) {
            AboutScreen(
                onBack = { navController.popBackStack() },
                onNavigateToUpdate = { navController.navigate(Screen.Update.route) }
            )
        }

        composable(Screen.Update.route) {
            UpdateScreen(
                onBack = { navController.popBackStack() }
            )
        }

    }
}
