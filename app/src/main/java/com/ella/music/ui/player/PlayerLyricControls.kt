package com.ella.music.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.data.SettingsManager
import com.ella.music.data.repository.MusicRepository
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun LyricToggleButton(
    text: String,
    active: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(LocalPlayerContentColor.current.copy(alpha = if (active) 0.24f else 0.10f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = LocalPlayerContentColor.current.copy(alpha = if (active) 1f else 0.62f)
        )
    }
}

@Composable
internal fun LyricActionMenu(
    showPronunciation: Boolean,
    showTranslation: Boolean,
    keepScreenOn: Boolean,
    lyricFormatAvailability: MusicRepository.LyricFormatAvailability,
    preferTtmlLyrics: Boolean?,
    lyricSourceMode: Int,
    fontScale: Float,
    onTogglePronunciation: () -> Unit,
    onToggleTranslation: () -> Unit,
    onToggleKeepScreenOn: () -> Unit,
    onLyricSourceMode: (Int) -> Unit,
    onLyricFormatPreference: (Boolean) -> Unit,
    onFontScale: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 10.dp)
    ) {
        PlayerActionMenuItem(
            text = stringResource(if (showPronunciation) R.string.player_hide_pronunciation else R.string.player_show_pronunciation),
            onClick = onTogglePronunciation
        )
        PlayerActionMenuItem(
            text = stringResource(if (showTranslation) R.string.player_hide_translation else R.string.player_show_translation),
            onClick = onToggleTranslation
        )
        PlayerActionMenuItem(
            text = stringResource(if (keepScreenOn) R.string.player_disable_keep_screen_on else R.string.player_enable_keep_screen_on),
            onClick = onToggleKeepScreenOn
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.player_lyric_font_size),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
        )
        DottedValueSlider(
            value = fontScale.coerceIn(0.75f, 1.30f),
            valueRange = 0.75f..1.30f,
            steps = 11,
            label = "${(fontScale.coerceIn(0.75f, 1.30f) * 100f).toInt()}%",
            onValueChange = onFontScale,
            modifier = Modifier
                .fillMaxWidth()
                .height(82.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (lyricFormatAvailability.hasBoth) {
            Text(
                text = stringResource(R.string.player_lyric_format),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LyricSourceChip(
                    text = stringResource(R.string.player_lyric_format_ttml),
                    selected = preferTtmlLyrics != false,
                    onClick = { onLyricFormatPreference(true) },
                    modifier = Modifier.weight(1f)
                )
                LyricSourceChip(
                    text = stringResource(R.string.player_lyric_format_lrc),
                    selected = preferTtmlLyrics == false,
                    onClick = { onLyricFormatPreference(false) },
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
        Text(
            text = stringResource(R.string.player_lyric_source),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LyricSourceChip(
                text = stringResource(R.string.player_lyric_source_auto),
                selected = lyricSourceMode == SettingsManager.LYRIC_SOURCE_AUTO,
                onClick = { onLyricSourceMode(SettingsManager.LYRIC_SOURCE_AUTO) },
                modifier = Modifier.weight(1f)
            )
            LyricSourceChip(
                text = stringResource(R.string.player_lyric_source_external),
                selected = lyricSourceMode == SettingsManager.LYRIC_SOURCE_EXTERNAL,
                onClick = { onLyricSourceMode(SettingsManager.LYRIC_SOURCE_EXTERNAL) },
                modifier = Modifier.weight(1f)
            )
            LyricSourceChip(
                text = stringResource(R.string.player_lyric_source_embedded),
                selected = lyricSourceMode == SettingsManager.LYRIC_SOURCE_EMBEDDED,
                onClick = { onLyricSourceMode(SettingsManager.LYRIC_SOURCE_EMBEDDED) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
internal fun LyricSourceChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(15.dp))
            .background(
                if (selected) MiuixTheme.colorScheme.primary.copy(alpha = 0.16f)
                else MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.72f)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (selected) "✓ $text" else text,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary
        )
    }
}
