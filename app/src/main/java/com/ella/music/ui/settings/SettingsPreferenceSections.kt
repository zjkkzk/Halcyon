package com.ella.music.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.ella.music.R
import com.ella.music.data.SettingsManager
import com.ella.music.ui.components.TagEditorOptionIds
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.WindowSpinnerPreference

@Composable
internal fun SettingsHomeCustomizeSection(
    onOpenHomeDisplay: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsManager = remember { SettingsManager.getInstance(context) }
    val homeDailyMixVisible by settingsManager.homeDailyMixVisible.collectAsState(initial = true)
    val homeAiMixVisible by settingsManager.homeAiMixVisible.collectAsState(initial = true)

    SmallTitle(text = stringResource(R.string.settings_home_customize))

    SettingsCardGroup {
        Column {
            SwitchPreference(
                title = stringResource(R.string.settings_daily_mix),
                summary = stringResource(R.string.settings_daily_mix_summary),
                checked = homeDailyMixVisible,
                onCheckedChange = {
                    scope.launch { settingsManager.setHomeDailyMixVisible(it) }
                }
            )
            SwitchPreference(
                title = stringResource(R.string.settings_ai_mix),
                summary = stringResource(R.string.settings_ai_mix_summary),
                checked = homeAiMixVisible,
                onCheckedChange = {
                    scope.launch { settingsManager.setHomeAiMixVisible(it) }
                }
            )
            ArrowPreference(
                title = stringResource(R.string.settings_home_display_items),
                summary = stringResource(R.string.settings_home_display_items_summary),
                onClick = onOpenHomeDisplay
            )
        }
    }
}

@Composable
internal fun SettingsLibrarySourceSection(
    onOpenScanFolders: (() -> Unit)?
) {
    SmallTitle(text = stringResource(R.string.settings_library_source))

    SettingsCardGroup {
        Column {
            ArrowPreference(
                title = stringResource(R.string.settings_scan_folders),
                summary = stringResource(R.string.settings_scan_folders_summary),
                onClick = { onOpenScanFolders?.invoke() }
            )
        }
    }
}

@Composable
internal fun SettingsAiInterpretationSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsManager = remember { SettingsManager.getInstance(context) }
    val openAiApiKey by settingsManager.openAiApiKey.collectAsState(initial = "")
    val openAiBaseUrl by settingsManager.openAiBaseUrl.collectAsState(initial = SettingsManager.DEFAULT_OPENAI_BASE_URL)
    val openAiModel by settingsManager.openAiModel.collectAsState(initial = SettingsManager.DEFAULT_OPENAI_MODEL)

    SmallTitle(text = stringResource(R.string.settings_ai_interpretation))

    SettingsCardGroup {
        Column {
            SplitSettingTextField(
                label = "OpenAI API Key",
                value = openAiApiKey,
                summary = stringResource(R.string.settings_openai_api_key_summary),
                onValueChange = { value -> scope.launch { settingsManager.setOpenAiApiKey(value) } }
            )
            SplitSettingTextField(
                label = "OpenAI Base URL",
                value = openAiBaseUrl,
                summary = stringResource(R.string.settings_openai_base_url_summary),
                onValueChange = { value -> scope.launch { settingsManager.setOpenAiBaseUrl(value) } }
            )
            SplitSettingTextField(
                label = stringResource(R.string.settings_openai_model),
                value = openAiModel,
                summary = stringResource(R.string.settings_openai_model_summary, SettingsManager.DEFAULT_OPENAI_MODEL),
                onValueChange = { value -> scope.launch { settingsManager.setOpenAiModel(value) } }
            )
        }
    }
}

@Composable
internal fun SettingsMcpSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsManager = remember { SettingsManager.getInstance(context) }
    val mcpServerEnabled by settingsManager.mcpServerEnabled.collectAsState(initial = false)

    SmallTitle(text = stringResource(R.string.settings_mcp_server))

    SettingsCardGroup {
        Column {
            SwitchPreference(
                title = stringResource(R.string.settings_mcp_server),
                summary = stringResource(R.string.settings_mcp_server_summary),
                checked = mcpServerEnabled,
                onCheckedChange = { enabled ->
                    scope.launch {
                        settingsManager.setMcpServerEnabled(enabled)
                        if (enabled) {
                            com.ella.music.mcp.McpServerService.start(context)
                        } else {
                            com.ella.music.mcp.McpServerService.stop(context)
                        }
                    }
                }
            )
        }
    }
}

@Composable
internal fun SettingsLyricShareSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsManager = remember { SettingsManager.getInstance(context) }
    val lyricShareCustomInfo by settingsManager.lyricShareCustomInfo.collectAsState(initial = "")

    SmallTitle(text = stringResource(R.string.settings_lyric_share_card))

    SettingsCardGroup {
        Column {
            SplitSettingTextField(
                label = stringResource(R.string.settings_lyric_share_custom_info),
                value = lyricShareCustomInfo,
                summary = stringResource(R.string.settings_lyric_share_custom_info_summary),
                onValueChange = { value -> scope.launch { settingsManager.setLyricShareCustomInfo(value) } }
            )
        }
    }
}

@Composable
internal fun SettingsTagScrapingSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsManager = remember { SettingsManager.getInstance(context) }
    val metadataEditorId by settingsManager.metadataEditorId.collectAsState(initial = TagEditorOptionIds.ASK_EACH_TIME)
    val lyricTimingEditorId by settingsManager.lyricTimingEditorId.collectAsState(initial = TagEditorOptionIds.ASK_EACH_TIME)

    val editorAskEveryTime = stringResource(R.string.settings_editor_ask_every_time)
    val editorBuiltinCustomTag = stringResource(R.string.settings_editor_builtin_custom_tag)
    val editorLunaBeatMetadata = stringResource(R.string.settings_editor_lunabeat_metadata)
    val editorMusicTag = stringResource(R.string.settings_editor_music_tag)
    val editorLunaBeatLyricTiming = stringResource(R.string.settings_editor_lunabeat_lyric_timing)
    val metadataEditorOptions = listOf(
        TagEditorOptionIds.ASK_EACH_TIME to editorAskEveryTime,
        TagEditorOptionIds.BUILTIN_CUSTOM_TAG to editorBuiltinCustomTag,
        TagEditorOptionIds.LYRICO to "Lyrico",
        TagEditorOptionIds.LUNABEAT_METADATA to editorLunaBeatMetadata,
        TagEditorOptionIds.MUSIC_TAG to editorMusicTag
    )
    val lyricTimingEditorOptions = listOf(
        TagEditorOptionIds.ASK_EACH_TIME to editorAskEveryTime,
        TagEditorOptionIds.LUNABEAT_LYRIC_TIMING to editorLunaBeatLyricTiming
    )
    val metadataEditorIndex = metadataEditorOptions
        .indexOfFirst { it.first == metadataEditorId }
        .takeIf { it >= 0 }
        ?: 0
    val lyricTimingEditorIndex = lyricTimingEditorOptions
        .indexOfFirst { it.first == lyricTimingEditorId }
        .takeIf { it >= 0 }
        ?: 0
    val metadataEditorEntries = remember(metadataEditorOptions) {
        metadataEditorOptions.map { DropdownItem(title = it.second) }
    }
    val lyricTimingEditorEntries = remember(lyricTimingEditorOptions) {
        lyricTimingEditorOptions.map { DropdownItem(title = it.second) }
    }

    SmallTitle(text = stringResource(R.string.settings_tag_scraping))

    SettingsCardGroup {
        Column {
            WindowSpinnerPreference(
                title = stringResource(R.string.settings_metadata_editor),
                summary = stringResource(R.string.settings_current_value, metadataEditorOptions.getOrNull(metadataEditorIndex)?.second.orEmpty()),
                items = metadataEditorEntries,
                selectedIndex = metadataEditorIndex,
                onSelectedIndexChange = { index ->
                    scope.launch {
                        settingsManager.setMetadataEditorId(
                            metadataEditorOptions.getOrNull(index)?.first.orEmpty()
                        )
                    }
                }
            )
            WindowSpinnerPreference(
                title = stringResource(R.string.settings_lyric_timing_editor),
                summary = stringResource(R.string.settings_current_value, lyricTimingEditorOptions.getOrNull(lyricTimingEditorIndex)?.second.orEmpty()),
                items = lyricTimingEditorEntries,
                selectedIndex = lyricTimingEditorIndex,
                onSelectedIndexChange = { index ->
                    scope.launch {
                        settingsManager.setLyricTimingEditorId(
                            lyricTimingEditorOptions.getOrNull(index)?.first.orEmpty()
                        )
                    }
                }
            )
        }
    }
}

@Composable
internal fun SettingsDesktopShortcutSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsManager = remember { SettingsManager.getInstance(context) }
    val shortcutLibraryLabel by settingsManager.shortcutLibraryLabel.collectAsState(initial = SettingsManager.DEFAULT_SHORTCUT_LIBRARY_LABEL)
    val shortcutPlaylistsLabel by settingsManager.shortcutPlaylistsLabel.collectAsState(initial = SettingsManager.DEFAULT_SHORTCUT_PLAYLISTS_LABEL)
    val shortcutFolderLabel by settingsManager.shortcutFolderLabel.collectAsState(initial = SettingsManager.DEFAULT_SHORTCUT_FOLDER_LABEL)

    SmallTitle(text = stringResource(R.string.settings_desktop_shortcuts))

    SettingsCardGroup {
        Column {
            SplitSettingTextField(
                label = stringResource(R.string.settings_shortcut_library),
                value = shortcutLibraryLabel,
                summary = stringResource(R.string.settings_shortcut_summary),
                singleLine = true,
                onValueChange = { value -> scope.launch { settingsManager.setShortcutLibraryLabel(value) } }
            )
            SplitSettingTextField(
                label = stringResource(R.string.settings_shortcut_playlists),
                value = shortcutPlaylistsLabel,
                summary = stringResource(R.string.settings_shortcut_summary),
                singleLine = true,
                onValueChange = { value -> scope.launch { settingsManager.setShortcutPlaylistsLabel(value) } }
            )
            SplitSettingTextField(
                label = stringResource(R.string.settings_shortcut_folder),
                value = shortcutFolderLabel,
                summary = stringResource(R.string.settings_shortcut_summary),
                singleLine = true,
                onValueChange = { value -> scope.launch { settingsManager.setShortcutFolderLabel(value) } }
            )
        }
    }
}

@Composable
internal fun SettingsScanSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsManager = remember { SettingsManager.getInstance(context) }
    val autoScan by settingsManager.autoScan.collectAsState(initial = false)
    val autoScanLocalPlaylists by settingsManager.autoScanLocalPlaylists.collectAsState(initial = false)
    val minDurationSec by settingsManager.minDurationSec.collectAsState(initial = 15)
    val artistSeparators by settingsManager.artistSeparators.collectAsState(initial = "")
    val artistProtectedNames by settingsManager.artistProtectedNames.collectAsState(initial = "")
    val genreSeparators by settingsManager.genreSeparators.collectAsState(initial = "")
    val genreProtectedNames by settingsManager.genreProtectedNames.collectAsState(initial = "")
    val tagIgnoreCase by settingsManager.tagIgnoreCase.collectAsState(initial = false)

    SmallTitle(text = stringResource(R.string.settings_scan))

    SettingsCardGroup {
        Column {
            SwitchPreference(
                title = stringResource(R.string.settings_auto_scan),
                summary = stringResource(R.string.settings_auto_scan_summary),
                checked = autoScan,
                onCheckedChange = {
                    scope.launch { settingsManager.setAutoScan(it) }
                }
            )
            SwitchPreference(
                title = stringResource(R.string.settings_auto_scan_local_playlists),
                summary = stringResource(R.string.settings_auto_scan_local_playlists_summary),
                checked = autoScanLocalPlaylists,
                onCheckedChange = {
                    scope.launch {
                        settingsManager.setAutoScanLocalPlaylists(it)
                        settingsManager.setLocalPlaylistScanPromptHandled(true)
                    }
                }
            )

            SettingsIntSliderPreference(
                title = stringResource(R.string.settings_min_duration_filter),
                summary = stringResource(R.string.settings_min_duration_filter_summary, minDurationSec),
                value = minDurationSec,
                valueRange = 0..60,
                valueText = stringResource(R.string.settings_seconds_value, minDurationSec.coerceIn(0, 60)),
                onValueChange = { sec -> scope.launch { settingsManager.setMinDurationSec(sec) } }
            )

            SplitSettingTextField(
                label = stringResource(R.string.settings_artist_separators),
                value = artistSeparators,
                summary = stringResource(R.string.settings_artist_separators_summary),
                onValueChange = { value -> scope.launch { settingsManager.setArtistSeparators(value) } }
            )
            SplitSettingTextField(
                label = stringResource(R.string.settings_artist_protected_names),
                value = artistProtectedNames,
                summary = stringResource(R.string.settings_artist_protected_names_summary),
                onValueChange = { value -> scope.launch { settingsManager.setArtistProtectedNames(value) } }
            )
            SplitSettingTextField(
                label = stringResource(R.string.settings_genre_separators),
                value = genreSeparators,
                summary = stringResource(R.string.settings_genre_separators_summary),
                onValueChange = { value -> scope.launch { settingsManager.setGenreSeparators(value) } }
            )
            SplitSettingTextField(
                label = stringResource(R.string.settings_genre_protected_names),
                value = genreProtectedNames,
                summary = stringResource(R.string.settings_genre_protected_names_summary),
                onValueChange = { value -> scope.launch { settingsManager.setGenreProtectedNames(value) } }
            )
            SwitchPreference(
                title = stringResource(R.string.settings_tag_ignore_case),
                summary = stringResource(R.string.settings_tag_ignore_case_summary),
                checked = tagIgnoreCase,
                onCheckedChange = {
                    scope.launch { settingsManager.setTagIgnoreCase(it) }
                }
            )
        }
    }
}
