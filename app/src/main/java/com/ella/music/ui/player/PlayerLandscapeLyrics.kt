package com.ella.music.ui.player

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ella.music.data.model.LyricLine
import com.ella.music.data.model.Song
import com.ella.music.ui.components.SmoothLyricView
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Photos

@Composable
internal fun LandscapeLyricsOverlay(
    song: Song?,
    embeddedCover: Bitmap?,
    annotation: String,
    lyrics: List<LyricLine>,
    currentLyricIndex: Int,
    currentPosition: Long,
    duration: Long,
    shuffleEnabled: Boolean,
    repeatMode: Int,
    showTranslation: Boolean,
    showPronunciation: Boolean,
    fontFamily: FontFamily?,
    fontPath: String,
    fontWeight: FontWeight,
    fontScale: Float,
    showTotalDuration: Boolean,
    palette: PlayerPalette,
    flowEffectMode: Int,
    isPlaying: Boolean,
    audioSessionId: Int,
    visualizerEnabled: Boolean,
    beautifulLyricsBackground: Boolean,
    onLineClick: (LyricLine) -> Unit,
    onLineLongClick: (LyricLine) -> Unit,
    onSeek: (Float) -> Unit,
    onCyclePlaybackMode: () -> Unit,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onShowCoverPlayer: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.background(palette.middle)) {
        if (beautifulLyricsBackground) {
            BeautifulLyricsDynamicBackground(
                palette = palette,
                positionMs = currentPosition,
                isPlaying = isPlaying,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            FluidLyricBackground(
                palette = palette,
                positionMs = currentPosition,
                isPlaying = isPlaying,
                flowEffectMode = flowEffectMode,
                modifier = Modifier.fillMaxSize()
            )
        }
        Row(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(start = 34.dp, end = 48.dp, top = 22.dp, bottom = 28.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(0.33f)
                    .widthIn(max = 400.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    AlbumArtView(
                        song = song,
                        embeddedCover = embeddedCover,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(16.dp))
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                LandscapeSongTitle(
                    song = song,
                    annotation = annotation,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(6.dp))
                LandscapeProgressRow(
                    currentPosition = currentPosition,
                    duration = duration,
                    palette = palette,
                    allowTapSeek = false,
                    showTotalDuration = showTotalDuration,
                    onSeek = onSeek
                )
                LandscapeTransportControls(
                    isPlaying = isPlaying,
                    shuffleEnabled = shuffleEnabled,
                    repeatMode = repeatMode,
                    palette = palette,
                    onCyclePlaybackMode = onCyclePlaybackMode,
                    onPrevious = onPrevious,
                    onPlayPause = onPlayPause,
                    onNext = onNext
                )
            }
            Spacer(modifier = Modifier.width(34.dp))
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(0.67f)
            ) {
                SmoothLyricView(
                    songId = song?.id ?: 0L,
                    songTitle = song?.title.orEmpty(),
                    songArtist = song?.artist.orEmpty(),
                    lyrics = lyrics,
                    currentIndex = currentLyricIndex,
                    currentPositionMs = currentPosition,
                    isPlaying = isPlaying,
                    showTranslation = showTranslation,
                    showPronunciation = showPronunciation,
                    fontScale = fontScale,
                    fontPath = fontPath,
                    fontWeight = fontWeight,
                    primaryTextSizeSp = 30f,
                    secondaryTextSizeSp = 15f,
                    anchorOffsetRatio = -0.06f,
                    topContentPadding = 12.dp,
                    onLineClick = onLineClick,
                    onLineLongClick = onLineLongClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                )
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = 26.dp, end = 92.dp)
                .size(56.dp)
                .clip(CircleShape)
                .clickable(onClick = onShowCoverPlayer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = MiuixIcons.Regular.Photos,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.92f),
                modifier = Modifier.size(26.dp)
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = 26.dp, end = 28.dp)
                .size(56.dp)
                .clip(CircleShape)
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            CloseIcon(
                color = Color.White.copy(alpha = 0.92f),
                modifier = Modifier.size(26.dp)
            )
        }
    }

}
