package com.ella.music.ui.player

import android.content.Context
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.ella.music.R
import com.ella.music.data.NeteaseKeyInfo
import com.ella.music.data.SettingsManager
import com.ella.music.data.model.AudioInfo
import com.ella.music.data.model.LyricLine
import com.ella.music.data.model.Song
import com.ella.music.data.model.SongTagInfo
import com.ella.music.data.model.albumIdentityId
import com.ella.music.data.neteaseAlbumUrl
import com.ella.music.data.neteaseArtistUrl
import com.ella.music.data.neteaseSongUrl
import com.ella.music.data.repository.MusicRepository
import com.ella.music.ui.components.TagEditorOptionKind
import com.ella.music.ui.components.openSongSpectrumWithAspectPro
import com.ella.music.ui.components.shareLocalSong
import com.ella.music.viewmodel.MainViewModel
import com.ella.music.viewmodel.PlayerViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
internal fun CoverPageContent(
    context: Context,
    mainViewModel: MainViewModel,
    playerViewModel: PlayerViewModel,
    settingsManager: SettingsManager,
    scope: CoroutineScope,
    song: Song?,
    embeddedCover: Bitmap?,
    songAnnotation: String,
    dynamicCoverFailedPath: String?,
    dynamicCoverEnabled: Boolean,
    immersiveAlbumCover: Boolean,
    playerBackgroundEnabled: Boolean,
    playerBackgroundUri: String,
    playerBackgroundOpacity: Float,
    playerBackgroundDim: Float,
    beautifulLyricsBackground: Boolean,
    hiResLogoEnabled: Boolean,
    hiResLogoUri: String,
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    shuffleEnabled: Boolean,
    repeatMode: Int,
    audioInfo: AudioInfo?,
    palette: PlayerPalette,
    lyricPalette: PlayerPalette,
    lyrics: List<LyricLine>,
    currentLyricIndex: Int,
    miniLyricLine: LyricLine?,
    showLyricTranslation: Boolean,
    showLyricPronunciation: Boolean,
    lyricFontFamily: FontFamily?,
    effectiveLyricFontPath: String,
    lyricFontWeight: FontWeight,
    lyricFontScale: Float,
    playerTapSeekEnabled: Boolean,
    playerShowTotalDuration: Boolean,
    menuExpanded: Boolean,
    onMenuExpandedChange: (Boolean) -> Unit,
    queueExpanded: Boolean,
    onQueueExpandedChange: (Boolean) -> Unit,
    playlist: List<Song>,
    sleepTimerEndRealtimeMs: Long?,
    stopAfterCurrentEnabled: Boolean,
    sleepTimerCustomMinutes: Int,
    sleepTimerStopAfterCurrent: Boolean,
    playbackSpeed: Float,
    playbackPitch: Float,
    isCurrentSongFavorite: Boolean,
    audioSessionId: Int,
    audioVisualizerEnabled: Boolean,
    lyricOffsetMs: Long,
    metadataEditorId: String,
    lyricTimingEditorId: String,
    onVisualizerEnabled: (Boolean) -> Unit,
    onDynamicCoverFailedPathChange: (String) -> Unit,
    onDynamicCoverSheetSongChange: (Song?) -> Unit,
    onPlaylistPickerSongChange: (Song?) -> Unit,
    onPlaylistPickerSongsChange: (List<Song>?) -> Unit,
    onLandscapeCoverModeChange: (Boolean) -> Unit,
    onLandscapeExpandedChange: (Boolean) -> Unit,
    onSongInfoExpandedChange: (Boolean) -> Unit,
    onRatingSheetSongChange: (Song?) -> Unit,
    onAiSheetSongChange: (Song?) -> Unit,
    onTagEditorSongChange: (Song?) -> Unit,
    onTagEditorKindChange: (TagEditorOptionKind) -> Unit,
    onLyricMatchSongChange: (Song?) -> Unit,
    onRequestDeleteSong: (Song) -> Unit,
    onNavigateToAlbum: (Long) -> Unit,
    onNavigateToArtist: (String) -> Unit,
    openLyricSharePicker: (LyricLine) -> Unit,
    navigateToArtistOrChoose: (String) -> Unit,
    onShowLyrics: () -> Unit,
    modifier: Modifier = Modifier
) {
    var actionMenuInitialPage by remember { mutableStateOf(PlayerActionSheetPage.Main) }
    fun openTagEditor(kind: TagEditorOptionKind) {
        val current = song
        when {
            current == null -> {
                Toast.makeText(context, context.getString(R.string.player_no_song_playing), Toast.LENGTH_SHORT).show()
            }
            current.path.startsWith("http://", ignoreCase = true) ||
                current.path.startsWith("https://", ignoreCase = true) -> {
                Toast.makeText(
                    context,
                    context.getString(R.string.player_external_editor_not_supported_for_remote),
                    Toast.LENGTH_SHORT
                ).show()
            }
            else -> {
                onTagEditorKindChange(kind)
                onTagEditorSongChange(current)
                onMenuExpandedChange(false)
            }
        }
    }
    CoverPlayerPage(
        context = context,
        mainViewModel = mainViewModel,
        playerViewModel = playerViewModel,
        song = song,
        embeddedCover = embeddedCover,
        annotation = songAnnotation,
        dynamicCoverFailedPath = dynamicCoverFailedPath,
        dynamicCoverEnabled = dynamicCoverEnabled,
        immersiveAlbumCover = immersiveAlbumCover,
        playerBackgroundEnabled = playerBackgroundEnabled,
        playerBackgroundUri = playerBackgroundUri,
        playerBackgroundOpacity = playerBackgroundOpacity,
        playerBackgroundDim = playerBackgroundDim,
        beautifulLyricsBackground = beautifulLyricsBackground,
        hiResLogoEnabled = hiResLogoEnabled,
        hiResLogoUri = hiResLogoUri,
        isPlaying = isPlaying,
        currentPosition = currentPosition,
        duration = duration,
        shuffleEnabled = shuffleEnabled,
        repeatMode = repeatMode,
        audioInfo = audioInfo,
        palette = palette,
        flowEffectMode = SettingsManager.PLAYER_FLOW_EFFECT_DARK,
        dynamicFlowEnabled = false,
        lyrics = lyrics,
        currentLyricIndex = currentLyricIndex,
        miniLyricLine = miniLyricLine,
        showTranslation = showLyricTranslation,
        showPronunciation = showLyricPronunciation,
        fontFamily = lyricFontFamily,
        fontPath = effectiveLyricFontPath,
        fontWeight = lyricFontWeight,
        fontScale = lyricFontScale,
        playerTapSeekEnabled = playerTapSeekEnabled,
        playerShowTotalDuration = playerShowTotalDuration,
        menuExpanded = menuExpanded,
        queueExpanded = queueExpanded,
        playlist = playlist,
        sleepTimerEndRealtimeMs = sleepTimerEndRealtimeMs,
        stopAfterCurrentEnabled = stopAfterCurrentEnabled,
        sleepTimerCustomMinutes = sleepTimerCustomMinutes,
        sleepTimerStopAfterCurrent = sleepTimerStopAfterCurrent,
        onDynamicCoverFailed = { onDynamicCoverFailedPathChange(it) },
        onMatchDynamicCover = {
            onMenuExpandedChange(false)
            onDynamicCoverSheetSongChange(song)
        },
        onToggleMenu = {
            actionMenuInitialPage = PlayerActionSheetPage.Main
            onMenuExpandedChange(!menuExpanded)
        },
        onToggleFavorite = { playerViewModel.toggleCurrentSongFavorite() },
        onDismissMenu = { onMenuExpandedChange(false) },
        onToggleQueue = { onQueueExpandedChange(!queueExpanded) },
        onDismissQueue = { onQueueExpandedChange(false) },
        onShowLyrics = onShowLyrics,
        onLyricLineClick = { line -> playerViewModel.seekTo(line.timeMs) },
        onLyricLineLongClick = openLyricSharePicker,
        onSeek = { fraction -> playerViewModel.seekTo((fraction * duration).toLong()) },
        onCyclePlaybackMode = { playerViewModel.cyclePlaybackMode() },
        onPrevious = { playerViewModel.skipToPrevious() },
        onPlayPause = { playerViewModel.togglePlayPause() },
        onNext = { playerViewModel.skipToNext() },
        onQueueSongClick = { index ->
            onQueueExpandedChange(false)
            playerViewModel.playQueueIndex(index)
        },
        onRemoveQueueSong = { index ->
            playerViewModel.removeFromPlaylist(index)
        },
        onMoveQueueSong = { fromIndex, toIndex ->
            playerViewModel.movePlaylistItem(fromIndex, toIndex)
        },
        onAddQueueToPlaylist = {
            onQueueExpandedChange(false)
            onPlaylistPickerSongsChange(playlist)
        },
        onClearQueue = {
            onQueueExpandedChange(false)
            playerViewModel.clearPlaylist()
        },
        onAlbum = {
            onMenuExpandedChange(false)
            val albumId = song?.albumIdentityId() ?: 0L
            if (albumId > 0L) onNavigateToAlbum(albumId)
            else Toast.makeText(context, context.getString(R.string.player_no_album_jump), Toast.LENGTH_SHORT).show()
        },
        onArtist = {
            onMenuExpandedChange(false)
            navigateToArtistOrChoose(song?.artist.orEmpty())
        },
        onNavigateToAlbumId = onNavigateToAlbum,
        onNavigateToArtistName = onNavigateToArtist,
        onDownload = {
            onMenuExpandedChange(false)
            val current = song
            if (current != null) {
                enqueuePlayerDownload(context, current)
                Toast.makeText(context, context.getString(R.string.player_download_started), Toast.LENGTH_SHORT).show()
            }
        },
        onLandscape = {
            onMenuExpandedChange(false)
            onLandscapeCoverModeChange(true)
            onLandscapeExpandedChange(true)
        },
        onSongInfo = {
            onMenuExpandedChange(false)
            onSongInfoExpandedChange(true)
        },
        onAddToPlaylist = {
            val current = song
            if (current != null) {
                onMenuExpandedChange(false)
                onPlaylistPickerSongChange(current)
            } else {
                Toast.makeText(context, context.getString(R.string.player_no_song_playing), Toast.LENGTH_SHORT).show()
            }
        },
        onShareSong = {
            val current = song
            if (current != null) shareLocalSong(context, current)
            else Toast.makeText(context, context.getString(R.string.player_no_share_song), Toast.LENGTH_SHORT).show()
        },
        onAddToQueue = {
            val current = song
            if (current != null) {
                playerViewModel.addToPlaylist(current)
                onMenuExpandedChange(false)
                Toast.makeText(context, context.getString(R.string.song_more_added_to_queue), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, context.getString(R.string.player_no_song_playing), Toast.LENGTH_SHORT).show()
            }
        },
        onPlayNext = {
            val current = song
            if (current != null) {
                playerViewModel.playNext(current)
                onMenuExpandedChange(false)
                Toast.makeText(context, context.getString(R.string.song_more_added_to_play_next), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, context.getString(R.string.player_no_song_playing), Toast.LENGTH_SHORT).show()
            }
        },
        onSetRating = {
            val current = song
            if (current != null) {
                onMenuExpandedChange(false)
                onRatingSheetSongChange(current)
            } else {
                Toast.makeText(context, context.getString(R.string.player_no_song_playing), Toast.LENGTH_SHORT).show()
            }
        },
        onAiInterpret = {
            val current = song
            if (current != null) {
                onMenuExpandedChange(false)
                onAiSheetSongChange(current)
            } else {
                Toast.makeText(context, context.getString(R.string.player_no_song_playing), Toast.LENGTH_SHORT).show()
            }
        },
        onSpectrum = {
            val current = song
            if (current != null) {
                onMenuExpandedChange(false)
                openSongSpectrumWithAspectPro(context, current)
            } else {
                Toast.makeText(context, context.getString(R.string.player_no_song_playing), Toast.LENGTH_SHORT).show()
            }
        },
        onDeleteSong = {
            val current = song
            if (current != null) {
                onMenuExpandedChange(false)
                onRequestDeleteSong(current)
            } else {
                Toast.makeText(context, context.getString(R.string.player_no_song_playing), Toast.LENGTH_SHORT).show()
            }
        },
        onOpenTimer = {
            actionMenuInitialPage = PlayerActionSheetPage.Timer
            onMenuExpandedChange(true)
        },
        onOpenMetadataEditor = {
            openTagEditor(TagEditorOptionKind.Metadata)
        },
        onEditMetadata = {
            openTagEditor(TagEditorOptionKind.Metadata)
        },
        onLyricTiming = {
            openTagEditor(TagEditorOptionKind.LyricTiming)
        },
        onMatchOnlineLyrics = {
            val current = song
            if (current != null) {
                onMenuExpandedChange(false)
                onLyricMatchSongChange(current)
            } else {
                Toast.makeText(context, context.getString(R.string.player_no_song_playing), Toast.LENGTH_SHORT).show()
            }
        },
        onStopAfterCurrent = {
            scope.launch { settingsManager.setSleepTimerStopAfterCurrent(it) }
            if (sleepTimerEndRealtimeMs == null) {
                playerViewModel.setStopAfterCurrentEnabled(it)
            } else if (!it) {
                playerViewModel.setStopAfterCurrentEnabled(false)
            }
            Toast.makeText(
                context,
                if (it) context.getString(R.string.player_pause_after_current_on) else context.getString(R.string.player_pause_after_current_off),
                Toast.LENGTH_SHORT
            ).show()
        },
        onTimer = { minutes ->
            scope.launch { settingsManager.setSleepTimerCustomMinutes(minutes) }
            playerViewModel.setStopAfterCurrentEnabled(false)
            playerViewModel.startSleepTimer(
                minutes = minutes,
                stopAfterCurrentWhenExpired = sleepTimerStopAfterCurrent
            )
            Toast.makeText(context, context.getString(R.string.player_sleep_timer_minutes, minutes), Toast.LENGTH_SHORT).show()
        },
        onCustomTimerMinutes = { minutes ->
            scope.launch { settingsManager.setSleepTimerCustomMinutes(minutes) }
        },
        onCancelTimer = {
            playerViewModel.cancelSleepTimer()
            Toast.makeText(context, context.getString(R.string.player_sleep_timer_cancelled), Toast.LENGTH_SHORT).show()
        },
        onSpeed = { playerViewModel.setPlaybackSpeed(it) },
        onPitch = { playerViewModel.setPlaybackPitch(it) },
        onLyricOffset = { playerViewModel.setCurrentLyricOffsetMs(it) },
        playbackSpeed = playbackSpeed,
        playbackPitch = playbackPitch,
        isFavorite = isCurrentSongFavorite,
        audioSessionId = audioSessionId,
        visualizerEnabled = audioVisualizerEnabled,
        lyricOffsetMs = lyricOffsetMs,
        metadataEditorId = metadataEditorId,
        lyricTimingEditorId = lyricTimingEditorId,
        onVisualizerEnabled = onVisualizerEnabled,
        actionMenuInitialPage = actionMenuInitialPage,
        modifier = modifier
    )
}

@Composable
internal fun LyricsPageContent(
    song: Song?,
    embeddedCover: Bitmap?,
    songAnnotation: String,
    lyrics: List<LyricLine>,
    currentLyricIndex: Int,
    currentPosition: Long,
    showLyricTranslation: Boolean,
    showLyricPronunciation: Boolean,
    lyricPageKeepScreenOn: Boolean,
    lyricFormatAvailability: MusicRepository.LyricFormatAvailability,
    preferTtmlLyrics: Boolean?,
    lyricSourceMode: Int,
    lyricFontFamily: FontFamily?,
    effectiveLyricFontPath: String,
    lyricFontWeight: FontWeight,
    lyricFontScale: Float,
    lyricPerspectiveEffect: Boolean,
    lyricPalette: PlayerPalette,
    isPlaying: Boolean,
    playerBackgroundEnabled: Boolean,
    playerBackgroundUri: String,
    playerBackgroundOpacity: Float,
    playerBackgroundDim: Float,
    beautifulLyricsBackground: Boolean,
    isCurrentSongFavorite: Boolean,
    audioSessionId: Int,
    effectiveAudioVisualizerEnabled: Boolean,
    playerViewModel: PlayerViewModel,
    settingsManager: SettingsManager,
    scope: CoroutineScope,
    openLyricSharePicker: (LyricLine) -> Unit,
    navigateToArtistOrChoose: (String) -> Unit,
    onDismissLyrics: () -> Unit,
    enableSwipeDismiss: Boolean,
    immersiveAlbumCover: Boolean,
    modifier: Modifier = Modifier
) {
    LyricsPlayerPage(
        song = song,
        embeddedCover = embeddedCover,
        annotation = songAnnotation,
        lyrics = lyrics,
        currentLyricIndex = currentLyricIndex,
        currentPosition = currentPosition,
        showTranslation = showLyricTranslation,
        showPronunciation = showLyricPronunciation,
        keepScreenOn = lyricPageKeepScreenOn,
        lyricFormatAvailability = lyricFormatAvailability,
        preferTtmlLyrics = preferTtmlLyrics,
        lyricSourceMode = lyricSourceMode,
        fontFamily = lyricFontFamily,
        fontPath = effectiveLyricFontPath,
        fontWeight = lyricFontWeight,
        italic = false,
        fontScale = lyricFontScale,
        perspectiveEffect = lyricPerspectiveEffect,
        palette = lyricPalette,
        flowEffectMode = SettingsManager.PLAYER_FLOW_EFFECT_DARK,
        currentPositionMs = currentPosition,
        isPlaying = isPlaying,
        playerBackgroundEnabled = playerBackgroundEnabled,
        playerBackgroundUri = playerBackgroundUri,
        playerBackgroundOpacity = playerBackgroundOpacity,
        playerBackgroundDim = playerBackgroundDim,
        beautifulLyricsBackground = beautifulLyricsBackground,
        isFavorite = isCurrentSongFavorite,
        audioSessionId = audioSessionId,
        visualizerEnabled = effectiveAudioVisualizerEnabled,
        onLineClick = { line -> playerViewModel.seekTo(line.timeMs) },
        onLineDoubleClick = { playerViewModel.togglePlayPause() },
        onLineLongClick = openLyricSharePicker,
        onDismissLyrics = onDismissLyrics,
        onTogglePronunciation = {
            playerViewModel.setLyricPagePronunciation(!showLyricPronunciation)
        },
        onToggleTranslation = {
            playerViewModel.setLyricPageTranslation(!showLyricTranslation)
        },
        onToggleKeepScreenOn = {
            scope.launch { settingsManager.setLyricPageKeepScreenOn(!lyricPageKeepScreenOn) }
        },
        onToggleFavorite = { playerViewModel.toggleCurrentSongFavorite() },
        onFontScale = { scale ->
            scope.launch { settingsManager.setLyricFontScale((scale * 100f).toInt()) }
        },
        onLyricSourceMode = { mode ->
            playerViewModel.setLyricSourceMode(mode)
        },
        onLyricFormatPreference = { preferTtml ->
            playerViewModel.setLyricFormatPreference(preferTtml)
        },
        onArtist = {
            navigateToArtistOrChoose(song?.artist.orEmpty())
        },
        enableSwipeDismiss = enableSwipeDismiss,
        useBlurBackground = immersiveAlbumCover,
        modifier = modifier
    )
}

@Composable
internal fun DetailPageContent(
    context: Context,
    song: Song?,
    tagInfo: SongTagInfo?,
    neteaseInfo: NeteaseKeyInfo?,
    playerBackgroundUri: String,
    playerBackgroundOpacity: Float,
    playerBackgroundDim: Float,
    immersiveAlbumCover: Boolean,
    playerBackgroundEnabled: Boolean,
    onNavigateToAlbum: (Long) -> Unit,
    onNavigateToArtist: (String) -> Unit,
    onNavigateToMetadataCategory: (String, String) -> Unit,
    openNetease: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    PlayerDetailPage(
        song = song,
        tagInfo = tagInfo,
        neteaseInfo = neteaseInfo,
        customBackgroundUri = playerBackgroundUri.takeIf {
            !immersiveAlbumCover && playerBackgroundEnabled && playerBackgroundUri.isNotBlank()
        }.orEmpty(),
        customBackgroundOpacity = playerBackgroundOpacity,
        customBackgroundDim = playerBackgroundDim,
        onAlbum = {
            val albumId = song?.albumIdentityId() ?: 0L
            if (albumId > 0L) onNavigateToAlbum(albumId)
            else Toast.makeText(context, context.getString(R.string.player_no_album_jump), Toast.LENGTH_SHORT).show()
        },
        onArtist = { name -> onNavigateToArtist(name) },
        onComposer = { name -> onNavigateToMetadataCategory("composer", name) },
        onLyricist = { name -> onNavigateToMetadataCategory("lyricist", name) },
        onNeteaseSong = { openNetease(neteaseInfo?.musicId?.takeIf { it.isNotBlank() }?.let(::neteaseSongUrl)) },
        onNeteaseArtist = { id -> openNetease(neteaseArtistUrl(id)) },
        onNeteaseAlbum = { openNetease(neteaseInfo?.albumId?.takeIf { it.isNotBlank() }?.let(::neteaseAlbumUrl)) },
        modifier = modifier
    )
}
