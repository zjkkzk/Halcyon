package com.ella.music.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ella.music.R
import com.ella.music.data.SettingsManager
import com.ella.music.ui.components.EllaSmallTopAppBar
import com.ella.music.viewmodel.PlayerViewModel
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.WindowSpinnerPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
@Composable
fun AudioSettingsScreen(
    onBack: () -> Unit,
    playerViewModel: PlayerViewModel? = null,
    onNavigateToEqualizer: () -> Unit = {},
    highlightKey: String? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsManager = remember { SettingsManager.getInstance(context) }
    val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    val pageBackground = if (isDark) Color(0xFF101014) else Color(0xFFF4F4F7)

    val gaplessPlayback by settingsManager.gaplessPlayback.collectAsState(initial = true)
    val replayGainEnabled by settingsManager.replayGainEnabled.collectAsState(initial = false)
    val audioFocusDisabled by settingsManager.audioFocusDisabled.collectAsState(initial = false)
    val shuffleMode by settingsManager.shuffleMode.collectAsState(initial = SettingsManager.SHUFFLE_MODE_PSEUDO)
    val playNextMode by settingsManager.playNextMode.collectAsState(initial = SettingsManager.PLAY_NEXT_MODE_REVERSE_STACK)
    val previousButtonAction by settingsManager.previousButtonAction.collectAsState(initial = SettingsManager.PREVIOUS_BUTTON_PREVIOUS)
    val decoderMode by settingsManager.decoderMode.collectAsState(initial = 2)
    val startupPlayMode by settingsManager.startupPlayMode.collectAsState(initial = SettingsManager.STARTUP_PLAY_OFF)
    val bluetoothAutoPlay by settingsManager.bluetoothAutoPlay.collectAsState(initial = false)
    val decoderLabels = listOf(
        stringResource(R.string.settings_audio_decoder_system),
        stringResource(R.string.settings_audio_decoder_ffmpeg),
        stringResource(R.string.settings_audio_decoder_auto)
    )
    val selectedDecoderMode = decoderMode.coerceIn(decoderLabels.indices)
    val shuffleModeLabels = listOf(
        stringResource(R.string.settings_shuffle_mode_pseudo_random),
        stringResource(R.string.settings_shuffle_mode_true_random)
    )
    val selectedShuffleMode = shuffleMode.coerceIn(shuffleModeLabels.indices)
    val playNextModeLabels = listOf(
        stringResource(R.string.settings_play_next_mode_reverse_stack),
        stringResource(R.string.settings_play_next_mode_forward_stack)
    )
    val selectedPlayNextMode = playNextMode.coerceIn(playNextModeLabels.indices)
    val previousButtonLabels = listOf(
        stringResource(R.string.settings_previous_button_previous),
        stringResource(R.string.settings_previous_button_replay_current)
    )
    val selectedPreviousButtonAction = previousButtonAction.coerceIn(previousButtonLabels.indices)
    val startupPlayLabels = listOf(
        stringResource(R.string.settings_startup_play_off),
        stringResource(R.string.settings_startup_play_random),
        stringResource(R.string.settings_startup_play_resume)
    )
    val selectedStartupPlayMode = startupPlayMode.coerceIn(startupPlayLabels.indices)
    val startupPlayEntries = listOf(
        DropdownItem(
            title = startupPlayLabels[SettingsManager.STARTUP_PLAY_OFF],
            summary = stringResource(R.string.settings_startup_play_off_summary)
        ),
        DropdownItem(
            title = startupPlayLabels[SettingsManager.STARTUP_PLAY_RANDOM],
            summary = stringResource(R.string.settings_startup_play_random_summary)
        ),
        DropdownItem(
            title = startupPlayLabels[SettingsManager.STARTUP_PLAY_RESUME],
            summary = stringResource(R.string.settings_startup_play_resume_summary)
        )
    )
    val decoderEntries = listOf(
        DropdownItem(
            title = decoderLabels[0],
            summary = stringResource(R.string.settings_audio_decoder_system_summary)
        ),
        DropdownItem(
            title = decoderLabels[1],
            summary = stringResource(R.string.settings_audio_decoder_ffmpeg_summary)
        ),
        DropdownItem(
            title = decoderLabels[2],
            summary = stringResource(R.string.settings_audio_decoder_auto_summary)
        )
    )
    val shuffleModeEntries = listOf(
        DropdownItem(
            title = shuffleModeLabels[0],
            summary = stringResource(R.string.settings_shuffle_mode_pseudo_random_summary)
        ),
        DropdownItem(
            title = shuffleModeLabels[SettingsManager.SHUFFLE_MODE_TRUE_RANDOM],
            summary = stringResource(R.string.settings_shuffle_mode_true_random_summary)
        )
    )
    val previousButtonEntries = listOf(
        DropdownItem(
            title = previousButtonLabels[SettingsManager.PREVIOUS_BUTTON_PREVIOUS],
            summary = stringResource(R.string.settings_previous_button_previous_summary)
        ),
        DropdownItem(
            title = previousButtonLabels[SettingsManager.PREVIOUS_BUTTON_REPLAY_CURRENT],
            summary = stringResource(R.string.settings_previous_button_replay_current_summary)
        )
    )
    val playNextModeEntries = listOf(
        DropdownItem(
            title = playNextModeLabels[SettingsManager.PLAY_NEXT_MODE_REVERSE_STACK],
            summary = stringResource(R.string.settings_play_next_mode_reverse_stack_summary)
        ),
        DropdownItem(
            title = playNextModeLabels[SettingsManager.PLAY_NEXT_MODE_FORWARD_STACK],
            summary = stringResource(R.string.settings_play_next_mode_forward_stack_summary)
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(pageBackground)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        EllaSmallTopAppBar(
            title = stringResource(R.string.settings_audio_screen_title),
            color = pageBackground,
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = MiuixIcons.Regular.Back,
                        contentDescription = stringResource(R.string.common_back),
                        tint = MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            SmallTitle(text = stringResource(R.string.equalizer_section_effects))

            SettingsCardGroup(highlight = highlightKey == "audio_effects") {
                ArrowPreference(
                    title = stringResource(R.string.equalizer_screen_title),
                    summary = stringResource(R.string.settings_audio_equalizer_summary),
                    onClick = onNavigateToEqualizer
                )
            }

            SmallTitle(text = stringResource(R.string.settings_playback_section))

            SettingsCardGroup(highlight = highlightKey == "audio_playback") {
                Column {
                    SwitchPreference(
                        title = stringResource(R.string.settings_gapless_playback),
                        summary = stringResource(R.string.settings_gapless_playback_summary),
                        checked = gaplessPlayback,
                        onCheckedChange = {
                            scope.launch { settingsManager.setGaplessPlayback(it) }
                        }
                    )
                    SwitchPreference(
                        title = stringResource(R.string.settings_replay_gain),
                        summary = stringResource(R.string.settings_replay_gain_summary),
                        checked = replayGainEnabled,
                        onCheckedChange = {
                            scope.launch { settingsManager.setReplayGainEnabled(it) }
                        }
                    )
                    WindowSpinnerPreference(
                        title = stringResource(R.string.settings_startup_play),
                        summary = stringResource(R.string.settings_current_value, startupPlayLabels[selectedStartupPlayMode]),
                        items = startupPlayEntries,
                        selectedIndex = selectedStartupPlayMode,
                        onSelectedIndexChange = { index ->
                            scope.launch { settingsManager.setStartupPlayMode(index) }
                        }
                    )
                    SwitchPreference(
                        title = stringResource(R.string.settings_bluetooth_auto_play),
                        summary = stringResource(R.string.settings_bluetooth_auto_play_summary),
                        checked = bluetoothAutoPlay,
                        onCheckedChange = {
                            scope.launch { settingsManager.setBluetoothAutoPlay(it) }
                        }
                    )
                    WindowSpinnerPreference(
                        title = stringResource(R.string.settings_shuffle_mode),
                        summary = stringResource(R.string.settings_current_value, shuffleModeLabels[selectedShuffleMode]),
                        items = shuffleModeEntries,
                        selectedIndex = selectedShuffleMode,
                        onSelectedIndexChange = { index ->
                            scope.launch { settingsManager.setShuffleMode(index) }
                            playerViewModel?.setShuffleMode(index)
                        }
                    )
                    WindowSpinnerPreference(
                        title = stringResource(R.string.settings_play_next_mode),
                        summary = stringResource(R.string.settings_current_value, playNextModeLabels[selectedPlayNextMode]),
                        items = playNextModeEntries,
                        selectedIndex = selectedPlayNextMode,
                        onSelectedIndexChange = { index ->
                            scope.launch { settingsManager.setPlayNextMode(index) }
                            playerViewModel?.setPlayNextMode(index)
                        }
                    )
                    WindowSpinnerPreference(
                        title = stringResource(R.string.settings_previous_button),
                        summary = stringResource(R.string.settings_current_value, previousButtonLabels[selectedPreviousButtonAction]),
                        items = previousButtonEntries,
                        selectedIndex = selectedPreviousButtonAction,
                        onSelectedIndexChange = { index ->
                            scope.launch { settingsManager.setPreviousButtonAction(index) }
                            playerViewModel?.setPreviousButtonAction(index)
                        }
                    )
                }
            }

            SmallTitle(text = stringResource(R.string.settings_system_section))

            SettingsCardGroup(highlight = highlightKey == "audio_system") {
                Column {
                    SwitchPreference(
                        title = stringResource(R.string.settings_disable_audio_focus),
                        summary = stringResource(R.string.settings_disable_audio_focus_summary),
                        checked = audioFocusDisabled,
                        onCheckedChange = {
                            scope.launch { settingsManager.setAudioFocusDisabled(it) }
                        }
                    )
                    WindowSpinnerPreference(
                        title = stringResource(R.string.settings_decoder),
                        summary = stringResource(R.string.settings_current_value, decoderLabels[selectedDecoderMode]),
                        items = decoderEntries,
                        selectedIndex = selectedDecoderMode,
                        onSelectedIndexChange = { index ->
                            playerViewModel?.setDecoderMode(index)
                                ?: scope.launch { settingsManager.setDecoderMode(index) }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(160.dp))
        }
    }
}
