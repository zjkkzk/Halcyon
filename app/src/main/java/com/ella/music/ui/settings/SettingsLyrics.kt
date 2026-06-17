package com.ella.music.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ella.music.R
import com.ella.music.data.SettingsManager
import com.ella.music.ui.components.EllaMiuixBottomSheet
import com.ella.music.ui.components.EllaMiuixTextField
import com.ella.music.viewmodel.PlayerViewModel
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.WindowSpinnerPreference

@Composable
internal fun SettingsLyricsSection(
    playerViewModel: PlayerViewModel?,
    highlightKey: String? = null,
    onNavigateToLyricPluginSources: () -> Unit = {}
) {
    SmallTitle(text = stringResource(R.string.settings_lyrics))
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsManager = remember { SettingsManager.getInstance(context) }
    val lyricLineBlacklist by settingsManager.lyricLineBlacklist.collectAsState(initial = emptyList())
    val ignoreLyricHeaderTags by settingsManager.ignoreLyricHeaderTags.collectAsState(initial = true)
    var showBlacklistSheet by remember { mutableStateOf(false) }
    var blacklistDraft by remember(lyricLineBlacklist) { mutableStateOf(lyricLineBlacklist.joinToString("\n")) }

    SettingsCardGroup(highlight = highlightKey == "lyric_basic" || highlightKey == "lyric_plugin_sources") {
        Column {
            ArrowPreference(
                title = stringResource(R.string.settings_lyric_plugin_sources),
                summary = stringResource(R.string.settings_lyric_plugin_sources_summary),
                onClick = onNavigateToLyricPluginSources
            )
            SettingsPlayerLyricAlignmentPreference()
            SwitchPreference(
                title = stringResource(R.string.settings_ignore_lyric_header_tags),
                summary = stringResource(R.string.settings_ignore_lyric_header_tags_summary),
                checked = ignoreLyricHeaderTags,
                onCheckedChange = { enabled ->
                    scope.launch { settingsManager.setIgnoreLyricHeaderTags(enabled) }
                }
            )
            ArrowPreference(
                title = stringResource(R.string.settings_lyric_line_blacklist),
                summary = stringResource(R.string.settings_lyric_line_blacklist_summary, lyricLineBlacklist.size),
                onClick = {
                    blacklistDraft = lyricLineBlacklist.joinToString("\n")
                    showBlacklistSheet = true
                }
            )
        }
    }

    SettingsCardGroup(highlight = highlightKey == "mini_lyrics") {
        Column {
            SettingsMiniLyricsControls()
        }
    }

    SettingsCardGroup(highlight = highlightKey == "lyricon") {
        Column {
            SettingsLyriconControls(playerViewModel = playerViewModel)
        }
    }

    SettingsCardGroup(highlight = highlightKey == "desktop_lyric") {
        Column {
            SettingsDesktopLyricControls(playerViewModel = playerViewModel)
        }
    }

    SettingsCardGroup(highlight = highlightKey == "lyric_output" || highlightKey == "coloros_lock_screen_lyric") {
        Column {
            SettingsLyricOutputControls(playerViewModel = playerViewModel)
        }
    }

    EllaMiuixBottomSheet(
        show = showBlacklistSheet,
        title = stringResource(R.string.settings_lyric_line_blacklist),
        onDismissRequest = { showBlacklistSheet = false }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp)
        ) {
            EllaMiuixTextField(
                value = blacklistDraft,
                onValueChange = { blacklistDraft = it },
                label = stringResource(R.string.settings_lyric_line_blacklist_editor_hint),
                singleLine = false,
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = {
                    showBlacklistSheet = false
                    scope.launch {
                        settingsManager.setLyricLineBlacklist(blacklistDraft.lineSequence().toList())
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp)
            ) {
                Text(text = stringResource(R.string.common_save))
            }
        }
    }
}

@Composable
private fun SettingsPlayerLyricAlignmentPreference() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsManager = remember { SettingsManager.getInstance(context) }
    val playerLyricTextAlign by settingsManager.playerLyricTextAlign.collectAsState(initial = SettingsManager.PLAYER_LYRIC_ALIGN_LEFT)
    val labels = listOf(
        stringResource(R.string.settings_status_align_left),
        stringResource(R.string.settings_status_align_center),
        stringResource(R.string.settings_status_align_right)
    )
    val entries = remember(labels) {
        labels.map { DropdownItem(title = it) }
    }

    WindowSpinnerPreference(
        title = stringResource(R.string.settings_player_lyric_text_align),
        summary = stringResource(
            R.string.settings_current_value,
            labels[playerLyricTextAlign.coerceIn(0, 2)]
        ),
        items = entries,
        selectedIndex = playerLyricTextAlign.coerceIn(0, 2),
        onSelectedIndexChange = { index ->
            scope.launch { settingsManager.setPlayerLyricTextAlign(index) }
        }
    )
}
