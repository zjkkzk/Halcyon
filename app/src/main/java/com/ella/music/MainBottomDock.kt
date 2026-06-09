package com.ella.music

import com.ella.music.ui.components.EllaMiuixBottomSheet

import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.data.BottomBarGlassEffect
import com.ella.music.data.SettingsManager
import com.ella.music.data.model.FAVORITES_PLAYLIST_ID
import com.ella.music.data.model.Song
import com.ella.music.ui.components.AddToPlaylistSheet
import com.ella.music.ui.components.CompactMiniPlayer
import com.ella.music.ui.components.CreatePlaylistAndAddSheet
import com.ella.music.ui.components.GlassPill
import com.ella.music.ui.components.LiquidGlassBottomBar
import com.ella.music.ui.components.LiquidGlassBottomBarItem
import com.ella.music.ui.components.MiniPlayer
import com.ella.music.ui.components.simpleLuminance
import com.ella.music.ui.navigation.Screen
import com.ella.music.viewmodel.MainViewModel
import com.ella.music.viewmodel.PlayerViewModel
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Search
import top.yukonga.miuix.kmp.icon.extended.Music
import top.yukonga.miuix.kmp.icon.extended.Playlist
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal enum class BottomDockMode {
    Expanded,
    Compact
}

internal data class BottomDockTab(
    val route: String,
    val label: String,
    val icon: ImageVector
)

@Composable
internal fun FloatingBottomControls(
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
    val context = androidx.compose.ui.platform.LocalContext.current
    var queueSheetExpanded by remember { mutableStateOf(false) }
    var queueSongsToAdd by remember { mutableStateOf<List<Song>?>(null) }
    var queueSongsForNewPlaylist by remember { mutableStateOf<List<Song>?>(null) }
    val playlist by playerViewModel.playlist.collectAsState()
    val userPlaylists by mainViewModel.playlists.collectAsState()
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
            Box(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .pointerInput(showMiniPlayer, showBottomBar) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    event.changes.forEach { it.consume() }
                                }
                            }
                        }
                )
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
                                .padding(horizontal = 16.dp, vertical = 2.dp),
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
    }

    if (queueSheetExpanded) {
        EllaMiuixBottomSheet(
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
                onAddQueueToPlaylist = {
                    queueSheetExpanded = false
                    queueSongsToAdd = playlist
                },
                onClearQueue = {
                    queueSheetExpanded = false
                    playerViewModel.clearPlaylist()
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    queueSongsToAdd?.let { songsToAdd ->
        EllaMiuixBottomSheet(
            show = true,
            enableNestedScroll = false,
            title = stringResource(R.string.player_add_to_playlist),
            onDismissRequest = { queueSongsToAdd = null }
        ) {
            AddToPlaylistSheet(
                playlists = userPlaylists.sortedWith(
                    compareByDescending<com.ella.music.data.model.UserPlaylist> { it.id == FAVORITES_PLAYLIST_ID }
                        .thenByDescending { it.createdAt }
                ),
                onDismiss = { queueSongsToAdd = null },
                onCreatePlaylist = {
                    queueSongsForNewPlaylist = songsToAdd
                    queueSongsToAdd = null
                },
                onPlaylistsConfirm = { selectedPlaylists, appendToEnd ->
                    selectedPlaylists.forEach { target ->
                        mainViewModel.addSongsToPlaylist(target.id, songsToAdd, appendToEnd)
                    }
                    Toast.makeText(
                        context,
                        context.getString(R.string.player_added_to_playlists, selectedPlaylists.size),
                        Toast.LENGTH_SHORT
                    ).show()
                    queueSongsToAdd = null
                }
            )
        }
    }

    queueSongsForNewPlaylist?.let { songsToAdd ->
        CreatePlaylistAndAddSheet(
            onDismiss = { queueSongsForNewPlaylist = null },
            onCreate = { name ->
                mainViewModel.createPlaylist(name) { target ->
                    if (target != null) {
                        mainViewModel.addSongsToPlaylist(target.id, songsToAdd)
                        Toast.makeText(
                            context,
                            context.getString(R.string.player_added_to_playlist_named, target.name),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                queueSongsForNewPlaylist = null
            }
        )
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
            selected = !isSearchSelected,
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
