package com.ella.music.ui.online

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.data.LxSourceConfig
import com.ella.music.data.SettingsManager
import com.ella.music.data.lx.LxOnlineService
import com.ella.music.data.remote.EmbyService
import com.ella.music.data.remote.NavidromeService
import com.ella.music.data.remote.RemoteMusicProvider
import com.ella.music.data.remote.RemoteMusicSourceConfig
import com.ella.music.ui.components.EllaMiuixTextField
import com.ella.music.ui.components.ellaPageBackground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTitle
import com.ella.music.ui.components.EllaSmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Import
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun LxSourceSettingsScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsManager = remember { SettingsManager.getInstance(context) }
    val service = remember(context) { LxOnlineService(context) }
    val navidromeService = remember(context) { NavidromeService(context) }
    val embyService = remember(context) { EmbyService(context) }
    val sources by settingsManager.lxSources.collectAsState(initial = emptyList())
    val selectedId by settingsManager.selectedLxSourceId.collectAsState(initial = "")
    val selectedProvider by settingsManager.selectedOnlineProvider.collectAsState(initial = RemoteMusicProvider.Lx)
    val savedNavidrome by settingsManager.navidromeConfig.collectAsState(
        initial = RemoteMusicSourceConfig(RemoteMusicProvider.Navidrome, "")
    )
    val savedEmby by settingsManager.embyConfig.collectAsState(
        initial = RemoteMusicSourceConfig(RemoteMusicProvider.Emby, "")
    )
    var importUrl by remember { mutableStateOf("") }
    var navidromeUrl by remember(savedNavidrome.baseUrl) { mutableStateOf(savedNavidrome.baseUrl) }
    var navidromeUser by remember(savedNavidrome.username) { mutableStateOf(savedNavidrome.username) }
    var navidromePassword by remember(savedNavidrome.password) { mutableStateOf(savedNavidrome.password) }
    var embyUrl by remember(savedEmby.baseUrl) { mutableStateOf(savedEmby.baseUrl) }
    var embyUser by remember(savedEmby.username) { mutableStateOf(savedEmby.username) }
    var embyPassword by remember { mutableStateOf("") }
    var isBusy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf(context.getString(R.string.lx_source_import_hint)) }

    fun showToast(text: String) {
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    }

    val localSourceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            isBusy = true
            runCatching {
                val script = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        input.bufferedReader(Charsets.UTF_8).readText()
                    }.orEmpty()
                }
                val (name, normalizedScript) = service.importSourceScript(script, allowRuntimeInspect = false)
                settingsManager.setLxSource(uri.toString(), name, normalizedScript)
                message = context.getString(R.string.lx_source_imported_named, name)
            }.onFailure {
                message = it.localizedMessage ?: context.getString(R.string.lx_source_import_local_failed)
                showToast(message)
            }
            isBusy = false
        }
    }

    SourceSettingsScaffold(
        title = stringResource(R.string.lx_source_title),
        onBack = onBack,
        message = if (isBusy) stringResource(R.string.lx_source_processing) else message,
        importUrl = importUrl,
        onImportUrlChange = { importUrl = it },
        importPlaceholder = "https://.../source.js",
        isBusy = isBusy,
        onLocalImport = {
            localSourceLauncher.launch(
                arrayOf(
                    "text/javascript",
                    "application/javascript",
                    "application/x-javascript",
                    "text/*",
                    "application/octet-stream"
                )
            )
        },
        onUrlImport = {
            if (importUrl.isBlank()) {
                showToast(context.getString(R.string.lx_source_url_empty_hint))
                return@SourceSettingsScaffold
            }
            scope.launch {
                isBusy = true
                runCatching {
                    val (name, script) = service.importSource(importUrl)
                    settingsManager.setLxSource(importUrl, name, script)
                    importUrl = ""
                    message = context.getString(R.string.lx_source_imported_named, name)
                }.onFailure {
                    message = it.localizedMessage ?: context.getString(R.string.lx_source_import_failed)
                    showToast(message)
                }
                isBusy = false
            }
        }
    ) {
        SmallTitle(text = stringResource(R.string.remote_source_section))
        RemoteProviderSelector(
            selectedProvider = selectedProvider,
            navidromeConfigured = savedNavidrome.isConfigured,
            embyConfigured = savedEmby.isConfigured,
            onSelect = { provider ->
                scope.launch {
                    settingsManager.selectOnlineProvider(provider)
                    message = context.getString(R.string.remote_source_selected, provider.displayName(context))
                }
            }
        )

        RemoteServerConfigCard(
            title = stringResource(R.string.remote_source_navidrome),
            summary = stringResource(R.string.remote_source_navidrome_summary),
            url = navidromeUrl,
            username = navidromeUser,
            password = navidromePassword,
            passwordLabel = stringResource(R.string.remote_source_password_or_token),
            enabled = !isBusy,
            onUrlChange = { navidromeUrl = it },
            onUsernameChange = { navidromeUser = it },
            onPasswordChange = { navidromePassword = it },
            onSave = {
                scope.launch {
                    isBusy = true
                    runCatching {
                        val config = RemoteMusicSourceConfig(
                            provider = RemoteMusicProvider.Navidrome,
                            baseUrl = navidromeUrl,
                            username = navidromeUser,
                            password = navidromePassword
                        )
                        navidromeService.test(config)
                        settingsManager.setNavidromeConfig(navidromeUrl, navidromeUser, navidromePassword)
                        settingsManager.selectOnlineProvider(RemoteMusicProvider.Navidrome)
                        message = context.getString(R.string.remote_source_saved_named, context.getString(R.string.remote_source_navidrome))
                    }.onFailure {
                        message = it.localizedMessage ?: context.getString(R.string.remote_source_save_failed)
                        showToast(message)
                    }
                    isBusy = false
                }
            },
            onClear = {
                scope.launch {
                    settingsManager.clearNavidromeConfig()
                    message = context.getString(R.string.remote_source_cleared_named, context.getString(R.string.remote_source_navidrome))
                }
            }
        )

        RemoteServerConfigCard(
            title = stringResource(R.string.remote_source_emby),
            summary = savedEmby.serverName.ifBlank { stringResource(R.string.remote_source_emby_summary) },
            url = embyUrl,
            username = embyUser,
            password = embyPassword,
            passwordLabel = stringResource(R.string.webdav_password),
            enabled = !isBusy,
            onUrlChange = { embyUrl = it },
            onUsernameChange = { embyUser = it },
            onPasswordChange = { embyPassword = it },
            onSave = {
                scope.launch {
                    isBusy = true
                    runCatching {
                        val login = embyService.login(embyUrl, embyUser, embyPassword)
                        settingsManager.setEmbyConfig(embyUrl, embyUser, login.token, login.userId, login.serverName)
                        settingsManager.selectOnlineProvider(RemoteMusicProvider.Emby)
                        embyPassword = ""
                        message = context.getString(R.string.remote_source_saved_named, context.getString(R.string.remote_source_emby))
                    }.onFailure {
                        message = it.localizedMessage ?: context.getString(R.string.remote_source_save_failed)
                        showToast(message)
                    }
                    isBusy = false
                }
            },
            onClear = {
                scope.launch {
                    settingsManager.clearEmbyConfig()
                    message = context.getString(R.string.remote_source_cleared_named, context.getString(R.string.remote_source_emby))
                }
            }
        )

        SmallTitle(text = stringResource(R.string.lx_source_imported_section))
        if (sources.isEmpty()) {
            EmptySourceText(stringResource(R.string.lx_source_empty))
        } else {
            sources.forEach { source ->
                LxSourceManageRow(
                    source = source,
                    selected = source.id == selectedId || selectedId.isBlank() && source == sources.first(),
                    enabled = !isBusy,
                    onSelect = {
                        scope.launch {
                            settingsManager.selectLxSource(source.id)
                            message = context.getString(R.string.lx_source_switched_named, source.name)
                        }
                    },
                    onRemove = {
                        scope.launch {
                            settingsManager.removeLxSource(source.id)
                            message = context.getString(R.string.lx_source_removed_named, source.name)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun RemoteProviderSelector(
    selectedProvider: RemoteMusicProvider,
    navidromeConfigured: Boolean,
    embyConfigured: Boolean,
    onSelect: (RemoteMusicProvider) -> Unit
) {
    Column {
        RemoteProviderRow(RemoteMusicProvider.Lx, selectedProvider == RemoteMusicProvider.Lx, true, onSelect)
        RemoteProviderRow(RemoteMusicProvider.Navidrome, selectedProvider == RemoteMusicProvider.Navidrome, navidromeConfigured, onSelect)
        RemoteProviderRow(RemoteMusicProvider.Emby, selectedProvider == RemoteMusicProvider.Emby, embyConfigured, onSelect)
    }
}

@Composable
private fun RemoteProviderRow(
    provider: RemoteMusicProvider,
    selected: Boolean,
    configured: Boolean,
    onSelect: (RemoteMusicProvider) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        onClick = { if (configured) onSelect(provider) }
    ) {
        BasicComponent(
            title = if (selected) stringResource(R.string.lx_source_current_suffix, provider.displayName(LocalContext.current)) else provider.displayName(LocalContext.current),
            summary = if (configured) stringResource(R.string.remote_source_ready) else stringResource(R.string.remote_source_not_configured)
        )
    }
}

@Composable
private fun RemoteServerConfigCard(
    title: String,
    summary: String,
    url: String,
    username: String,
    password: String,
    passwordLabel: String,
    enabled: Boolean,
    onUrlChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSave: () -> Unit,
    onClear: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            BasicComponent(title = title, summary = summary)
            Spacer(modifier = Modifier.height(10.dp))
            EllaMiuixTextField(
                value = url,
                onValueChange = onUrlChange,
                label = stringResource(R.string.webdav_url),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(8.dp))
            EllaMiuixTextField(
                value = username,
                onValueChange = onUsernameChange,
                label = stringResource(R.string.webdav_username),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(8.dp))
            EllaMiuixTextField(
                value = password,
                onValueChange = onPasswordChange,
                label = passwordLabel,
                singleLine = true,
                visualTransformation = PasswordVisualTransformation()
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(enabled = enabled && url.isNotBlank() && username.isNotBlank(), onClick = onSave) {
                    Text(stringResource(R.string.common_save))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(enabled = enabled, onClick = onClear) {
                    Text(stringResource(R.string.common_delete))
                }
            }
        }
    }
}

private fun RemoteMusicProvider.displayName(context: android.content.Context): String =
    when (this) {
        RemoteMusicProvider.Lx -> "LX Music"
        RemoteMusicProvider.Navidrome -> context.getString(R.string.remote_source_navidrome)
        RemoteMusicProvider.Emby -> context.getString(R.string.remote_source_emby)
    }

@Composable
private fun SourceSettingsScaffold(
    title: String,
    onBack: () -> Unit,
    message: String,
    importUrl: String,
    onImportUrlChange: (String) -> Unit,
    importPlaceholder: String,
    isBusy: Boolean,
    onLocalImport: () -> Unit,
    onUrlImport: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ellaPageBackground())
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        EllaSmallTopAppBar(
            title = title,
            color = ellaPageBackground(),
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
            SmallTitle(text = stringResource(R.string.lx_source_import_section))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    EllaMiuixTextField(
                        value = importUrl,
                        onValueChange = onImportUrlChange,
                        label = importPlaceholder,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(enabled = !isBusy, onClick = onLocalImport) {
                            Text(stringResource(R.string.lx_source_local_js))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(enabled = !isBusy && importUrl.isNotBlank(), onClick = onUrlImport) {
                            Icon(
                                imageVector = MiuixIcons.Regular.Import,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.lx_source_url_import))
                        }
                    }
                }
            }

            Text(
                text = message,
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 10.dp)
            )

            content()
            Spacer(modifier = Modifier.height(120.dp))
        }
    }
}

@Composable
private fun LxSourceManageRow(
    source: LxSourceConfig,
    selected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
    onRemove: () -> Unit
) {
    SourceManageRow(
        title = source.name,
        summary = source.url,
        selected = selected,
        enabled = enabled,
        onSelect = onSelect,
        onRemove = onRemove
    )
}

@Composable
private fun SourceManageRow(
    title: String,
    summary: String,
    selected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp),
        onClick = { if (enabled && !selected) onSelect() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                BasicComponent(
                    title = if (selected) stringResource(R.string.lx_source_current_suffix, title) else title,
                    summary = summary
                )
            }
            IconButton(
                enabled = enabled,
                onClick = onRemove
            ) {
                Icon(
                    imageVector = MiuixIcons.Regular.Delete,
                    contentDescription = stringResource(R.string.common_delete),
                    tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
private fun EmptySourceText(text: String) {
    Text(
        text = text,
        fontSize = 14.sp,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp)
    )
}
