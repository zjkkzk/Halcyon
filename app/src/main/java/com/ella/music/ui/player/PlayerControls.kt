package com.ella.music.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.data.SettingsManager
import com.ella.music.data.audioQualitySummary
import com.ella.music.data.model.AudioInfo
import com.ella.music.data.model.Song
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text

@Composable
internal fun LandscapeProgressRow(
    currentPosition: Long,
    duration: Long,
    palette: PlayerPalette,
    allowTapSeek: Boolean,
    showTotalDuration: Boolean,
    onSeek: (Float) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = formatTime(currentPosition),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.72f)
        )
        GlowSeekBar(
            value = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f,
            onSeek = onSeek,
            accent = palette.accent,
            allowTapSeek = allowTapSeek,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
        )
        Text(
            text = if (showTotalDuration) {
                formatTime(duration.coerceAtLeast(0L))
            } else {
                "-${formatTime((duration - currentPosition).coerceAtLeast(0L))}"
            },
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.72f)
        )
    }
}

@Composable
internal fun LandscapeTransportControls(
    isPlaying: Boolean,
    shuffleEnabled: Boolean,
    repeatMode: Int,
    palette: PlayerPalette,
    onCyclePlaybackMode: () -> Unit,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        PlayerTransportIconButton(onClick = onCyclePlaybackMode) {
            PlaybackModeIcon(shuffleEnabled = shuffleEnabled, repeatMode = repeatMode, accent = palette.accent)
        }
        PlayerTransportIconButton(onClick = onPrevious) {
            Icon(
                painter = painterResource(id = R.drawable.ic_skip_previous),
                contentDescription = stringResource(R.string.common_previous),
                tint = Color.White.copy(alpha = 0.92f),
                modifier = Modifier.size(28.dp)
            )
        }
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .playerNoIndicationClick(onPlayPause),
            contentAlignment = Alignment.Center
        ) {
            CenteredPlayPauseGlyph(
                isPlaying = isPlaying,
                tint = Color.White.copy(alpha = 0.96f),
                modifier = Modifier.size(34.dp)
            )
        }
        PlayerTransportIconButton(onClick = onNext) {
            Icon(
                painter = painterResource(id = R.drawable.ic_skip_next),
                contentDescription = stringResource(R.string.common_next),
                tint = Color.White.copy(alpha = 0.92f),
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
internal fun PlayerProgressBlock(
    currentPosition: Long,
    duration: Long,
    audioInfo: AudioInfo?,
    bluetoothDeviceName: String?,
    palette: PlayerPalette,
    allowTapSeek: Boolean,
    showTotalDuration: Boolean,
    onSeek: (Float) -> Unit
) {
    val context = LocalContext.current
    var infoMode by remember(audioInfo, bluetoothDeviceName) { mutableStateOf(0) }
    val infoLabels = remember(audioInfo, bluetoothDeviceName) {
        buildList {
            audioInfo?.let {
                val quality = audioQualitySummary(it)
                add(quality.playerCompactText())
                quality.detailLabel.takeIf { text -> text.isNotBlank() }?.let(::add)
            }
            bluetoothDeviceName?.takeIf { it.isNotBlank() }?.let(::add)
        }.distinct()
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        GlowSeekBar(
            value = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f,
            onSeek = onSeek,
            accent = palette.accent,
            allowTapSeek = allowTapSeek,
            modifier = Modifier.fillMaxWidth()
        )
        Box(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = formatTime(currentPosition),
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.56f),
                modifier = Modifier.align(Alignment.CenterStart)
            )
            if (infoLabels.isNotEmpty()) {
                val infoText = infoLabels[infoMode % infoLabels.size]
                Text(
                    text = infoText,
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.62f),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.10f))
                        .pointerInput(infoLabels, bluetoothDeviceName) {
                            detectTapGestures(
                                onTap = {
                                    if (infoLabels.size > 1) infoMode = (infoMode + 1) % infoLabels.size
                                },
                                onLongPress = {
                                    if (!bluetoothDeviceName.isNullOrBlank()) {
                                        openSystemOutputSwitcher(context)
                                    }
                                }
                            )
                        }
                        .padding(horizontal = 10.dp, vertical = 3.dp)
                )
            }
            Text(
                text = if (showTotalDuration) {
                    formatTime(duration.coerceAtLeast(0L))
                } else {
                    "-${formatTime((duration - currentPosition).coerceAtLeast(0L))}"
                },
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.56f),
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }
    }
}

@Composable
internal fun PlayerTransportControls(
    isPlaying: Boolean,
    shuffleEnabled: Boolean,
    repeatMode: Int,
    palette: PlayerPalette,
    queueExpanded: Boolean,
    playlist: List<Song>,
    currentSongId: Long?,
    onCyclePlaybackMode: () -> Unit,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onToggleQueue: () -> Unit,
    onDismissQueue: () -> Unit,
    onQueueSongClick: (Int) -> Unit,
    onRemoveQueueSong: (Int) -> Unit,
    onMoveQueueSong: (Int, Int) -> Unit,
    onAddQueueToPlaylist: () -> Unit,
    onClearQueue: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager.getInstance(context) }
    val showOutlines by settingsManager.transportButtonOutlines.collectAsState(initial = false)
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        PlayerTransportIconButton(onClick = onCyclePlaybackMode) {
            PlaybackModeIcon(shuffleEnabled = shuffleEnabled, repeatMode = repeatMode, accent = palette.accent)
        }
        PlayerTransportIconButton(onClick = onPrevious) {
            Icon(
                painter = painterResource(id = R.drawable.ic_skip_previous),
                contentDescription = stringResource(R.string.common_previous),
                tint = Color.White.copy(alpha = 0.92f),
                modifier = Modifier.size(38.dp)
            )
        }
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .then(if (showOutlines) Modifier.background(Color.White.copy(alpha = 0.18f)) else Modifier)
                .playerNoIndicationClick(onPlayPause),
            contentAlignment = Alignment.Center
        ) {
            CenteredPlayPauseGlyph(
                isPlaying = isPlaying,
                tint = Color.White.copy(alpha = 0.96f),
                modifier = Modifier.size(if (isPlaying) 38.dp else 40.dp)
            )
        }
        PlayerTransportIconButton(onClick = onNext) {
            Icon(
                painter = painterResource(id = R.drawable.ic_skip_next),
                contentDescription = stringResource(R.string.common_next),
                tint = Color.White.copy(alpha = 0.92f),
                modifier = Modifier.size(38.dp)
            )
        }
        Box(contentAlignment = Alignment.Center) {
            PlayerTransportIconButton(onClick = onToggleQueue) {
                QueueListIcon(
                    color = Color.White.copy(alpha = 0.58f),
                    modifier = Modifier.size(28.dp)
                )
            }
            PlayerQueueSheet(
                show = queueExpanded,
                playlist = playlist,
                currentSongId = currentSongId,
                onDismiss = onDismissQueue,
                onSongClick = onQueueSongClick,
                onRemoveSong = onRemoveQueueSong,
                onMoveSong = onMoveQueueSong,
                onAddQueueToPlaylist = onAddQueueToPlaylist,
                onClearQueue = onClearQueue
            )
        }
    }
}
