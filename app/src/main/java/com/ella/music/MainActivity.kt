package com.ella.music

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.content.pm.PackageManager
import android.net.Uri
import java.net.URLDecoder
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
import androidx.compose.ui.input.pointer.pointerInput
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
import com.ella.music.data.model.FAVORITES_PLAYLIST_ID
import com.ella.music.data.model.Song
import com.ella.music.ui.components.AddToPlaylistSheet
import com.ella.music.ui.components.CompactMiniPlayer
import com.ella.music.ui.components.CreatePlaylistAndAddSheet
import com.ella.music.ui.components.GlassPill
import com.ella.music.ui.components.LiquidGlassBottomBar
import com.ella.music.ui.components.LiquidGlassBottomBarItem
import com.ella.music.ui.components.MiniPlayer
import com.ella.music.ui.components.SafeCoverImage
import com.ella.music.ui.components.TagEditorEditTracker
import com.ella.music.ui.components.simpleLuminance
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

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            lifecycleScope.launch {
                if (SettingsManager.getInstance(this@MainActivity).initialScanPromptHandled.first()) {
                    mainViewModel?.scanMusicIfAutoEnabled()
                }
            }
        }
    }

    private var mainViewModel: MainViewModel? = null
    private var appliedLanguageTag: String? = null
    var latestIntent: Intent? = null
        private set
    var onNewIntentCallback: ((Intent) -> Unit)? = null

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

            val settingsManager = remember { SettingsManager.getInstance(this@MainActivity) }
            val themeMode by settingsManager.themeMode.collectAsState(initial = 0)
            val appLanguage by settingsManager.appLanguage.collectAsState(initial = SettingsManager.APP_LANGUAGE_SYSTEM)
            val appFontPath by settingsManager.lyricFontPath.collectAsState(initial = "")
            val appFontWeight by settingsManager.lyricFontWeight.collectAsState(initial = 800)

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

            EllaTheme(
                themeMode = themeMode,
                appFontPath = appFontPath,
                appFontWeight = appFontWeight
            ) {
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
            SettingsManager.getInstance(this@MainActivity).appLanguage.first()
        }
        applyAppLanguage(language)
    }

    private fun applyAppLanguage(languageTag: String): Boolean {
        if (appliedLanguageTag == languageTag) return false
        val locales = when (languageTag) {
            SettingsManager.APP_LANGUAGE_ZH_CN -> LocaleListCompat.forLanguageTags("zh-CN")
            SettingsManager.APP_LANGUAGE_ZH_TW -> LocaleListCompat.forLanguageTags("zh-TW")
            SettingsManager.APP_LANGUAGE_EN -> LocaleListCompat.forLanguageTags("en")
            SettingsManager.APP_LANGUAGE_JA -> LocaleListCompat.forLanguageTags("ja")
            else -> LocaleListCompat.getEmptyLocaleList()
        }
        appliedLanguageTag = languageTag
        if (AppCompatDelegate.getApplicationLocales() != locales) {
            AppCompatDelegate.setApplicationLocales(locales)
            return true
        }
        return false
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        latestIntent = intent
        onNewIntentCallback?.invoke(intent)
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
    val settingsManager = remember { SettingsManager.getInstance(context) }
    val scope = rememberCoroutineScope()
    val activity = context as? Activity
    val mainActivity = context as? MainActivity
    val currentProcessingIntent = remember { mutableStateOf(activity?.intent) }
    DisposableEffect(mainActivity) {
        mainActivity?.onNewIntentCallback = { intent -> currentProcessingIntent.value = intent }
        onDispose { mainActivity?.onNewIntentCallback = null }
    }
    var showPlayerOverlay by remember { mutableStateOf(false) }
    var playerDismissProgress by remember { mutableFloatStateOf(0f) }
    var playerOverlayOpenToken by remember { mutableIntStateOf(0) }
    val isPlayerVisible = showPlayerOverlay || currentRoute == Screen.Player.route
    val libraryCacheLoaded by mainViewModel.libraryCacheLoaded.collectAsState()
    val initialScanPromptHandled by settingsManager.initialScanPromptHandled.collectAsState(initial = true)
    val localPlaylistScanPromptHandled by settingsManager.localPlaylistScanPromptHandled.collectAsState(initial = true)
    val autoScanLocalPlaylists by settingsManager.autoScanLocalPlaylists.collectAsState(initial = false)
    val shortcutLibraryLabel by settingsManager.shortcutLibraryLabel.collectAsState(initial = SettingsManager.DEFAULT_SHORTCUT_LIBRARY_LABEL)
    val shortcutPlaylistsLabel by settingsManager.shortcutPlaylistsLabel.collectAsState(initial = SettingsManager.DEFAULT_SHORTCUT_PLAYLISTS_LABEL)
    val shortcutFolderLabel by settingsManager.shortcutFolderLabel.collectAsState(initial = SettingsManager.DEFAULT_SHORTCUT_FOLDER_LABEL)
    val isScanning by mainViewModel.isScanning.collectAsState()
    var showInitialScanPrompt by remember { mutableStateOf(false) }
    var showLocalPlaylistScanPrompt by remember { mutableStateOf(false) }
    var localPlaylistAutoScanHandled by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(currentProcessingIntent.value) {
        val activity = context as? Activity
        val shortcutAction = currentProcessingIntent.value?.getStringExtra(EXTRA_SHORTCUT_ACTION)
            ?: currentProcessingIntent.value?.getStringExtra(EXTRA_SHORTCUT_ACTION_NEW)
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

        val shortcutRoute = currentProcessingIntent.value?.resolveShortcutRoute().orEmpty()
        if (shortcutRoute.isNotBlank()) {
            runCatching {
                navController.navigate(shortcutRoute) {
                    launchSingleTop = true
                    restoreState = true
                }
            }
        }
        currentProcessingIntent.value?.removeExtra(EXTRA_SHORTCUT_ACTION)
        currentProcessingIntent.value?.removeExtra(EXTRA_SHORTCUT_ACTION_NEW)
        currentProcessingIntent.value?.removeExtra(EXTRA_SHORTCUT_ROUTE)
        currentProcessingIntent.value?.removeExtra(EXTRA_SHORTCUT_ROUTE_NEW)
        currentProcessingIntent.value?.setData(null)
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

    LaunchedEffect(
        libraryCacheLoaded,
        localPlaylistScanPromptHandled,
        autoScanLocalPlaylists,
        librarySongs,
        showInitialScanPrompt
    ) {
        if (!libraryCacheLoaded || librarySongs.isEmpty() || showInitialScanPrompt) return@LaunchedEffect
        if (!localPlaylistScanPromptHandled) {
            showLocalPlaylistScanPrompt = true
            return@LaunchedEffect
        }
        if (autoScanLocalPlaylists && !localPlaylistAutoScanHandled) {
            localPlaylistAutoScanHandled = true
            mainViewModel.scanLocalPlaylistFiles()
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
        currentRoute != Screen.AiChat.route &&
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

            LocalPlaylistScanPromptDialog(
                show = showLocalPlaylistScanPrompt,
                onDismiss = {
                    showLocalPlaylistScanPrompt = false
                    scope.launch {
                        settingsManager.setLocalPlaylistScanPromptHandled(true)
                        settingsManager.setAutoScanLocalPlaylists(false)
                    }
                },
                onScan = {
                    showLocalPlaylistScanPrompt = false
                    scope.launch {
                        settingsManager.setLocalPlaylistScanPromptHandled(true)
                        settingsManager.setAutoScanLocalPlaylists(true)
                    }
                    mainViewModel.scanLocalPlaylistFiles { result ->
                        result
                            .onSuccess { importResult ->
                                val message = if (importResult.importedPlaylists == 0) {
                                    context.getString(R.string.local_playlist_scan_none)
                                } else {
                                    context.getString(
                                        R.string.playlist_import_result,
                                        context.getString(
                                            R.string.playlist_import_playlist_prefix,
                                            importResult.importedPlaylists
                                        ),
                                        importResult.importedCount,
                                        importResult.matchedCount,
                                        if (importResult.missingCount > 0) {
                                            context.getString(R.string.playlist_import_missing_paths, importResult.missingCount)
                                        } else {
                                            ""
                                        },
                                        if (importResult.duplicateCount > 0) {
                                            context.getString(R.string.playlist_import_duplicates, importResult.duplicateCount)
                                        } else {
                                            ""
                                        }
                                    )
                                }
                                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                            }
                            .onFailure {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.playlist_import_failed, it.message.orEmpty()),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                    }
                }
            )
        }
    }
}
