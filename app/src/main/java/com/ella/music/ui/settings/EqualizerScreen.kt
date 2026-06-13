package com.ella.music.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.data.SettingsManager
import com.ella.music.player.AudioEffectSettings
import com.ella.music.player.AudioEffectState
import com.ella.music.ui.components.EllaSmallTopAppBar
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.VerticalSlider
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.WindowSpinnerPreference
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

@Composable
fun EqualizerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsManager = remember { SettingsManager.getInstance(context) }
    val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    val pageBackground = if (isDark) Color(0xFF101014) else Color(0xFFF4F4F7)

    val capabilities by AudioEffectState.capabilities.collectAsState()
    val eqEnabled by settingsManager.eqEnabled.collectAsState(initial = false)
    val eqPreset by settingsManager.eqPreset.collectAsState(initial = AudioEffectSettings.PRESET_CUSTOM)
    val bandLevels by settingsManager.eqBandLevelsMb.collectAsState(initial = emptyList())
    val bassBoostEnabled by settingsManager.bassBoostEnabled.collectAsState(initial = false)
    val bassBoostStrength by settingsManager.bassBoostStrength.collectAsState(initial = 0)
    val virtualizerEnabled by settingsManager.virtualizerEnabled.collectAsState(initial = false)
    val virtualizerStrength by settingsManager.virtualizerStrength.collectAsState(initial = 0)

    val accent = MiuixTheme.colorScheme.primary

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(pageBackground)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        EllaSmallTopAppBar(
            title = stringResource(R.string.equalizer_screen_title),
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

            val caps = capabilities
            if (caps == null || !caps.supported) {
                SettingsCardGroup {
                    Text(
                        text = stringResource(R.string.equalizer_unavailable),
                        fontSize = 13.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(18.dp)
                    )
                }
                Spacer(modifier = Modifier.height(160.dp))
                return@Column
            }

            SmallTitle(text = stringResource(R.string.equalizer_section_eq))
            SettingsCardGroup {
                Column {
                    SwitchPreference(
                        title = stringResource(R.string.equalizer_master),
                        summary = stringResource(R.string.equalizer_band_count, caps.displayBandCount),
                        checked = eqEnabled,
                        onCheckedChange = { scope.launch { settingsManager.setEqEnabled(it) } }
                    )

                    val presetItems = buildList {
                        add(DropdownItem(title = stringResource(R.string.equalizer_preset_custom)))
                        caps.presetNames.forEach { add(DropdownItem(title = it)) }
                    }
                    val selectedPresetIndex = if (eqPreset in caps.presetNames.indices) eqPreset + 1 else 0
                    WindowSpinnerPreference(
                        title = stringResource(R.string.equalizer_preset),
                        items = presetItems,
                        selectedIndex = selectedPresetIndex,
                        onSelectedIndexChange = { index ->
                            scope.launch {
                                if (index <= 0) {
                                    settingsManager.setEqPreset(AudioEffectSettings.PRESET_CUSTOM)
                                } else {
                                    val presetIndex = index - 1
                                    val levels = caps.presetBandLevelsMb.getOrNull(presetIndex)
                                        ?: List(caps.displayBandCount) { 0 }
                                    settingsManager.setEqPresetWithBands(presetIndex, levels.toDisplayBandLevels(caps))
                                }
                            }
                        }
                    )
                }
            }

            SettingsCardGroup {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    for (band in 0 until caps.displayBandCount) {
                        val levelMb = bandLevels.getOrElse(band) { 0 }
                        val freqHz = caps.displayCenterFreqsHz.getOrElse(band) { 0 }
                        EqBandColumn(
                            freqLabel = formatFreq(freqHz),
                            gainLabel = formatGainDb(levelMb),
                            levelMb = levelMb,
                            minMb = caps.minLevelMb,
                            maxMb = caps.maxLevelMb,
                            onLevelChange = { newLevel ->
                                val updated = MutableList(caps.displayBandCount) { idx -> bandLevels.getOrElse(idx) { 0 } }
                                updated[band] = newLevel.coerceIn(caps.minLevelMb, caps.maxLevelMb)
                                scope.launch { settingsManager.setEqBandLevelsMb(updated) }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Text(
                text = stringResource(R.string.equalizer_reset),
                color = accent,
                fontSize = 14.sp,
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(end = 8.dp, top = 2.dp, bottom = 6.dp)
                    .pointerInput(Unit) {
                        detectTapGestures {
                            scope.launch { settingsManager.setEqBandLevelsMb(List(caps.displayBandCount) { 0 }) }
                        }
                    }
            )

            if (caps.bassBoostSupported || caps.virtualizerSupported) {
                SmallTitle(text = stringResource(R.string.equalizer_section_effects))
                SettingsCardGroup {
                    Column {
                        if (caps.bassBoostSupported) {
                            EffectStrengthRow(
                                title = stringResource(R.string.equalizer_bass_boost),
                                enabled = bassBoostEnabled,
                                strength = bassBoostStrength,
                                strengthAdjustable = caps.bassBoostStrengthAdjustable,
                                accent = accent,
                                onEnabledChange = { enabled ->
                                    scope.launch {
                                        // Turning the effect on with 0 strength is inaudible; seed a default.
                                        if (enabled && bassBoostStrength <= 0) {
                                            settingsManager.setBassBoostStrength(DEFAULT_EFFECT_STRENGTH)
                                        }
                                        settingsManager.setBassBoostEnabled(enabled)
                                    }
                                },
                                onStrengthChange = { scope.launch { settingsManager.setBassBoostStrength(it) } }
                            )
                        }
                        if (caps.virtualizerSupported) {
                            EffectStrengthRow(
                                title = stringResource(R.string.equalizer_virtualizer),
                                enabled = virtualizerEnabled,
                                strength = virtualizerStrength,
                                strengthAdjustable = caps.virtualizerStrengthAdjustable,
                                accent = accent,
                                onEnabledChange = { enabled ->
                                    scope.launch {
                                        if (enabled && virtualizerStrength <= 0) {
                                            settingsManager.setVirtualizerStrength(DEFAULT_EFFECT_STRENGTH)
                                        }
                                        settingsManager.setVirtualizerEnabled(enabled)
                                    }
                                },
                                onStrengthChange = { scope.launch { settingsManager.setVirtualizerStrength(it) } }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(160.dp))
        }
    }
}

@Composable
private fun EqBandColumn(
    freqLabel: String,
    gainLabel: String,
    levelMb: Int,
    minMb: Int,
    maxMb: Int,
    onLevelChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = freqLabel,
            fontSize = 11.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        VerticalSlider(
            value = levelMb.toFloat().coerceIn(minMb.toFloat(), maxMb.toFloat()),
            onValueChange = { onLevelChange(it.roundToInt()) },
            valueRange = minMb.toFloat()..maxMb.toFloat(),
            width = 18.dp,
            modifier = Modifier.height(180.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = gainLabel,
            fontSize = 11.sp,
            color = MiuixTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun EffectStrengthRow(
    title: String,
    enabled: Boolean,
    strength: Int,
    strengthAdjustable: Boolean,
    accent: Color,
    onEnabledChange: (Boolean) -> Unit,
    onStrengthChange: (Int) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                fontSize = 15.sp,
                color = MiuixTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            // Only meaningful when the device exposes variable strength.
            if (strengthAdjustable) {
                Text(
                    text = "${(strength * 100 / AudioEffectSettings.STRENGTH_MAX)}%",
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
                Spacer(modifier = Modifier.width(12.dp))
            }
            Switch(checked = enabled, onCheckedChange = onEnabledChange)
        }
        if (strengthAdjustable) {
            Spacer(modifier = Modifier.height(8.dp))
            Slider(
                value = strength.toFloat() / AudioEffectSettings.STRENGTH_MAX,
                onValueChange = { onStrengthChange((it.coerceIn(0f, 1f) * AudioEffectSettings.STRENGTH_MAX).roundToInt()) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private const val DEFAULT_EFFECT_STRENGTH = 600

private fun List<Int>.toDisplayBandLevels(caps: com.ella.music.player.EqualizerCapabilities): List<Int> {
    if (size == caps.displayBandCount) return this
    if (isEmpty()) return List(caps.displayBandCount) { 0 }
    return caps.displayCenterFreqsHz.map { displayFreq ->
        val sourceIndex = caps.centerFreqsHz.nearestBandIndex(displayFreq).takeIf { it >= 0 } ?: 0
        getOrElse(sourceIndex) { 0 }
    }
}

private fun List<Int>.nearestBandIndex(freqHz: Int): Int {
    if (isEmpty()) return -1
    var bestIndex = 0
    var bestDistance = Float.MAX_VALUE
    forEachIndexed { index, center ->
        val safeCenter = center.coerceAtLeast(1)
        val safeFreq = freqHz.coerceAtLeast(1)
        val distance = kotlin.math.abs(kotlin.math.ln(safeFreq.toFloat() / safeCenter.toFloat()))
        if (distance < bestDistance) {
            bestDistance = distance
            bestIndex = index
        }
    }
    return bestIndex
}

private fun formatFreq(hz: Int): String =
    if (hz >= 1000) "%.1fk".format(hz / 1000f) else hz.toString()

private fun formatGainDb(levelMb: Int): String {
    val db = levelMb / 100f
    return "%.1f".format(db)
}
