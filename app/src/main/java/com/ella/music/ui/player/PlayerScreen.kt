package com.ella.music.ui.player

import android.content.Context
import android.app.Activity
import android.media.AudioManager
import android.content.Intent
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.zIndex
import androidx.media3.common.Player
import androidx.core.view.WindowCompat
import com.ella.music.R
import com.ella.music.data.SettingsManager
import com.ella.music.data.splitArtistNames
import com.ella.music.data.tagIdentityKey
import com.ella.music.data.model.LyricLine
import com.ella.music.data.model.Song
import com.ella.music.data.model.playlistIdentityKey
import com.ella.music.player.PlaybackAudioSession
import com.ella.music.ui.components.TagEditorOptionIds
import com.ella.music.ui.components.shareLyricCard
import com.ella.music.viewmodel.MainViewModel
import com.ella.music.viewmodel.PlayerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.ln
import kotlin.math.sqrt
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Music
import top.yukonga.miuix.kmp.icon.extended.Photos
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun PlayerScreen(
    mainViewModel: MainViewModel,
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit,
    onNavigateToAlbum: (Long) -> Unit = {},
    onNavigateToArtist: (String) -> Unit = {},
    onNavigateToMetadataCategory: (String, String) -> Unit = { _, _ -> },
    onDismissProgressChange: (Float) -> Unit = {},
    openToken: Int = 0
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val settingsManager = remember { SettingsManager.getInstance(context) }
    val playerTapSeekEnabled by settingsManager.playerTapSeekEnabled.collectAsState(initial = true)
    val playerShowTotalDuration by settingsManager.playerShowTotalDuration.collectAsState(initial = false)
    val lyricSourceMode by settingsManager.lyricSourceMode.collectAsState(initial = SettingsManager.LYRIC_SOURCE_AUTO)
    val lyricFontState = rememberPlayerLyricFontState(context, settingsManager)
    val lyricFontFamily = lyricFontState.fontFamily
    val effectiveLyricFontPath = lyricFontState.fontPath
    val lyricFontWeight = lyricFontState.fontWeight
    val lyricFontScale = lyricFontState.fontScale
    val lyricShareTypeface = lyricFontState.shareTypeface
    val currentSong by playerViewModel.currentSong.collectAsState()
    val isPlaying by playerViewModel.isPlaying.collectAsState()
    val currentPosition = rememberThrottledPlayerPosition(
        positionFlow = playerViewModel.currentPosition,
        isPlaying = isPlaying,
        anchorKey = currentSong?.id
    )
    val duration by playerViewModel.duration.collectAsState()
    val shuffleEnabled by playerViewModel.shuffleEnabled.collectAsState()
    val repeatMode by playerViewModel.repeatMode.collectAsState()
    val playbackSpeed by playerViewModel.playbackSpeed.collectAsState()
    val playbackPitch by playerViewModel.playbackPitch.collectAsState()
    val audioSessionId by PlaybackAudioSession.audioSessionId.collectAsState()
    val audioVisualizerEnabled by settingsManager.audioVisualizerEnabled.collectAsState(initial = false)
    val dynamicCoverEnabled by settingsManager.dynamicCoverEnabled.collectAsState(initial = false)
    val immersiveAlbumCover by settingsManager.playerImmersiveCover.collectAsState(initial = true)
    val playerBackgroundEnabled by settingsManager.playerBackgroundEnabled.collectAsState(initial = false)
    val playerBackgroundUri by settingsManager.playerBackgroundUri.collectAsState(initial = "")
    val hiResLogoEnabled by settingsManager.hiResLogoEnabled.collectAsState(initial = false)
    val hiResLogoUri by settingsManager.hiResLogoUri.collectAsState(initial = "")
    val lyricShareCustomInfo by settingsManager.lyricShareCustomInfo.collectAsState(initial = "")
    val metadataEditorId by settingsManager.metadataEditorId.collectAsState(initial = TagEditorOptionIds.ASK_EACH_TIME)
    val lyricTimingEditorId by settingsManager.lyricTimingEditorId.collectAsState(initial = TagEditorOptionIds.ASK_EACH_TIME)
    val sleepTimerCustomMinutes by settingsManager.sleepTimerCustomMinutes.collectAsState(initial = 45)
    val sleepTimerStopAfterCurrent by settingsManager.sleepTimerStopAfterCurrent.collectAsState(initial = false)
    val playlists by mainViewModel.playlists.collectAsState()
    val playlist by playerViewModel.playlist.collectAsState()
    val lyrics by playerViewModel.lyrics.collectAsState()
    val lyricFormatAvailability by playerViewModel.lyricFormatAvailability.collectAsState()
    val preferTtmlLyrics by playerViewModel.preferTtmlLyrics.collectAsState()
    val currentLyricIndex by playerViewModel.currentLyricIndex.collectAsState()
    val showLyrics by playerViewModel.showLyrics.collectAsState()
    val showLyricTranslation by playerViewModel.showLyricTranslation.collectAsState()
    val showLyricPronunciation by playerViewModel.showLyricPronunciation.collectAsState()
    val lyricPageKeepScreenOn by settingsManager.lyricPageKeepScreenOn.collectAsState(initial = false)
    val lyricPerspectiveEffect by settingsManager.lyricPerspectiveEffect.collectAsState(initial = false)
    val favoriteSongKeys by playerViewModel.favoriteSongKeys.collectAsState()
    val sleepTimerEndRealtimeMs by playerViewModel.sleepTimerEndRealtimeMs.collectAsState()
    val stopAfterCurrentEnabled by playerViewModel.stopAfterCurrentEnabled.collectAsState()
    val currentLyricLine = lyrics.getOrNull(currentLyricIndex)
    val miniLyricLine = currentLyricLine
        ?.takeIf { it.hasMiniLyric() }
        ?: lyrics.firstOrNull { it.hasMiniLyric() }
    var menuExpanded by remember { mutableStateOf(false) }
    var dynamicCoverSheetSong by remember { mutableStateOf<Song?>(null) }
    var songInfoExpanded by remember { mutableStateOf(false) }
    var queueExpanded by remember { mutableStateOf(false) }
    var artistChoices by remember { mutableStateOf<List<String>>(emptyList()) }
    var playlistPickerSong by remember { mutableStateOf<Song?>(null) }
    var playlistPickerSongs by remember { mutableStateOf<List<Song>?>(null) }
    var createPlaylistSong by remember { mutableStateOf<Song?>(null) }
    var createPlaylistSongs by remember { mutableStateOf<List<Song>?>(null) }
    var ratingSheetSong by remember { mutableStateOf<Song?>(null) }
    var aiSheetSong by remember { mutableStateOf<Song?>(null) }
    var deleteConfirmSong by remember { mutableStateOf<Song?>(null) }
    var pendingWriteRetry by remember { mutableStateOf<(suspend () -> Unit)?>(null) }
    var landscapeExpanded by rememberSaveable { mutableStateOf(false) }
    var landscapeCoverMode by rememberSaveable { mutableStateOf(false) }
    var dynamicCoverFailedPath by remember { mutableStateOf<String?>(null) }
    val visualizerPermissionState = rememberPlayerVisualizerPermissionState(
        context = context,
        scope = scope,
        settingsManager = settingsManager,
        immersiveAlbumCover = immersiveAlbumCover,
        audioVisualizerEnabled = audioVisualizerEnabled,
        isPlaying = isPlaying,
        showLyrics = showLyrics,
        landscapeExpanded = landscapeExpanded
    )
    val effectiveAudioVisualizerEnabled = visualizerPermissionState.effectiveEnabled
    val setAudioVisualizerEnabled = visualizerPermissionState.setEnabled
    val deletePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            pendingWriteRetry?.let { retry ->
                scope.launch { retry() }
            }
            pendingWriteRetry = null
        } else {
            pendingWriteRetry = null
        }
    }
    PlayerSystemBarsEffect(
        context = context,
        view = view,
        trigger = landscapeExpanded
    )
    PlayerLyricKeepScreenOnEffect(
        view = view,
        showLyrics = showLyrics,
        keepScreenOn = lyricPageKeepScreenOn
    )

    val song = currentSong
    val isCurrentSongFavorite = song?.playlistIdentityKey()?.let { it in favoriteSongKeys } == true
    fun requestDeleteSong(targetSong: Song) {
        deleteConfirmSong = targetSong
    }
    val songPresentation = rememberPlayerSongPresentationState(
        context = context,
        song = song,
        playerViewModel = playerViewModel
    )
    val embeddedCover = songPresentation.embeddedCover
    val paletteBitmap = songPresentation.paletteBitmap
    val palette = songPresentation.palette
    val lyricPalette = songPresentation.lyricPalette
    val audioInfo = songPresentation.audioInfo
    val tagInfo = songPresentation.tagInfo
    val songAnnotation = songPresentation.annotation
    val neteaseInfo = songPresentation.neteaseInfo
    var lyricShareInitialLine by remember { mutableStateOf<LyricLine?>(null) }
    fun openLyricSharePicker(line: LyricLine) {
        lyricShareInitialLine = line
    }
    fun shareSelectedLyrics(lines: List<LyricLine>, includeTranslation: Boolean) {
        shareLyricCard(
            context = context,
            song = song,
            lines = lines,
            cover = embeddedCover ?: paletteBitmap,
            backgroundColors = listOf(
                palette.top.toArgb(),
                palette.middle.toArgb(),
                palette.bottom.toArgb()
            ),
            annotation = songAnnotation,
            customInfo = lyricShareCustomInfo,
            shareTypeface = lyricShareTypeface,
            includeTranslation = includeTranslation
        )
        lyricShareInitialLine = null
    }
    fun navigateToArtistOrChoose(artistText: String) {
        val artists = splitArtistNames(artistText)
            .distinctBy { it.tagIdentityKey() }
        when (artists.size) {
            0 -> Toast.makeText(context, context.getString(R.string.player_no_artist_jump), Toast.LENGTH_SHORT).show()
            1 -> onNavigateToArtist(artists.first())
            else -> artistChoices = artists
        }
    }
    fun openNetease(url: String?) {
        if (url.isNullOrBlank()) {
            Toast.makeText(context, context.getString(R.string.player_no_netease_jump), Toast.LENGTH_SHORT).show()
        } else {
            uriHandler.openUri(url)
        }
    }
    val playerPagerState = rememberPagerState(
        initialPage = PLAYER_PAGE_COVER,
        pageCount = { PLAYER_PAGE_COUNT }
    )
    PlayerPagerSyncEffects(
        immersiveAlbumCover = immersiveAlbumCover,
        showLyrics = showLyrics,
        pagerState = playerPagerState,
        onShowLyricsChange = playerViewModel::setShowLyrics
    )

    PlayerDismissMotionHost(
        openToken = openToken,
        onDismissProgressChange = onDismissProgressChange,
        onDismiss = {
            playerViewModel.setShowLyrics(false)
            onBack()
        },
        overlayContent = {
            PlayerLyricShareHost(
                song = song,
                lyrics = lyrics,
                initialLine = lyricShareInitialLine,
                embeddedCover = embeddedCover,
                paletteBitmap = paletteBitmap,
                palette = palette,
                annotation = songAnnotation,
                customInfo = lyricShareCustomInfo,
                shareTypeface = lyricShareTypeface,
                onDismiss = { lyricShareInitialLine = null },
                onShare = ::shareSelectedLyrics
            )
        }
    ) { dismissingPlayer ->
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                palette.top,
                                palette.middle,
                                palette.bottom
                            )
                        )
                    )
            )
            if (immersiveAlbumCover) {
                ImmersiveCoverBackground(
                    palette = palette,
                    flowEffectMode = SettingsManager.PLAYER_FLOW_EFFECT_DARK,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                ImmersiveCoverBackground(
                    palette = palette,
                    flowEffectMode = SettingsManager.PLAYER_FLOW_EFFECT_DARK,
                    modifier = Modifier.fillMaxSize()
                )
            }

            PlayerScreenPageHost(
                immersiveAlbumCover = immersiveAlbumCover,
                showLyrics = showLyrics,
                pagerState = playerPagerState,
                userScrollEnabled = !dismissingPlayer,
                onShowImmersiveLyrics = { playerViewModel.setShowLyrics(true) },
                onDismissImmersiveLyrics = { playerViewModel.setShowLyrics(false) },
                onShowPagedLyrics = {
                    scope.launch { playerPagerState.animateScrollToPage(PLAYER_PAGE_LYRICS) }
                },
                onDismissPagedLyrics = {
                    scope.launch { playerPagerState.animateScrollToPage(PLAYER_PAGE_COVER) }
                },
                coverPage = { onShowLyrics, pageModifier ->
                    CoverPageContent(
                        context = context,
                        mainViewModel = mainViewModel,
                        playerViewModel = playerViewModel,
                        settingsManager = settingsManager,
                        scope = scope,
                        song = song,
                        embeddedCover = embeddedCover,
                        songAnnotation = songAnnotation,
                        dynamicCoverFailedPath = dynamicCoverFailedPath,
                        dynamicCoverEnabled = dynamicCoverEnabled,
                        immersiveAlbumCover = immersiveAlbumCover,
                        playerBackgroundEnabled = playerBackgroundEnabled,
                        playerBackgroundUri = playerBackgroundUri,
                        hiResLogoEnabled = hiResLogoEnabled,
                        hiResLogoUri = hiResLogoUri,
                        isPlaying = isPlaying,
                        currentPosition = currentPosition,
                        duration = duration,
                        shuffleEnabled = shuffleEnabled,
                        repeatMode = repeatMode,
                        audioInfo = audioInfo,
                        palette = palette,
                        lyricPalette = lyricPalette,
                        lyrics = lyrics,
                        currentLyricIndex = currentLyricIndex,
                        miniLyricLine = miniLyricLine,
                        showLyricTranslation = showLyricTranslation,
                        showLyricPronunciation = showLyricPronunciation,
                        lyricFontFamily = lyricFontFamily,
                        effectiveLyricFontPath = effectiveLyricFontPath,
                        lyricFontWeight = lyricFontWeight,
                        lyricFontScale = lyricFontScale,
                        playerTapSeekEnabled = playerTapSeekEnabled,
                        playerShowTotalDuration = playerShowTotalDuration,
                        menuExpanded = menuExpanded,
                        onMenuExpandedChange = { menuExpanded = it },
                        queueExpanded = queueExpanded,
                        onQueueExpandedChange = { queueExpanded = it },
                        playlist = playlist,
                        sleepTimerEndRealtimeMs = sleepTimerEndRealtimeMs,
                        stopAfterCurrentEnabled = stopAfterCurrentEnabled,
                        sleepTimerCustomMinutes = sleepTimerCustomMinutes,
                        sleepTimerStopAfterCurrent = sleepTimerStopAfterCurrent,
                        playbackSpeed = playbackSpeed,
                        playbackPitch = playbackPitch,
                        isCurrentSongFavorite = isCurrentSongFavorite,
                        audioSessionId = audioSessionId,
                        audioVisualizerEnabled = audioVisualizerEnabled,
                        metadataEditorId = metadataEditorId,
                        lyricTimingEditorId = lyricTimingEditorId,
                        onVisualizerEnabled = setAudioVisualizerEnabled,
                        onDynamicCoverFailedPathChange = { dynamicCoverFailedPath = it },
                        onDynamicCoverSheetSongChange = { dynamicCoverSheetSong = it },
                        onPlaylistPickerSongChange = { playlistPickerSong = it },
                        onPlaylistPickerSongsChange = { playlistPickerSongs = it },
                        onLandscapeCoverModeChange = { landscapeCoverMode = it },
                        onLandscapeExpandedChange = { landscapeExpanded = it },
                        onSongInfoExpandedChange = { songInfoExpanded = it },
                        onRatingSheetSongChange = { ratingSheetSong = it },
                        onAiSheetSongChange = { aiSheetSong = it },
                        onRequestDeleteSong = ::requestDeleteSong,
                        onNavigateToAlbum = onNavigateToAlbum,
                        onNavigateToArtist = onNavigateToArtist,
                        openLyricSharePicker = ::openLyricSharePicker,
                        navigateToArtistOrChoose = ::navigateToArtistOrChoose,
                        onShowLyrics = onShowLyrics,
                        modifier = pageModifier
                    )
                },
                lyricsPage = { onDismissLyrics, enableSwipeDismiss, pageModifier ->
                    LyricsPageContent(
                        song = song,
                        embeddedCover = embeddedCover,
                        songAnnotation = songAnnotation,
                        lyrics = lyrics,
                        currentLyricIndex = currentLyricIndex,
                        currentPosition = currentPosition,
                        showLyricTranslation = showLyricTranslation,
                        showLyricPronunciation = showLyricPronunciation,
                        lyricPageKeepScreenOn = lyricPageKeepScreenOn,
                        lyricFormatAvailability = lyricFormatAvailability,
                        preferTtmlLyrics = preferTtmlLyrics,
                        lyricSourceMode = lyricSourceMode,
                        lyricFontFamily = lyricFontFamily,
                        effectiveLyricFontPath = effectiveLyricFontPath,
                        lyricFontWeight = lyricFontWeight,
                        lyricFontScale = lyricFontScale,
                        lyricPerspectiveEffect = lyricPerspectiveEffect,
                        lyricPalette = lyricPalette,
                        isPlaying = isPlaying,
                        playerBackgroundEnabled = playerBackgroundEnabled,
                        playerBackgroundUri = playerBackgroundUri,
                        isCurrentSongFavorite = isCurrentSongFavorite,
                        audioSessionId = audioSessionId,
                        effectiveAudioVisualizerEnabled = effectiveAudioVisualizerEnabled,
                        playerViewModel = playerViewModel,
                        settingsManager = settingsManager,
                        scope = scope,
                        openLyricSharePicker = ::openLyricSharePicker,
                        navigateToArtistOrChoose = ::navigateToArtistOrChoose,
                        onDismissLyrics = onDismissLyrics,
                        enableSwipeDismiss = enableSwipeDismiss,
                        immersiveAlbumCover = immersiveAlbumCover,
                        modifier = pageModifier
                    )
                },
                detailPage = { pageModifier ->
                    DetailPageContent(
                        context = context,
                        song = song,
                        tagInfo = tagInfo,
                        neteaseInfo = neteaseInfo,
                        playerBackgroundUri = playerBackgroundUri,
                        immersiveAlbumCover = immersiveAlbumCover,
                        playerBackgroundEnabled = playerBackgroundEnabled,
                        onNavigateToAlbum = onNavigateToAlbum,
                        onNavigateToArtist = onNavigateToArtist,
                        onNavigateToMetadataCategory = onNavigateToMetadataCategory,
                        openNetease = ::openNetease,
                        modifier = pageModifier
                    )
                },
                modifier = Modifier.fillMaxSize()
            )

            PlayerLandscapeOverlayHost(
                context = context,
                expanded = landscapeExpanded,
                coverMode = landscapeCoverMode,
                dynamicCoverEnabled = dynamicCoverEnabled,
                song = song,
                embeddedCover = embeddedCover,
                annotation = songAnnotation,
                dynamicCoverFailedPath = dynamicCoverFailedPath,
                isPlaying = isPlaying,
                currentPosition = currentPosition,
                duration = duration,
                shuffleEnabled = shuffleEnabled,
                repeatMode = repeatMode,
                audioInfo = audioInfo,
                palette = lyricPalette,
                lyrics = lyrics,
                currentLyricIndex = currentLyricIndex,
                showTranslation = showLyricTranslation,
                showPronunciation = showLyricPronunciation,
                fontFamily = lyricFontFamily,
                fontPath = effectiveLyricFontPath,
                fontWeight = lyricFontWeight,
                fontScale = lyricFontScale,
                showTotalDuration = playerShowTotalDuration,
                queueExpanded = queueExpanded,
                playlist = playlist,
                audioSessionId = audioSessionId,
                visualizerEnabled = effectiveAudioVisualizerEnabled,
                flowEffectMode = SettingsManager.PLAYER_FLOW_EFFECT_DARK,
                isFavorite = isCurrentSongFavorite,
                onDynamicCoverFailed = { dynamicCoverFailedPath = it },
                onToggleFavorite = { playerViewModel.toggleCurrentSongFavorite() },
                onToggleQueue = { queueExpanded = !queueExpanded },
                onDismissQueue = { queueExpanded = false },
                onShowLyrics = { landscapeCoverMode = false },
                onShowCoverPlayer = { landscapeCoverMode = true },
                onLyricLineClick = { line -> playerViewModel.seekTo(line.timeMs) },
                onLyricLineLongClick = ::openLyricSharePicker,
                onSeekProgress = { progress ->
                    if (duration > 0L) playerViewModel.seekTo((duration * progress).toLong())
                },
                onCyclePlaybackMode = { playerViewModel.cyclePlaybackMode() },
                onPrevious = { playerViewModel.skipToPrevious() },
                onPlayPause = { playerViewModel.togglePlayPause() },
                onNext = { playerViewModel.skipToNext() },
                onQueueSongClick = { index ->
                    queueExpanded = false
                    playerViewModel.playQueueIndex(index)
                },
                onRemoveQueueSong = { index -> playerViewModel.removeFromPlaylist(index) },
                onMoveQueueSong = { fromIndex, toIndex ->
                    playerViewModel.movePlaylistItem(fromIndex, toIndex)
                },
                onAddQueueToPlaylist = {
                    queueExpanded = false
                    playlistPickerSongs = playlist
                },
                onClearQueue = {
                    queueExpanded = false
                    playerViewModel.clearPlaylist()
                },
                onArtist = {
                    navigateToArtistOrChoose(song?.artist.orEmpty())
                },
                onDismiss = {
                    landscapeExpanded = false
                    landscapeCoverMode = false
                }
            )

            PlayerScreenSheetHost(
                context = context,
                scope = scope,
                mainViewModel = mainViewModel,
                playerViewModel = playerViewModel,
                song = song,
                playlists = playlists,
                artistChoices = artistChoices,
                onArtistChoicesChange = { artistChoices = it },
                onNavigateToArtist = onNavigateToArtist,
                songInfoExpanded = songInfoExpanded,
                onSongInfoExpandedChange = { songInfoExpanded = it },
                dynamicCoverSheetSong = dynamicCoverSheetSong,
                onDynamicCoverSheetSongChange = { dynamicCoverSheetSong = it },
                ratingSheetSong = ratingSheetSong,
                onRatingSheetSongChange = { ratingSheetSong = it },
                aiSheetSong = aiSheetSong,
                onAiSheetSongChange = { aiSheetSong = it },
                deleteConfirmSong = deleteConfirmSong,
                onDeleteConfirmSongChange = { deleteConfirmSong = it },
                onWritePermissionRequired = { error, retry ->
                    pendingWriteRetry = retry
                    deletePermissionLauncher.launch(
                        IntentSenderRequest.Builder(error.intentSender).build()
                    )
                },
                playlistPickerSong = playlistPickerSong,
                onPlaylistPickerSongChange = { playlistPickerSong = it },
                playlistPickerSongs = playlistPickerSongs,
                onPlaylistPickerSongsChange = { playlistPickerSongs = it },
                createPlaylistSong = createPlaylistSong,
                onCreatePlaylistSongChange = { createPlaylistSong = it },
                createPlaylistSongs = createPlaylistSongs,
                onCreatePlaylistSongsChange = { createPlaylistSongs = it }
            )
        }
    }
}
