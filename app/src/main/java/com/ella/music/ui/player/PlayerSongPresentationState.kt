package com.ella.music.ui.player

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import com.ella.music.data.NeteaseKeyInfo
import com.ella.music.data.decodeNeteaseKey
import com.ella.music.data.model.AudioInfo
import com.ella.music.data.model.Song
import com.ella.music.data.model.SongTagInfo
import com.ella.music.ui.components.CoverLoadLimiter
import com.ella.music.viewmodel.PlayerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class PlayerSongPresentationState(
    val embeddedCover: Bitmap?,
    val paletteBitmap: Bitmap?,
    val palette: PlayerPalette,
    val lyricPalette: PlayerPalette,
    val audioInfo: AudioInfo?,
    val tagInfo: SongTagInfo?,
    val annotation: String,
    val neteaseInfo: NeteaseKeyInfo?
)

@Composable
internal fun rememberPlayerSongPresentationState(
    context: Context,
    song: Song?,
    playerViewModel: PlayerViewModel,
    playerLight: Boolean = false
): PlayerSongPresentationState {
    val paletteDefault = if (playerLight) PlayerPalette.LightDefault else PlayerPalette.Default
    val embeddedCover by produceState<Bitmap?>(initialValue = null, song?.id, song?.dateModified, song?.fileSize) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                CoverLoadLimiter.run { song?.takeIf { it.coverUrl.isBlank() }?.let(playerViewModel::getCoverArtBitmap) }
            }.getOrNull()
        }
    }
    val paletteBitmap by produceState<Bitmap?>(initialValue = null, song?.id, song?.albumId, song?.coverUrl, song?.dateModified, song?.fileSize, embeddedCover) {
        value = withContext(Dispatchers.IO) {
            embeddedCover ?: song?.let { loadPaletteCoverBitmap(context, it) }
        }
    }
    val palette by produceState(initialValue = paletteDefault, paletteBitmap, playerLight) {
        value = withContext(Dispatchers.Default) { PlayerPalette.from(paletteBitmap, playerLight) }
    }
    val lyricPalette by produceState(initialValue = paletteDefault, paletteBitmap, playerLight) {
        value = withContext(Dispatchers.Default) { PlayerPalette.fromLyricBackground(paletteBitmap, playerLight) }
    }
    val audioInfo by produceState<AudioInfo?>(initialValue = null, song?.id, song?.dateModified, song?.fileSize) {
        value = withContext(Dispatchers.IO) { song?.let(playerViewModel::getAudioInfo) }
    }
    val tagInfo by produceState<SongTagInfo?>(initialValue = null, song?.id, song?.dateModified, song?.fileSize) {
        value = withContext(Dispatchers.IO) { song?.let(playerViewModel::getSongTagInfo) }
    }
    val annotation = tagInfo?.displayComment.orEmpty()
    val neteaseInfo = remember(tagInfo?.neteaseKey) { decodeNeteaseKey(tagInfo?.neteaseKey.orEmpty()) }

    return PlayerSongPresentationState(
        embeddedCover = embeddedCover,
        paletteBitmap = paletteBitmap,
        palette = palette,
        lyricPalette = lyricPalette,
        audioInfo = audioInfo,
        tagInfo = tagInfo,
        annotation = annotation,
        neteaseInfo = neteaseInfo
    )
}
