package com.ella.music.ui.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ella.music.R
import com.ella.music.data.SettingsManager
import com.ella.music.ui.components.EllaSmallTopAppBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun LyricFontScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsManager = remember { SettingsManager.getInstance(context) }
    val selectedFontPath by settingsManager.lyricFontPath.collectAsState(initial = "")
    val lyricFontWeight by settingsManager.lyricFontWeight.collectAsState(initial = 800)
    val lyricFontItalic by settingsManager.lyricFontItalic.collectAsState(initial = false)
    var fonts by remember { mutableStateOf<List<FontChoice>>(emptyList()) }
    val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    val pageBackground = if (isDark) Color(0xFF101014) else Color(0xFFF4F4F7)

    LaunchedEffect(Unit) {
        fonts = withContext(Dispatchers.IO) { collectFontChoices(context) }
    }
    LaunchedEffect(lyricFontItalic) {
        if (lyricFontItalic) settingsManager.setLyricFontItalic(false)
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) { copyImportedFont(context, uri) }
            }.onSuccess { font ->
                settingsManager.setLyricFont(font.name, font.path)
                fonts = withContext(Dispatchers.IO) { collectFontChoices(context) }
                Toast.makeText(
                    context,
                    context.getString(R.string.settings_lyric_font_applied, font.name),
                    Toast.LENGTH_SHORT
                ).show()
            }.onFailure {
                Toast.makeText(context, context.getString(R.string.settings_lyric_font_import_failed), Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(pageBackground)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        EllaSmallTopAppBar(
            title = stringResource(R.string.settings_lyric_font),
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = MiuixIcons.Regular.Back,
                        contentDescription = stringResource(R.string.common_back),
                        tint = MiuixTheme.colorScheme.onSurface
                    )
                }
            },
            actions = {
                IconButton(onClick = { importLauncher.launch(SUPPORTED_FONT_MIME_TYPES) }) {
                    Icon(
                        imageVector = MiuixIcons.Regular.Download,
                        contentDescription = stringResource(R.string.settings_lyric_font_import),
                        tint = MiuixTheme.colorScheme.onSurface
                    )
                }
            },
            color = pageBackground
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                SystemDefaultFontCard(
                    selected = selectedFontPath.isBlank(),
                    onClick = {
                        scope.launch {
                            settingsManager.clearLyricFont()
                            Toast.makeText(
                                context,
                                context.getString(R.string.settings_lyric_font_system_default_applied),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                )
                LyricFontWeightCard(
                    selectedFontPath = selectedFontPath,
                    lyricFontWeight = lyricFontWeight,
                    onWeightChange = { weight ->
                        scope.launch { settingsManager.setLyricFontWeight(weight) }
                    }
                )
                LyricFontListTitle()
            }

            items(fonts, key = { it.path }) { font ->
                FontChoiceItem(
                    font = font,
                    currentWeight = lyricFontWeight,
                    italic = false,
                    selected = selectedFontPath == font.path,
                    onClick = {
                        scope.launch {
                            settingsManager.setLyricFont(font.name, font.path)
                            Toast.makeText(
                                context,
                                context.getString(R.string.settings_lyric_font_applied, font.name),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    onDelete = if (font.sourceRank == FONT_SOURCE_IMPORTED) {
                        {
                            scope.launch {
                                val deleted = withContext(Dispatchers.IO) {
                                    deleteImportedFont(font)
                                }

                                if (selectedFontPath == font.path) {
                                    settingsManager.clearLyricFont()
                                }

                                fonts = withContext(Dispatchers.IO) { collectFontChoices(context) }

                                Toast.makeText(
                                    context,
                                    if (deleted) {
                                        context.getString(R.string.settings_lyric_font_deleted)
                                    } else {
                                        context.getString(R.string.settings_lyric_font_delete_failed)
                                    },
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    } else {
                        null
                    }
                )
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
