package com.ella.music.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import com.ella.music.data.SettingsManager
import com.ella.music.ui.components.TagEditorOptionIds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * All DataStore-backed settings the player screen needs, collected through a single combined
 * flow instead of ~17 independent [collectAsState] subscriptions.
 *
 * PlayerScreen reads every one of these at the top of the same composable, so any single
 * setting change already recomposes the whole body — bundling them does not widen the
 * recomposition scope, it only collapses the burst of collectors (and their initial
 * emissions) that used to spin up each time the player surface entered composition.
 */
internal data class PlayerScreenSettings(
    val playerTapSeekEnabled: Boolean = true,
    val playerShowTotalDuration: Boolean = false,
    val lyricSourceMode: Int = SettingsManager.LYRIC_SOURCE_AUTO,
    val audioVisualizerEnabled: Boolean = false,
    val audioVisualizerOpacity: Int = 100,
    val dynamicCoverEnabled: Boolean = false,
    val immersiveAlbumCover: Boolean = true,
    val playerBackgroundEnabled: Boolean = false,
    val playerBackgroundUri: String = "",
    val playerBackgroundOpacity: Int = 100,
    val playerBackgroundDim: Int = 26,
    val beautifulLyricsBackground: Boolean = false,
    val showSongAnnotation: Boolean = true,
    val coverSwipeEnabled: Boolean = true,
    val playerKeepScreenOn: Boolean = false,
    val hiResLogoEnabled: Boolean = false,
    val hiResLogoUri: String = "",
    val lyricShareCustomInfo: String = "",
    val metadataEditorId: String = TagEditorOptionIds.ASK_EACH_TIME,
    val lyricTimingEditorId: String = TagEditorOptionIds.ASK_EACH_TIME,
    val sleepTimerCustomMinutes: Int = 45,
    val sleepTimerStopAfterCurrent: Boolean = false,
    val lyricPageKeepScreenOn: Boolean = false,
    val lyricPerspectiveEffect: Boolean = false,
    val playerLyricTextAlign: Int = SettingsManager.PLAYER_LYRIC_ALIGN_LEFT
)

private data class PlayerSettingsGroupA(
    val playerTapSeekEnabled: Boolean,
    val playerShowTotalDuration: Boolean,
    val lyricSourceMode: Int,
    val audioVisualizerEnabled: Boolean,
    val audioVisualizerOpacity: Int,
    val dynamicCoverEnabled: Boolean
)

private data class PlayerSettingsVisualizer(
    val enabled: Boolean,
    val opacity: Int
)

private data class PlayerSettingsGroupB(
    val immersiveAlbumCover: Boolean,
    val playerBackgroundEnabled: Boolean,
    val playerBackgroundUri: String,
    val playerBackgroundOpacity: Int,
    val playerBackgroundDim: Int,
    val beautifulLyricsBackground: Boolean,
    val showSongAnnotation: Boolean,
    val coverSwipeEnabled: Boolean,
    val playerKeepScreenOn: Boolean,
    val hiResLogoEnabled: Boolean,
    val hiResLogoUri: String
)

private data class PlayerSettingsGroupBBase(
    val immersiveAlbumCover: Boolean,
    val playerBackgroundEnabled: Boolean,
    val playerBackgroundUri: String,
    val playerBackgroundOpacity: Int,
    val playerBackgroundDim: Int
)

private data class PlayerSettingsGroupBExtra(
    val beautifulLyricsBackground: Boolean,
    val showSongAnnotation: Boolean,
    val coverSwipeEnabled: Boolean,
    val playerKeepScreenOn: Boolean,
    val hiResLogoEnabled: Boolean,
    val hiResLogoUri: String
)

private data class PlayerSettingsGroupBFlags(
    val beautifulLyricsBackground: Boolean,
    val showSongAnnotation: Boolean,
    val coverSwipeEnabled: Boolean,
    val playerKeepScreenOn: Boolean
)

private data class PlayerSettingsGroupBHiRes(
    val hiResLogoEnabled: Boolean,
    val hiResLogoUri: String
)

private data class PlayerSettingsGroupC(
    val lyricShareCustomInfo: String,
    val metadataEditorId: String,
    val lyricTimingEditorId: String,
    val sleepTimerCustomMinutes: Int,
    val sleepTimerStopAfterCurrent: Boolean
)

private data class PlayerSettingsGroupD(
    val lyricPageKeepScreenOn: Boolean,
    val lyricPerspectiveEffect: Boolean,
    val playerLyricTextAlign: Int
)

@Composable
internal fun rememberPlayerScreenSettings(settingsManager: SettingsManager): PlayerScreenSettings {
    val flow: Flow<PlayerScreenSettings> = remember(settingsManager) {
        val visualizer = combine(
            settingsManager.audioVisualizerEnabled,
            settingsManager.audioVisualizerOpacity
        ) { enabled, opacity ->
            PlayerSettingsVisualizer(enabled, opacity)
        }
        val groupA = combine(
            settingsManager.playerTapSeekEnabled,
            settingsManager.playerShowTotalDuration,
            settingsManager.lyricSourceMode,
            visualizer,
            settingsManager.dynamicCoverEnabled
        ) { tapSeek, showTotal, lyricSource, visualizerState, dynamicCover ->
            PlayerSettingsGroupA(tapSeek, showTotal, lyricSource, visualizerState.enabled, visualizerState.opacity, dynamicCover)
        }
        val groupBBase = combine(
            settingsManager.playerImmersiveCover,
            settingsManager.playerBackgroundEnabled,
            settingsManager.playerBackgroundUri,
            settingsManager.playerBackgroundOpacity,
            settingsManager.playerBackgroundDim
        ) { immersive, bgEnabled, bgUri, bgOpacity, bgDim ->
            PlayerSettingsGroupBBase(immersive, bgEnabled, bgUri, bgOpacity, bgDim)
        }
        val groupBFlags = combine(
            settingsManager.playerBeautifulLyricsBackground,
            settingsManager.playerShowSongAnnotation,
            settingsManager.playerCoverSwipeEnabled,
            settingsManager.playerKeepScreenOn
        ) { beautifulLyrics, showAnnotation, coverSwipe, keepScreenOn ->
            PlayerSettingsGroupBFlags(beautifulLyrics, showAnnotation, coverSwipe, keepScreenOn)
        }
        val groupBHiRes = combine(
            settingsManager.hiResLogoEnabled,
            settingsManager.hiResLogoUri
        ) { hiResEnabled, hiResUri ->
            PlayerSettingsGroupBHiRes(hiResEnabled, hiResUri)
        }
        val groupBExtra = combine(groupBFlags, groupBHiRes) { flags, hiRes ->
            PlayerSettingsGroupBExtra(
                beautifulLyricsBackground = flags.beautifulLyricsBackground,
                showSongAnnotation = flags.showSongAnnotation,
                coverSwipeEnabled = flags.coverSwipeEnabled,
                playerKeepScreenOn = flags.playerKeepScreenOn,
                hiResLogoEnabled = hiRes.hiResLogoEnabled,
                hiResLogoUri = hiRes.hiResLogoUri
            )
        }
        val groupB = combine(groupBBase, groupBExtra) { base, extra ->
            PlayerSettingsGroupB(
                immersiveAlbumCover = base.immersiveAlbumCover,
                playerBackgroundEnabled = base.playerBackgroundEnabled,
                playerBackgroundUri = base.playerBackgroundUri,
                playerBackgroundOpacity = base.playerBackgroundOpacity,
                playerBackgroundDim = base.playerBackgroundDim,
                beautifulLyricsBackground = extra.beautifulLyricsBackground,
                showSongAnnotation = extra.showSongAnnotation,
                coverSwipeEnabled = extra.coverSwipeEnabled,
                playerKeepScreenOn = extra.playerKeepScreenOn,
                hiResLogoEnabled = extra.hiResLogoEnabled,
                hiResLogoUri = extra.hiResLogoUri
            )
        }
        val groupC = combine(
            settingsManager.lyricShareCustomInfo,
            settingsManager.metadataEditorId,
            settingsManager.lyricTimingEditorId,
            settingsManager.sleepTimerCustomMinutes,
            settingsManager.sleepTimerStopAfterCurrent
        ) { shareInfo, metadataId, timingId, customMinutes, stopAfterCurrent ->
            PlayerSettingsGroupC(shareInfo, metadataId, timingId, customMinutes, stopAfterCurrent)
        }
        val groupD = combine(
            settingsManager.lyricPageKeepScreenOn,
            settingsManager.lyricPerspectiveEffect,
            settingsManager.playerLyricTextAlign
        ) { keepScreenOn, perspective, lyricTextAlign ->
            PlayerSettingsGroupD(keepScreenOn, perspective, lyricTextAlign)
        }
        combine(groupA, groupB, groupC, groupD) { a, b, c, d ->
            PlayerScreenSettings(
                playerTapSeekEnabled = a.playerTapSeekEnabled,
                playerShowTotalDuration = a.playerShowTotalDuration,
                lyricSourceMode = a.lyricSourceMode,
                audioVisualizerEnabled = a.audioVisualizerEnabled,
                audioVisualizerOpacity = a.audioVisualizerOpacity,
                dynamicCoverEnabled = a.dynamicCoverEnabled,
                immersiveAlbumCover = b.immersiveAlbumCover,
                playerBackgroundEnabled = b.playerBackgroundEnabled,
                playerBackgroundUri = b.playerBackgroundUri,
                playerBackgroundOpacity = b.playerBackgroundOpacity,
                playerBackgroundDim = b.playerBackgroundDim,
                beautifulLyricsBackground = b.beautifulLyricsBackground,
                showSongAnnotation = b.showSongAnnotation,
                coverSwipeEnabled = b.coverSwipeEnabled,
                playerKeepScreenOn = b.playerKeepScreenOn,
                hiResLogoEnabled = b.hiResLogoEnabled,
                hiResLogoUri = b.hiResLogoUri,
                lyricShareCustomInfo = c.lyricShareCustomInfo,
                metadataEditorId = c.metadataEditorId,
                lyricTimingEditorId = c.lyricTimingEditorId,
                sleepTimerCustomMinutes = c.sleepTimerCustomMinutes,
                sleepTimerStopAfterCurrent = c.sleepTimerStopAfterCurrent,
                lyricPageKeepScreenOn = d.lyricPageKeepScreenOn,
                lyricPerspectiveEffect = d.lyricPerspectiveEffect,
                playerLyricTextAlign = d.playerLyricTextAlign
            )
        }
    }
    val settings by flow.collectAsState(initial = PlayerScreenSettings())
    return settings
}
