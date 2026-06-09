package com.ella.music.ui.about

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.ella.music.BuildConfig
import com.ella.music.R
import com.ella.music.ui.components.EllaSmallTopAppBar
import com.ella.music.ui.components.ellaPageBackground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun UpdateScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    val pageBackground = ellaPageBackground()
    val cardColor = if (isDark) Color(0xFF1D1D21) else Color.White
    var state by remember { mutableStateOf<UpdateUiState>(UpdateUiState.Loading) }

    fun checkUpdate() {
        state = UpdateUiState.Loading
        scope.launch {
            state = withContext(Dispatchers.IO) {
                runCatching { fetchLatestRelease() }
                    .fold(
                        onSuccess = { release ->
                            val hasUpdate = compareVersionNames(release.versionName, BuildConfig.VERSION_NAME) > 0
                            UpdateUiState.Ready(release = release, hasUpdate = hasUpdate)
                        },
                        onFailure = { error ->
                            UpdateUiState.Error(error.localizedMessage ?: context.getString(R.string.update_check_failed))
                        }
                    )
            }
        }
    }

    LaunchedEffect(Unit) {
        delay(250)
        checkUpdate()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(pageBackground)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        EllaSmallTopAppBar(
            title = stringResource(R.string.about_update),
            color = pageBackground,
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
                IconButton(onClick = ::checkUpdate) {
                    Icon(
                        imageVector = MiuixIcons.Regular.Refresh,
                        contentDescription = stringResource(R.string.update_check_action),
                        tint = MiuixTheme.colorScheme.onSurface
                    )
                }
            }
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                val updateButtonTarget = state.updateButtonTargetUrl()
                UpdatePressureHero(
                    state = state,
                    isDark = isDark,
                    buttonText = state.updateButtonText(),
                    buttonEnabled = updateButtonTarget != null,
                    onButtonClick = {
                        updateButtonTarget?.let(context::openUrl)
                    }
                )
            }

            item {
                Card(
                    colors = CardDefaults.defaultColors(color = cardColor)
                ) {
                    BasicComponent(
                        title = stringResource(R.string.update_current_version),
                        summary = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
                    )
                    when (val current = state) {
                        UpdateUiState.Loading -> {
                            BasicComponent(
                                title = stringResource(R.string.update_checking),
                                summary = stringResource(R.string.update_fetching_latest)
                            )
                        }
                        is UpdateUiState.Error -> {
                            BasicComponent(
                                title = stringResource(R.string.update_check_failed_title),
                                summary = current.message
                            )
                        }
                        is UpdateUiState.Ready -> {
                            BasicComponent(
                                title = if (current.hasUpdate) stringResource(R.string.update_new_version_found) else stringResource(R.string.update_already_latest),
                                summary = stringResource(R.string.update_latest_version, current.release.tagName)
                            )
                        }
                    }
                }
            }

            val release = (state as? UpdateUiState.Ready)?.release
            if (release != null) {
                item {
                    Card(
                        colors = CardDefaults.defaultColors(color = cardColor)
                    ) {
                        BasicComponent(
                            title = release.title,
                            summary = release.publishedAt.takeIf { it.isNotBlank() } ?: release.tagName
                        )
                        ReleaseMarkdown(
                            markdown = release.body.ifBlank { stringResource(R.string.update_empty_changelog) },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                        )
                    }
                }
            }

            item {
                Spacer(
                    modifier = Modifier
                        .height(120.dp)
                        .navigationBarsPadding()
                )
            }
        }
    }
}
