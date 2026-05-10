package com.ella.music.ui.folder

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.ella.music.data.webdav.WebDavClient
import com.ella.music.data.webdav.WebDavConfig
import com.ella.music.data.webdav.WebDavItem
import com.ella.music.data.webdav.WebDavTestResult
import com.ella.music.viewmodel.MainViewModel
import com.ella.music.viewmodel.PlayerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun WebDavScreen(
    mainViewModel: MainViewModel,
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit,
    onNavigateToPlayer: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val savedUrl by mainViewModel.settingsManager.webDavUrl.collectAsState(initial = "")
    val savedUser by mainViewModel.settingsManager.webDavUsername.collectAsState(initial = "")
    val savedPassword by mainViewModel.settingsManager.webDavPassword.collectAsState(initial = "")
    val savedLastUrl by mainViewModel.settingsManager.webDavLastUrl.collectAsState(initial = "")

    var showSettings by remember { mutableStateOf(false) }
    var webDavUrl by remember { mutableStateOf("") }
    var webDavUser by remember { mutableStateOf("") }
    var webDavPassword by remember { mutableStateOf("") }
    var currentUrl by remember { mutableStateOf("") }
    var items by remember { mutableStateOf<List<WebDavItem>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var testStatus by remember { mutableStateOf<String?>(null) }
    var loadedKey by remember { mutableStateOf("") }

    fun load(url: String, forceRefresh: Boolean = false) {
        scope.launch {
            val config = WebDavConfig(webDavUrl, webDavUser, webDavPassword)
            loading = true
            error = null
            if (forceRefresh) WebDavClient.clearListCache()
            runCatching {
                withContext(Dispatchers.IO) { WebDavClient.list(config, url, forceRefresh = forceRefresh) }
            }.onSuccess {
                items = it
            }.onFailure {
                items = emptyList()
                error = it.localizedMessage ?: "WebDAV 加载失败"
            }
            loading = false
        }
    }

    LaunchedEffect(savedUrl, savedUser, savedPassword, savedLastUrl) {
        webDavUrl = savedUrl
        webDavUser = savedUser
        webDavPassword = savedPassword
        if (savedUrl.isBlank()) {
            currentUrl = ""
            items = emptyList()
            error = null
            showSettings = true
            return@LaunchedEffect
        }
        val startUrl = savedLastUrl.ifBlank { savedUrl }
        val key = listOf(savedUrl, savedUser, savedPassword, startUrl).joinToString("|")
        if (loadedKey == key && items.isNotEmpty()) return@LaunchedEffect
        loadedKey = key
        currentUrl = startUrl
        load(startUrl)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        SmallTopAppBar(
            title = "WebDAV 音乐库",
            color = MiuixTheme.colorScheme.background,
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = MiuixIcons.Regular.Back,
                        contentDescription = "返回"
                    )
                }
            },
            actions = {
                Text(
                    text = "设置",
                    color = MiuixTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable { showSettings = true }
                        .padding(end = 16.dp)
                )
            }
        )

        if (showSettings) {
            WebDavSettingsDialog(
                url = webDavUrl,
                username = webDavUser,
                password = webDavPassword,
                onUrlChange = { webDavUrl = it },
                onUsernameChange = { webDavUser = it },
                onPasswordChange = { webDavPassword = it },
                testStatus = testStatus,
                onDismiss = { showSettings = false },
                onTest = {
                    scope.launch {
                        testStatus = "正在测试 WebDAV 连接..."
                        val result = runCatching {
                            withContext(Dispatchers.IO) {
                                WebDavClient.testDetailed(WebDavConfig(webDavUrl, webDavUser, webDavPassword))
                            }
                        }.getOrElse { WebDavTestResult(ok = false, message = it.localizedMessage ?: "WebDAV 连接失败") }
                        testStatus = result.message
                        error = if (result.ok) null else result.message
                        Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                    }
                },
                onSave = {
                    scope.launch {
                        mainViewModel.settingsManager.setWebDavConfig(webDavUrl, webDavUser, webDavPassword)
                    }
                    currentUrl = webDavUrl
                    showSettings = false
                    load(webDavUrl, forceRefresh = true)
                    Toast.makeText(context, "WebDAV 配置已保存", Toast.LENGTH_SHORT).show()
                },
                onClear = {
                    scope.launch { mainViewModel.settingsManager.clearWebDavConfig() }
                    webDavUrl = ""
                    webDavUser = ""
                    webDavPassword = ""
                    currentUrl = ""
                    items = emptyList()
                    error = null
                    testStatus = null
                    showSettings = false
                    Toast.makeText(context, "WebDAV 配置已移除", Toast.LENGTH_SHORT).show()
                }
            )
        }

        if (savedUrl.isBlank() && items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = MiuixIcons.Regular.Folder,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                    Text(
                        text = "请先配置 WebDAV",
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 160.dp)
            ) {
                item {
                    WebDavBrowserCard(
                        currentUrl = WebDavClient.displayUrl(currentUrl.ifBlank { savedUrl }),
                        loading = loading,
                        error = error,
                        items = items,
                        onRefresh = { load(currentUrl.ifBlank { webDavUrl }, forceRefresh = true) },
                        onItemClick = { item ->
                            if (item.isDirectory) {
                                currentUrl = item.url
                                scope.launch { mainViewModel.settingsManager.setWebDavLastUrl(item.url) }
                                load(item.url)
                            } else {
                                playerViewModel.setPlaylist(listOf(item.toRemoteSong()), 0)
                                onNavigateToPlayer()
                            }
                        },
                        onAddToQueue = { item ->
                            playerViewModel.addToPlaylist(item.toRemoteSong())
                            Toast.makeText(context, "已加入播放列表", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }
    }
}
