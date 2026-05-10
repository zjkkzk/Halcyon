package com.ella.music.ui.settings

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.BuildConfig
import com.ella.music.data.SettingsManager
import com.ella.music.data.repository.MusicRepository
import com.ella.music.ui.theme.THEME_DARK
import com.ella.music.ui.theme.THEME_FOLLOW_SYSTEM
import com.ella.music.ui.theme.THEME_LIGHT
import com.ella.music.viewmodel.PlayerViewModel
import kotlinx.coroutines.launch
import android.widget.Toast
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.SpinnerEntry
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.WindowSpinnerPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun SettingsScreen(
    onNavigateToAbout: () -> Unit,
    onNavigateToAnalytics: () -> Unit,
    onNavigateToLxOnline: () -> Unit,
    onNavigateToLyricFont: () -> Unit,
    playerViewModel: PlayerViewModel? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsManager = remember { SettingsManager(context) }
    val cacheRepository = remember { MusicRepository(context) }

    val autoScan by settingsManager.autoScan.collectAsState(initial = true)
    val gaplessPlayback by settingsManager.gaplessPlayback.collectAsState(initial = true)
    val lyriconEnabled by settingsManager.lyriconEnabled.collectAsState(initial = true)
    val lyriconTranslation by settingsManager.lyriconTranslation.collectAsState(initial = true)
    val themeMode by settingsManager.themeMode.collectAsState(initial = 0)
    val tickerEnabled by settingsManager.tickerEnabled.collectAsState(initial = true)
    val bluetoothLyricEnabled by settingsManager.bluetoothLyricEnabled.collectAsState(initial = false)
    val minDurationSec by settingsManager.minDurationSec.collectAsState(initial = 15)
    val replayGainEnabled by settingsManager.replayGainEnabled.collectAsState(initial = false)
    val lyricFontName by settingsManager.lyricFontName.collectAsState(initial = "")
    val scanIncludeFolders by settingsManager.scanIncludeFolders.collectAsState(initial = "")
    val decoderMode by settingsManager.decoderMode.collectAsState(initial = 2)
    val themeLabels = listOf("跟随系统", "浅色", "深色")
    val selectedThemeMode = themeMode.coerceIn(themeLabels.indices)
    val decoderLabels = listOf("系统解码", "FFmpeg 解码", "自动")
    val selectedDecoderMode = decoderMode.coerceIn(decoderLabels.indices)
    var scanIncludeExpanded by remember { mutableStateOf(false) }
    var scanIncludeDraft by remember(scanIncludeFolders) { mutableStateOf(scanIncludeFolders) }
    val themeEntries = remember { themeLabels.map { SpinnerEntry(title = it) } }
    val decoderEntries = remember {
        decoderLabels.mapIndexed { index, label ->
            SpinnerEntry(
                title = label,
                summary = when (index) {
                    0 -> "只使用 Android 系统解码；不支持的格式会播放失败"
                    1 -> "优先使用 FFmpeg 扩展解码"
                    else -> "系统不支持时才回落到 FFmpeg"
                }
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        SmallTopAppBar(
            title = "设置",
            color = MiuixTheme.colorScheme.background
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            SmallTitle(text = "外观")

            Card(modifier = Modifier.padding(bottom = 12.dp)) {
                Column {
                    WindowSpinnerPreference(
                        title = "主题模式",
                        summary = "选择应用明暗外观",
                        items = themeEntries,
                        selectedIndex = selectedThemeMode,
                        onSelectedIndexChange = { index ->
                            scope.launch { settingsManager.setThemeMode(index) }
                        }
                    )
                    ArrowPreference(
                        title = "歌词字体",
                        summary = lyricFontName.ifBlank { "系统默认" },
                        onClick = onNavigateToLyricFont
                    )
                }
            }

            SmallTitle(text = "通用")

            Card(modifier = Modifier.padding(bottom = 12.dp)) {
                Column {
                SwitchPreference(
                    title = "自动扫描",
                    summary = "启动时自动扫描音乐文件",
                    checked = autoScan,
                    onCheckedChange = {
                        scope.launch { settingsManager.setAutoScan(it) }
                    }
                )

                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(text = "最短时长过滤", fontSize = 15.sp)
                    Text(
                        text = "过滤短于 ${minDurationSec} 秒的音频文件",
                        fontSize = 13.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Slider(
                        value = minDurationSec.toFloat() / 60f,
                        onValueChange = { fraction ->
                            val sec = (fraction * 60f).toInt().coerceIn(0, 60)
                            scope.launch { settingsManager.setMinDurationSec(sec) }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(text = "0秒", fontSize = 11.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                        Spacer(modifier = Modifier.weight(1f))
                        Text(text = "60秒", fontSize = 11.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    }
                }

                    ArrowPreference(
                        title = "扫描文件夹",
                        summary = scanIncludeFolders.ifBlank { "在文件夹页右上角添加本地文件夹" },
                        onClick = { scanIncludeExpanded = !scanIncludeExpanded }
                    )
                    if (scanIncludeExpanded) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                            InputField(
                                query = scanIncludeDraft,
                                onQueryChange = { scanIncludeDraft = it },
                                onSearch = {},
                                expanded = true,
                                onExpandedChange = {},
                                label = "/storage/emulated/0/Music；/sdcard/Download"
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Button(onClick = {
                                    scope.launch { settingsManager.setScanIncludeFolders(scanIncludeDraft) }
                                }) {
                                    Text("保存")
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(onClick = {
                                    scanIncludeDraft = ""
                                    scope.launch { settingsManager.setScanIncludeFolders("") }
                                }) {
                                    Text("清空")
                                }
                            }
                        }
                    }
                }
            }

            SmallTitle(text = "音频")

            Card(modifier = Modifier.padding(bottom = 12.dp)) {
                Column {
                SwitchPreference(
                    title = "无缝播放",
                    summary = "歌曲之间无间隙切换",
                    checked = gaplessPlayback,
                    onCheckedChange = {
                        scope.launch { settingsManager.setGaplessPlayback(it) }
                    }
                )

                SwitchPreference(
                    title = "ReplayGain 音量均衡",
                    summary = "根据音频标签自动调整播放音量",
                    checked = replayGainEnabled,
                    onCheckedChange = {
                        scope.launch { settingsManager.setReplayGainEnabled(it) }
                    }
                )

                    WindowSpinnerPreference(
                        title = "解码器",
                        summary = "当前：${decoderLabels[selectedDecoderMode]}",
                        items = decoderEntries,
                        selectedIndex = selectedDecoderMode,
                        onSelectedIndexChange = { index ->
                            scope.launch { settingsManager.setDecoderMode(index) }
                        }
                    )
                }
            }

            SmallTitle(text = "歌词")

            Card(modifier = Modifier.padding(bottom = 12.dp)) {
                Column {
                SwitchPreference(
                    title = "启用词幕",
                    summary = "将歌词推送到词幕（Lyricon）",
                    checked = lyriconEnabled,
                    onCheckedChange = { enabled ->
                        scope.launch { settingsManager.setLyriconEnabled(enabled) }
                        playerViewModel?.setLyriconEnabled(enabled)
                    }
                )

                SwitchPreference(
                    title = "传递翻译",
                    summary = "在词幕中显示歌词翻译",
                    enabled = lyriconEnabled,
                    checked = lyriconTranslation,
                    onCheckedChange = { enabled ->
                        scope.launch { settingsManager.setLyriconTranslation(enabled) }
                        playerViewModel?.setLyriconTranslation(enabled)
                    }
                )

                SwitchPreference(
                    title = "启用 FLYme 状态栏歌词",
                    summary = "在魅族设备上通过 Ticker 通知显示歌词",
                    checked = tickerEnabled,
                    onCheckedChange = { enabled ->
                        scope.launch { settingsManager.setTickerEnabled(enabled) }
                        playerViewModel?.setTickerEnabled(enabled)
                    }
                )

                SwitchPreference(
                    title = "启用蓝牙车载歌词",
                    summary = "将当前歌词写入媒体标题，供蓝牙设备或车机显示。",
                    checked = bluetoothLyricEnabled,
                    onCheckedChange = { enabled ->
                        scope.launch { settingsManager.setBluetoothLyricEnabled(enabled) }
                        playerViewModel?.setBluetoothLyricEnabled(enabled)
                    }
                )
                }
            }

            SmallTitle(text = "其他")

            Card(modifier = Modifier.padding(bottom = 12.dp)) {
                Column {
                    ArrowPreference(
                        title = "歌曲库分析",
                        summary = "查看音质占比、听歌时长和播放次数排行",
                        onClick = onNavigateToAnalytics
                    )
                    ArrowPreference(
                        title = "LX Music 在线音乐",
                        summary = "导入 LX Music API 源并搜索在线播放",
                        onClick = onNavigateToLxOnline
                    )
                    ArrowPreference(
                        title = "清除封面歌词缓存",
                        summary = "清除 WebDAV 首次播放时缓存的封面和内嵌歌词文件",
                        onClick = {
                            scope.launch {
                                cacheRepository.clearRemoteMetadataCache()
                                Toast.makeText(context, "封面歌词缓存已清除", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                    ArrowPreference(
                        title = "关于",
                        summary = "Ella Music v${BuildConfig.VERSION_NAME}",
                        startAction = {
                            Icon(
                                imageVector = MiuixIcons.Regular.Info,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.size(24.dp)
                            )
                        },
                        onClick = onNavigateToAbout
                    )
                }
            }

            Spacer(modifier = Modifier.height(160.dp))
        }
    }
}
