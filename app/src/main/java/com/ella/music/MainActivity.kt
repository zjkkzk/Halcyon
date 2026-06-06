package com.ella.music

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.ella.music.data.BottomBarGlassEffect
import com.ella.music.data.SettingsManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.ella.music.data.model.Song
import com.ella.music.ui.components.CompactMiniPlayer
import com.ella.music.ui.components.GlassPill
import com.ella.music.ui.components.LiquidGlassBottomBar
import com.ella.music.ui.components.LiquidGlassBottomBarItem
import com.ella.music.ui.components.MiniPlayer
import com.ella.music.ui.components.SafeCoverImage
import com.ella.music.ui.components.TagEditorEditTracker
import com.ella.music.ui.components.simpleLuminance
import top.yukonga.miuix.kmp.window.WindowBottomSheet
import com.ella.music.ui.components.updateEllaDynamicShortcuts
import com.ella.music.ui.navigation.AppNavigation
import com.ella.music.ui.navigation.EXTRA_SHORTCUT_ROUTE
import com.ella.music.ui.navigation.EXTRA_SHORTCUT_ROUTE_NEW
import com.ella.music.ui.navigation.Screen
import com.ella.music.ui.navigation.EXTRA_SHORTCUT_ACTION
import com.ella.music.ui.navigation.EXTRA_SHORTCUT_ACTION_NEW
import com.ella.music.ui.navigation.SHORTCUT_ACTION_PLAY
import com.ella.music.ui.navigation.SHORTCUT_ACTION_SHUFFLE_ALL
import com.ella.music.ui.player.PlayerScreen
import com.ella.music.ui.theme.EllaTheme
import com.ella.music.ui.theme.THEME_DARK
import com.ella.music.ui.theme.THEME_FOLLOW_SYSTEM
import com.ella.music.viewmodel.MainViewModel
import com.ella.music.viewmodel.PlayerViewModel
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Search
import top.yukonga.miuix.kmp.icon.extended.Music
import top.yukonga.miuix.kmp.icon.extended.Playlist
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

private enum class BottomDockMode {
    Expanded,
    Compact
}

private data class BottomDockTab(
    val route: String,
    val label: String,
    val icon: ImageVector
)

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            lifecycleScope.launch {
                if (SettingsManager(this@MainActivity).initialScanPromptHandled.first()) {
                    mainViewModel?.scanMusicIfAutoEnabled()
                }
            }
        }
    }

    private var mainViewModel: MainViewModel? = null
    private var appliedLanguageTag: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        applySavedAppLanguage()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        setContent {
            val mainVm: MainViewModel = viewModel()
            val playerVm: PlayerViewModel = viewModel()
            mainViewModel = mainVm

            val settingsManager = remember { SettingsManager(this@MainActivity) }
            val themeMode by settingsManager.themeMode.collectAsState(initial = 0)
            val appLanguage by settingsManager.appLanguage.collectAsState(initial = SettingsManager.APP_LANGUAGE_SYSTEM)

            val isDark = when (themeMode) {
                THEME_DARK -> true
                THEME_FOLLOW_SYSTEM -> isSystemInDarkTheme()
                else -> false
            }

            LaunchedEffect(appLanguage) {
                if (applyAppLanguage(appLanguage)) {
                    recreate()
                }
            }

            val view = LocalView.current
            DisposableEffect(isDark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(
                        Color.TRANSPARENT,
                        Color.TRANSPARENT
                    ) { isDark },
                    navigationBarStyle = SystemBarStyle.auto(
                        Color.TRANSPARENT,
                        Color.TRANSPARENT
                    ) { isDark },
                )

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    window.isNavigationBarContrastEnforced = false
                }

                onDispose {}
            }

            LaunchedEffect(isDark) {
                val window = (view.context as ComponentActivity).window
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !isDark
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !isDark
            }

            LaunchedEffect(Unit) {
                val canScanNow = checkAndRequestPermissions()
                mainVm.loadCachedLibrary()
                if (!startupPlaybackHandled) {
                    startupPlaybackHandled = true
                    when (settingsManager.startupPlayMode.first()) {
                        SettingsManager.STARTUP_PLAY_RANDOM -> {
                            val songs = mainVm.songs.first { it.isNotEmpty() }
                            if (playerVm.currentSong.value == null && !playerVm.hasSavedPlaybackQueue()) {
                                val startIndex = songs.indices.random()
                                playerVm.setPlaylist(songs, startIndex)
                            }
                        }
                        SettingsManager.STARTUP_PLAY_RESUME -> {
                            if (playerVm.currentSong.value == null && playerVm.hasSavedPlaybackQueue()) {
                                playerVm.playRestoredQueue()
                            }
                        }
                    }
                }
                if (canScanNow && settingsManager.initialScanPromptHandled.first()) {
                    mainVm.scanMusicIfAutoEnabled()
                }
            }

            EllaTheme(themeMode = themeMode) {
                EllaApp(mainVm, playerVm, isDark)
            }
        }
    }

    private fun checkAndRequestPermissions(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        return if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(permission)
            false
        } else true
    }

    private fun applySavedAppLanguage() {
        val language = runBlocking(Dispatchers.IO) {
            SettingsManager(this@MainActivity).appLanguage.first()
        }
        applyAppLanguage(language)
    }

    private fun applyAppLanguage(languageTag: String): Boolean {
        if (appliedLanguageTag == languageTag) return false
        val locales = when (languageTag) {
            SettingsManager.APP_LANGUAGE_ZH_CN -> LocaleListCompat.forLanguageTags("zh-CN")
            SettingsManager.APP_LANGUAGE_EN -> LocaleListCompat.forLanguageTags("en")
            else -> LocaleListCompat.getEmptyLocaleList()
        }
        appliedLanguageTag = languageTag
        if (AppCompatDelegate.getApplicationLocales() != locales) {
            AppCompatDelegate.setApplicationLocales(locales)
            return true
        }
        return false
    }

    private companion object {
        var startupPlaybackHandled = false
    }
}

private fun isVivoFamilyDevice(): Boolean {
    val manufacturer = Build.MANUFACTURER.orEmpty().lowercase()
    val brand = Build.BRAND.orEmpty().lowercase()
    return manufacturer.contains("vivo") ||
        brand.contains("vivo") ||
        manufacturer.contains("iqoo") ||
        brand.contains("iqoo")
}

@Composable
fun EllaApp(
    mainViewModel: MainViewModel,
    playerViewModel: PlayerViewModel,
    isDarkTheme: Boolean
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val view = LocalView.current
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val settingsManager = remember { SettingsManager(context) }
    val scope = rememberCoroutineScope()
    var showPlayerOverlay by remember { mutableStateOf(false) }
    var playerDismissProgress by remember { mutableFloatStateOf(0f) }
    var playerOverlayOpenToken by remember { mutableIntStateOf(0) }
    val isPlayerVisible = showPlayerOverlay || currentRoute == Screen.Player.route
    val libraryCacheLoaded by mainViewModel.libraryCacheLoaded.collectAsState()
    val initialScanPromptHandled by settingsManager.initialScanPromptHandled.collectAsState(initial = true)
    val shortcutLibraryLabel by settingsManager.shortcutLibraryLabel.collectAsState(initial = SettingsManager.DEFAULT_SHORTCUT_LIBRARY_LABEL)
    val shortcutPlaylistsLabel by settingsManager.shortcutPlaylistsLabel.collectAsState(initial = SettingsManager.DEFAULT_SHORTCUT_PLAYLISTS_LABEL)
    val shortcutFolderLabel by settingsManager.shortcutFolderLabel.collectAsState(initial = SettingsManager.DEFAULT_SHORTCUT_FOLDER_LABEL)
    val isScanning by mainViewModel.isScanning.collectAsState()
    var showInitialScanPrompt by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val activity = context as? Activity
        val shortcutAction = activity?.intent?.getStringExtra(EXTRA_SHORTCUT_ACTION)
            ?: activity?.intent?.getStringExtra(EXTRA_SHORTCUT_ACTION_NEW)
            .orEmpty()
        when (shortcutAction) {
            SHORTCUT_ACTION_PLAY -> {
                when {
                    playerViewModel.currentSong.value != null || playerViewModel.hasSavedPlaybackQueue() -> {
                        playerViewModel.playRestoredQueue()
                    }
                    else -> {
                        val songs = mainViewModel.songs.first { it.isNotEmpty() }
                        playerViewModel.setPlaylist(songs, 0)
                    }
                }
                runCatching {
                    navController.navigate(Screen.Player.route) {
                        launchSingleTop = true
                    }
                }
            }
            SHORTCUT_ACTION_SHUFFLE_ALL -> {
                val songs = mainViewModel.songs.first { it.isNotEmpty() }
                playerViewModel.setPlaylist(songs.shuffled(), 0)
                runCatching {
                    navController.navigate(Screen.Player.route) {
                        launchSingleTop = true
                    }
                }
            }
        }

        val shortcutRoute = activity?.intent?.resolveShortcutRoute().orEmpty()
        if (shortcutRoute.isNotBlank()) {
            runCatching {
                navController.navigate(shortcutRoute) {
                    launchSingleTop = true
                    restoreState = true
                }
            }
        }
        activity?.intent?.removeExtra(EXTRA_SHORTCUT_ACTION)
        activity?.intent?.removeExtra(EXTRA_SHORTCUT_ACTION_NEW)
        activity?.intent?.removeExtra(EXTRA_SHORTCUT_ROUTE)
        activity?.intent?.removeExtra(EXTRA_SHORTCUT_ROUTE_NEW)
        activity?.intent?.setData(null)
    }

    LaunchedEffect(shortcutLibraryLabel, shortcutPlaylistsLabel, shortcutFolderLabel) {
        updateEllaDynamicShortcuts(
            context = context,
            libraryLabel = shortcutLibraryLabel,
            searchLabel = shortcutPlaylistsLabel,
            shuffleLabel = shortcutFolderLabel
        )
    }

    val initialScanFolderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val readOnly = Intent.FLAG_GRANT_READ_URI_PERMISSION
        val readWrite = readOnly or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, readWrite)
        }.recoverCatching {
            context.contentResolver.takePersistableUriPermission(uri, readOnly)
        }
        val folderPath = uri.toPrimaryStoragePath()
        if (folderPath == null) {
            Toast.makeText(context, context.getString(R.string.unsupported_system_folder_path), Toast.LENGTH_SHORT).show()
        } else {
            scope.launch {
                settingsManager.setUseAndroidMediaLibrary(false)
                settingsManager.setScanIncludeFolders(folderPath)
                settingsManager.setAutoScan(false)
                mainViewModel.scanMusic()
            }
            Toast.makeText(context, context.getString(R.string.scan_folder_added), Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(isPlayerVisible, isDarkTheme) {
        val window = (view.context as ComponentActivity).window
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = if (isPlayerVisible) false else !isDarkTheme
            isAppearanceLightNavigationBars = if (isPlayerVisible) false else !isDarkTheme
        }
    }

    val showBottomBar = currentRoute.isBottomDockRoute()
    val canCompactBottomDock = showBottomBar
    var bottomDockMode by rememberSaveable { mutableStateOf(BottomDockMode.Expanded) }

    val currentSong by playerViewModel.currentSong.collectAsState()
    val isPlaying by playerViewModel.isPlaying.collectAsState()
    val librarySongs by mainViewModel.songs.collectAsState()

    LaunchedEffect(currentRoute, canCompactBottomDock) {
        bottomDockMode = BottomDockMode.Expanded
    }

    LaunchedEffect(libraryCacheLoaded, initialScanPromptHandled, isScanning, librarySongs) {
        if (!libraryCacheLoaded || initialScanPromptHandled) return@LaunchedEffect
        if (librarySongs.isNotEmpty()) {
            settingsManager.setInitialScanPromptHandled(true)
            mainViewModel.scanMusicIfAutoEnabled()
        } else if (!isScanning) {
            showInitialScanPrompt = true
        }
    }

    DisposableEffect(lifecycleOwner, mainViewModel, playerViewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                TagEditorEditTracker.consume()?.let { editedSong ->
                    mainViewModel.refreshSongAfterExternalEdit(editedSong) { updatedSong ->
                        playerViewModel.refreshCurrentSongAfterExternalEdit(updatedSong)
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    val currentPosition by playerViewModel.currentPosition.collectAsState()
    val duration by playerViewModel.duration.collectAsState()
    val lyrics by playerViewModel.lyrics.collectAsState()
    val currentLyricIndex by playerViewModel.currentLyricIndex.collectAsState()
    val miniPlayerLyricSecondary by settingsManager.miniPlayerLyricSecondary.collectAsState(initial = SettingsManager.LYRIC_SECONDARY_TRANSLATION)
    val miniPlayerCoverRotation by settingsManager.miniPlayerCoverRotation.collectAsState(initial = true)
    val miniPlayerLyricsEnabled by settingsManager.miniPlayerLyricsEnabled.collectAsState(initial = true)
    val miniPlayerRightButton by settingsManager.miniPlayerRightButton.collectAsState(initial = 0)
    val bottomBarGlassEffect by settingsManager.bottomBarGlassEffect.collectAsState(initial = BottomBarGlassEffect.LiquidGlass)
    val appWallpaperEnabled by settingsManager.appWallpaperEnabled.collectAsState(initial = false)
    val appWallpaperUri by settingsManager.appWallpaperUri.collectAsState(initial = "")
    val startupPosterEnabled by settingsManager.startupPosterEnabled.collectAsState(initial = false)
    val startupPosterUri by settingsManager.startupPosterUri.collectAsState(initial = "")
    val notificationPermissionPromptHandled by settingsManager.notificationPermissionPromptHandled.collectAsState(initial = false)
    var showStartupPoster by rememberSaveable { mutableStateOf(true) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        scope.launch { settingsManager.setNotificationPermissionPromptHandled(true) }
        if (!granted) {
            Toast.makeText(
                context,
                context.getString(R.string.notification_permission_denied_hint),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    LaunchedEffect(startupPosterEnabled, startupPosterUri) {
        if (startupPosterEnabled && startupPosterUri.isNotBlank() && showStartupPoster) {
            kotlinx.coroutines.delay(3_000L)
            showStartupPoster = false
        }
    }

    val currentLyricLine = lyrics.getOrNull(currentLyricIndex)
    val miniPlayerLyricText = if (isPlaying && miniPlayerLyricsEnabled) {
        currentLyricLine?.text?.takeIf { it.isNotBlank() && !it.isMusicSymbolOnly() }
    } else {
        null
    }
    val miniPlayerLyricSecondaryText = if (isPlaying && miniPlayerLyricsEnabled) {
        when (miniPlayerLyricSecondary) {
            SettingsManager.LYRIC_SECONDARY_TRANSLATION -> currentLyricLine?.translation?.takeIf { it.isNotBlank() }
            SettingsManager.LYRIC_SECONDARY_PRONUNCIATION -> currentLyricLine?.pronunciation?.takeIf { it.isNotBlank() }
            else -> null
        }
    } else {
        null
    }

    val nextLyricLine = lyrics.getOrNull(currentLyricIndex + 1)

    val miniPlayerLyricProgress = if (isPlaying && currentLyricLine != null) {
        val start = currentLyricLine.timeMs
        val end = currentLyricLine.endMs
            ?: nextLyricLine?.timeMs
            ?: (start + 5_000L)

        ((currentPosition - start).toFloat() / (end - start).coerceAtLeast(1L).toFloat())
            .coerceIn(0f, 1f)
    } else {
        0f
    }

    val showMiniPlayer = currentSong != null &&
        currentRoute != Screen.Player.route &&
        !showPlayerOverlay
    LaunchedEffect(showMiniPlayer, canCompactBottomDock) {
        if (!showMiniPlayer || !canCompactBottomDock) bottomDockMode = BottomDockMode.Expanded
    }

    val dockScrollConnection = remember(showMiniPlayer, canCompactBottomDock) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (!showMiniPlayer || !canCompactBottomDock || source != NestedScrollSource.UserInput) return Offset.Zero
                when {
                    available.y < -12f -> bottomDockMode = BottomDockMode.Compact
                    available.y > 16f -> bottomDockMode = BottomDockMode.Expanded
                }
                return Offset.Zero
            }
        }
    }

    val backdrop = rememberLayerBackdrop()
    val useGlass = true
    val tabs = listOf(
        BottomDockTab(Screen.Home.route, stringResource(R.string.tab_home), MiuixIcons.Regular.Music),
        BottomDockTab(Screen.Library.route, stringResource(R.string.tab_library), MiuixIcons.Regular.Playlist),
    )
    val currentTabRoute = currentRoute.toCurrentTabRoute()

    val wallpaperVisible = appWallpaperEnabled && appWallpaperUri.isNotBlank()
    val startupPosterVisible = startupPosterEnabled && startupPosterUri.isNotBlank() && showStartupPoster
    LaunchedEffect(startupPosterVisible, notificationPermissionPromptHandled) {
        if (startupPosterVisible) return@LaunchedEffect
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return@LaunchedEffect
        if (notificationPermissionPromptHandled || !isVivoFamilyDevice()) return@LaunchedEffect
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            scope.launch { settingsManager.setNotificationPermissionPromptHandled(true) }
        } else {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val contentModifier = Modifier
        .fillMaxSize()
        .then(if (wallpaperVisible) Modifier else Modifier.background(MiuixTheme.colorScheme.background))
        .layerBackdrop(backdrop)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MiuixTheme.colorScheme.background)
    ) {
        if (startupPosterVisible) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ComposeColor.Black)
            ) {
                SafeCoverImage(
                    model = Uri.parse(startupPosterUri),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    sizePx = 1800,
                    showDefaultPlaceholder = false
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(ComposeColor.Black.copy(alpha = 0.10f))
                )
            }
        } else {
            if (wallpaperVisible) {
                SafeCoverImage(
                    model = Uri.parse(appWallpaperUri),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    sizePx = 1600,
                    showDefaultPlaceholder = false
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = if (isDarkTheme) {
                                    listOf(
                                        ComposeColor.Black.copy(alpha = 0.32f),
                                        ComposeColor.Black.copy(alpha = 0.22f),
                                        ComposeColor.Black.copy(alpha = 0.40f)
                                    )
                                } else {
                                    listOf(
                                        ComposeColor.White.copy(alpha = 0.28f),
                                        ComposeColor.White.copy(alpha = 0.18f),
                                        ComposeColor.White.copy(alpha = 0.34f)
                                    )
                                }
                            )
                        )
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                AppNavigation(
                    navController = navController,
                    mainViewModel = mainViewModel,
                    playerViewModel = playerViewModel,
                    modifier = contentModifier.nestedScroll(dockScrollConnection),
                    onNavigateToPlayer = {
                        playerDismissProgress = 0f
                        playerOverlayOpenToken++
                        showPlayerOverlay = true
                    }
                )
                FloatingBottomControls(
                    showMiniPlayer = showMiniPlayer,
                    showBottomBar = showBottomBar,
                    currentSong = currentSong,
                    isPlaying = isPlaying,
                    coverRotationEnabled = miniPlayerCoverRotation,
                    currentPosition = currentPosition,
                    duration = duration,
                    lyricText = miniPlayerLyricText,
                    lyricTranslation = miniPlayerLyricSecondaryText,
                    lyricProgress = miniPlayerLyricProgress,
                    miniPlayerRightButton = miniPlayerRightButton,
                    tabs = tabs,
                    currentTabRoute = currentTabRoute,
                    currentRoute = currentRoute,
                    bottomDockMode = bottomDockMode,
                    canCompact = canCompactBottomDock,
                    backdrop = backdrop,
                    glassEffect = bottomBarGlassEffect,
                    mainViewModel = mainViewModel,
                    playerViewModel = playerViewModel,
                    onNavigate = { route ->
                        bottomDockMode = BottomDockMode.Expanded
                        if (!currentRoute.matchesRoute(route)) {
                            navController.navigate(route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    },
                    onNavigateSearch = {
                        bottomDockMode = BottomDockMode.Expanded
                        val route = Screen.LibrarySearch.createRoute()
                        if (!currentRoute.matchesRoute(route)) {
                            navController.navigate(route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    },
                    onNavigatePlayer = {
                        playerDismissProgress = 0f
                        playerOverlayOpenToken++
                        showPlayerOverlay = true
                    },
                    onExpand = {
                        bottomDockMode = BottomDockMode.Expanded
                    },
                    modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter)
                )
            }
            AnimatedVisibility(
                visible = showPlayerOverlay,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = ExitTransition.None,
                modifier = Modifier.fillMaxSize()
            ) {
                PlayerScreen(
                    mainViewModel = mainViewModel,
                    playerViewModel = playerViewModel,
                    onBack = {
                        playerViewModel.setShowLyrics(false)
                        showPlayerOverlay = false
                        playerDismissProgress = 0f
                    },
                    onNavigateToAlbum = { albumId ->
                        playerViewModel.setShowLyrics(false)
                        showPlayerOverlay = false
                        playerDismissProgress = 0f
                        navController.navigate(Screen.AlbumDetail.createRoute(albumId))
                    },
                    onNavigateToArtist = { artistName ->
                        playerViewModel.setShowLyrics(false)
                        showPlayerOverlay = false
                        playerDismissProgress = 0f
                        navController.navigate(Screen.ArtistDetail.createRoute(artistName))
                    },
                    onNavigateToMetadataCategory = { type, name ->
                        playerViewModel.setShowLyrics(false)
                        showPlayerOverlay = false
                        playerDismissProgress = 0f
                        navController.navigate(Screen.MetadataCategoryDetail.createRoute(type, name))
                    },
                    onDismissProgressChange = { progress ->
                        playerDismissProgress = progress
                    },
                    openToken = playerOverlayOpenToken
                )
            }

            InitialScanPromptDialog(
                show = showInitialScanPrompt,
                onDismiss = {
                    showInitialScanPrompt = false
                    scope.launch {
                        settingsManager.setInitialScanPromptHandled(true)
                        settingsManager.setAutoScan(false)
                    }
                },
                onCustomFolderScan = {
                    showInitialScanPrompt = false
                    scope.launch {
                        settingsManager.setInitialScanPromptHandled(true)
                        settingsManager.setUseAndroidMediaLibrary(false)
                        settingsManager.setAutoScan(false)
                    }
                    initialScanFolderPicker.launch(null)
                },
                onMediaLibraryScan = {
                    showInitialScanPrompt = false
                    scope.launch {
                        settingsManager.setInitialScanPromptHandled(true)
                        settingsManager.setUseAndroidMediaLibrary(true)
                        settingsManager.setAutoScan(false)
                        mainViewModel.scanMusic()
                    }
                }
            )
        }
    }
}

private fun Intent.resolveShortcutRoute(): String {
    val uri = data
    if (uri != null && uri.scheme in setOf("ella", "halcyon")) {
        val host = uri.host.orEmpty()
        if (host == "search") {
            return Screen.LibrarySearch.createRoute(
                type = uri.getQueryParameter("type"),
                keyword = uri.getQueryParameter("keyword")
            )
        }
        uri.getQueryParameter("route")
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }
    }
    return getStringExtra(EXTRA_SHORTCUT_ROUTE)
        ?: getStringExtra(EXTRA_SHORTCUT_ROUTE_NEW)
        ?: ""
}

@Composable
private fun InitialScanPromptDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    onCustomFolderScan: () -> Unit,
    onMediaLibraryScan: () -> Unit
) {
    WindowDialog(
        show = show,
        title = stringResource(R.string.initial_scan_title),
        onDismissRequest = onDismiss
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            top.yukonga.miuix.kmp.basic.Text(
                text = stringResource(R.string.initial_scan_message),
                color = MiuixTheme.colorScheme.onSurface,
                fontSize = 15.sp,
                lineHeight = 22.sp
            )
            Spacer(modifier = Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    TextButton(
                        text = stringResource(R.string.common_cancel),
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Box(modifier = Modifier.weight(1f)) {
                    TextButton(
                        text = stringResource(R.string.common_custom),
                        onClick = onCustomFolderScan,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Box(modifier = Modifier.weight(1f)) {
                    TextButton(
                        text = stringResource(R.string.common_confirm),
                        onClick = onMediaLibraryScan,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun FloatingBottomControls(
    showMiniPlayer: Boolean,
    showBottomBar: Boolean,
    currentSong: Song?,
    isPlaying: Boolean,
    coverRotationEnabled: Boolean,
    currentPosition: Long,
    duration: Long,
    lyricText: String?,
    lyricTranslation: String?,
    lyricProgress: Float,
    miniPlayerRightButton: Int = 0,
    tabs: List<BottomDockTab>,
    currentTabRoute: String?,
    currentRoute: String?,
    bottomDockMode: BottomDockMode,
    canCompact: Boolean,
    backdrop: com.kyant.backdrop.Backdrop?,
    glassEffect: BottomBarGlassEffect,
    mainViewModel: MainViewModel,
    playerViewModel: PlayerViewModel,
    onNavigate: (String) -> Unit,
    onNavigateSearch: () -> Unit,
    onNavigatePlayer: () -> Unit,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
    useGlass: Boolean = true
) {
    var queueSheetExpanded by remember { mutableStateOf(false) }
    val playlist by playerViewModel.playlist.collectAsState()
    val currentSongId = currentSong?.id
    val effectiveMode = if (showMiniPlayer && canCompact) bottomDockMode else BottomDockMode.Expanded
    AnimatedContent(
        targetState = effectiveMode,
        transitionSpec = {
            fadeIn() + slideInVertically(initialOffsetY = { it / 3 }) togetherWith
                fadeOut() + slideOutVertically(targetOffsetY = { it / 3 })
        },
        label = "BottomDockMode",
        modifier = modifier
            .fillMaxWidth()
            .then(if (useGlass) Modifier.navigationBarsPadding() else Modifier)
    ) { mode ->
        if (mode == BottomDockMode.Compact && currentSong != null) {
            CompactBottomDock(
                song = currentSong,
                isPlaying = isPlaying,
                progress = if (duration > 0L) currentPosition.toFloat() / duration.toFloat() else 0f,
                lyricText = lyricText,
                lyricTranslation = lyricTranslation,
                lyricProgress = lyricProgress,
                coverRotationEnabled = coverRotationEnabled,
                albumArtUri = mainViewModel.getAlbumArtUri(currentSong.albumId),
                loadCoverArt = mainViewModel::getCoverArtBitmap,
                backdrop = if (useGlass) backdrop else null,
                glassEffect = glassEffect,
                currentTabRoute = currentTabRoute,
                isSearchSelected = currentRoute.isSearchRoute(),
                onOpenPlayer = onNavigatePlayer,
                onPlayPause = { playerViewModel.togglePlayPause() },
                onSkipNext = { playerViewModel.skipToNext() },
                onNavigateTab = { onNavigate(it) },
                onNavigateSearch = onNavigateSearch,
                onExpand = onExpand
            )
        } else {
            Column(modifier = Modifier.fillMaxWidth()) {
                AnimatedVisibility(
                    visible = showMiniPlayer,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                ) {
                    currentSong?.let { song ->
                        MiniPlayer(
                            song = song,
                            isPlaying = isPlaying,
                            progress = if (duration > 0L) currentPosition.toFloat() / duration.toFloat() else 0f,
                            coverRotationEnabled = coverRotationEnabled,
                            lyricText = lyricText,
                            lyricTranslation = lyricTranslation,
                            albumArtUri = mainViewModel.getAlbumArtUri(song.albumId),
                            loadCoverArt = mainViewModel::getCoverArtBitmap,
                            backdrop = if (useGlass) backdrop else null,
                            liquidGlass = useGlass,
                            glassEffect = glassEffect,
                            showQueueButton = miniPlayerRightButton == SettingsManager.MINI_PLAYER_RIGHT_QUEUE,
                            onClick = onNavigatePlayer,
                            onPlayPause = { playerViewModel.togglePlayPause() },
                            onSkipNext = { playerViewModel.skipToNext() },
                            onSkipPrevious = { playerViewModel.skipToPrevious() },
                            onShowQueue = { queueSheetExpanded = true },
                            lyricProgress = lyricProgress,
                        )
                    }
                }

                AnimatedVisibility(visible = showBottomBar) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (useGlass) {
                            Box(modifier = Modifier.weight(1f)) {
                                val selectedBottomTabIndex = tabs
                                    .indexOfFirst { currentTabRoute == it.route }
                                    .takeIf { it >= 0 }
                                LiquidGlassBottomBar(
                                    backdrop = backdrop,
                                    isBlurEnabled = true,
                                    glassEffect = glassEffect,
                                    selectedIndex = selectedBottomTabIndex,
                                    itemCount = tabs.size,
                                    onSelected = { index ->
                                        tabs.getOrNull(index)?.let { onNavigate(it.route) }
                                    }
                                ) {
                                    tabs.forEach { tab ->
                                        LiquidGlassBottomBarItem(
                                            selected = currentTabRoute == tab.route,
                                            onClick = {},
                                            backdrop = backdrop,
                                            isBlurEnabled = true,
                                            showSelectedIndicator = glassEffect == BottomBarGlassEffect.LiquidGlass,
                                            icon = {
                                                Icon(
                                                    imageVector = tab.icon,
                                                    contentDescription = tab.label,
                                                    tint = if (currentTabRoute == tab.route) MiuixTheme.colorScheme.primary
                                                    else MiuixTheme.colorScheme.onSurface,
                                                    modifier = Modifier
                                                )
                                            },
                                            label = {
                                                top.yukonga.miuix.kmp.basic.Text(
                                                    text = tab.label,
                                                    fontSize = 11.sp,
                                                    color = if (currentTabRoute == tab.route) MiuixTheme.colorScheme.primary
                                                    else MiuixTheme.colorScheme.onSurface
                                                )
                                            }
                                        )
                                    }
                                }
                            }
                            BottomDockActionPill(
                                icon = MiuixIcons.Basic.Search,
                                label = stringResource(R.string.common_search),
                                selected = currentRoute.isSearchRoute(),
                                onClick = onNavigateSearch,
                                backdrop = backdrop,
                                glassEffect = glassEffect,
                                modifier = Modifier.size(64.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (queueSheetExpanded) {
        WindowBottomSheet(
            show = true,
            enableNestedScroll = false,
            title = stringResource(R.string.player_queue_title),
            onDismissRequest = { queueSheetExpanded = false }
        ) {
            com.ella.music.ui.player.PlayerQueueMenu(
                playlist = playlist,
                currentSongId = currentSongId,
                onSongClick = { index ->
                    queueSheetExpanded = false
                    playerViewModel.playQueueIndex(index)
                },
                onRemoveSong = { index -> playerViewModel.removeFromPlaylist(index) },
                onMoveSong = { fromIndex, toIndex -> playerViewModel.movePlaylistItem(fromIndex, toIndex) },
                onClearQueue = {
                    queueSheetExpanded = false
                    playerViewModel.clearPlaylist()
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun CompactBottomDock(
    song: Song,
    isPlaying: Boolean,
    progress: Float,
    lyricText: String?,
    lyricTranslation: String?,
    lyricProgress: Float,
    coverRotationEnabled: Boolean,
    albumArtUri: Uri?,
    loadCoverArt: ((Song) -> android.graphics.Bitmap?)?,
    backdrop: com.kyant.backdrop.Backdrop?,
    glassEffect: BottomBarGlassEffect,
    currentTabRoute: String?,
    isSearchSelected: Boolean,
    onOpenPlayer: () -> Unit,
    onPlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onNavigateTab: (String) -> Unit,
    onNavigateSearch: () -> Unit,
    onExpand: () -> Unit
) {
    val isHomeSelected = currentTabRoute == Screen.Home.route
    val leftIcon = if (isHomeSelected) MiuixIcons.Regular.Music else MiuixIcons.Regular.Playlist
    val leftLabel = if (isHomeSelected) stringResource(R.string.tab_home) else stringResource(R.string.tab_library)
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .height(64.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onExpand
            ),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BottomDockActionPill(
            icon = leftIcon,
            label = leftLabel,
            selected = true,
            onClick = onExpand,
            backdrop = backdrop,
            glassEffect = glassEffect,
            modifier = Modifier.size(64.dp)
        )
        CompactMiniPlayer(
            song = song,
            isPlaying = isPlaying,
            progress = progress,
            lyricText = null,
            lyricTranslation = null,
            lyricProgress = 0f,
            coverRotationEnabled = coverRotationEnabled,
            albumArtUri = albumArtUri,
            loadCoverArt = loadCoverArt,
            backdrop = backdrop,
            glassEffect = glassEffect,
            onClick = onOpenPlayer,
            onPlayPause = onPlayPause,
            onSkipNext = onSkipNext,
            showSkipButton = false,
            modifier = Modifier.weight(1f)
        )
        BottomDockActionPill(
            icon = MiuixIcons.Basic.Search,
            label = stringResource(R.string.common_search),
            selected = isSearchSelected,
            onClick = onNavigateSearch,
            backdrop = backdrop,
            glassEffect = glassEffect,
            modifier = Modifier.size(64.dp)
        )
    }
}

@Composable
private fun BottomDockActionPill(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    backdrop: com.kyant.backdrop.Backdrop?,
    glassEffect: BottomBarGlassEffect,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pillScale by animateFloatAsState(
        targetValue = if (pressed) 1.06f else 1f,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 620f),
        label = "BottomDockActionPillScale"
    )
    val overlayAlpha by animateFloatAsState(
        targetValue = when {
            pressed -> 1f
            selected -> 0.72f
            else -> 0f
        },
        animationSpec = spring(dampingRatio = 0.88f, stiffness = 700f),
        label = "BottomDockActionPillOverlay"
    )
    val isLight = MiuixTheme.colorScheme.background.simpleLuminance() > 0.5f
    val overlayColor = when {
        selected -> if (isLight) ComposeColor.Black.copy(alpha = 0.08f) else ComposeColor.White.copy(alpha = 0.13f)
        isLight -> ComposeColor.White.copy(alpha = 0.32f)
        else -> ComposeColor.White.copy(alpha = 0.16f)
    }

    GlassPill(
        backdrop = backdrop,
        modifier = modifier.height(64.dp),
        shape = RoundedCornerShape(32.dp),
        glassEffect = glassEffect
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp)
                .graphicsLayer {
                    scaleX = pillScale
                    scaleY = pillScale
                }
                .background(
                    color = overlayColor.copy(alpha = overlayColor.alpha * overlayAlpha),
                    shape = RoundedCornerShape(28.dp)
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

private fun String?.toCurrentTabRoute(): String? {
    return when (this) {
        null,
        Screen.Home.route -> Screen.Home.route

        Screen.Library.route -> Screen.Library.route
        else -> null
    }
}

private fun String?.isSearchRoute(): Boolean {
    return this?.startsWith(Screen.LibrarySearch.baseRoute) == true ||
        this == Screen.LibrarySearch.route
}

private fun String?.isBottomDockRoute(): Boolean {
    return when {
        this == null -> false
        this.isSearchRoute() -> true
        this == Screen.Home.route -> true
        this == Screen.Library.route -> true
        else -> false
    }
}

private fun String?.matchesRoute(route: String): Boolean {
    return when {
        route.startsWith(Screen.LibrarySearch.baseRoute) -> this.isSearchRoute()
        else -> this == route
    }
}

private fun String.isMusicSymbolOnly(): Boolean {
    val content = trim()
    if (content.isBlank()) return true

    return content.all { char ->
        char.isWhitespace() ||
                char in setOf('♪', '♫', '♬', '♩', '♭', '♯', '♮') ||
                Character.UnicodeBlock.of(char) == Character.UnicodeBlock.MUSICAL_SYMBOLS
    }
}

private fun Uri.toPrimaryStoragePath(): String? {
    val documentId = runCatching { DocumentsContract.getTreeDocumentId(this) }.getOrNull() ?: return null
    val parts = documentId.split(':', limit = 2)
    val volume = parts.firstOrNull().orEmpty()
    val path = parts.getOrNull(1).orEmpty().trim('/')
    return when {
        volume.equals("primary", ignoreCase = true) && path.isBlank() -> "/storage/emulated/0"
        volume.equals("primary", ignoreCase = true) -> "/storage/emulated/0/$path"
        else -> null
    }
}
