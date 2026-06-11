package com.ella.music.plugin.source

import android.content.Context
import android.net.Uri
import com.ella.music.data.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class LyricoPluginManager(
    context: Context,
    private val settingsManager: SettingsManager = SettingsManager.getInstance(context)
) {
    private val appContext = context.applicationContext
    private val loader = BuiltInPluginLoader(appContext)
    private val customStore = CustomPluginStore(appContext)

    suspend fun availableSources(): List<BuiltInPluginSource> =
        loader.loadPlugins() + customStore.loadPlugins()

    suspend fun importPluginZip(uri: Uri) = customStore.importPluginZip(uri)

    suspend fun deletePlugin(id: String): Boolean = customStore.deletePlugin(id)

    suspend fun enabledSources(): List<BuiltInPluginSource> {
        val sources = availableSources()
        val enabledIds = settingsManager.lyricoPluginEnabledIds.first()
        return sources.filter { it.manifest.id in enabledIds }
    }

    suspend fun searchSongs(keyword: String, pageSizePerSource: Int = 8): List<PluginSearchHit> = withContext(Dispatchers.IO) {
        enabledSources().flatMap { source ->
            ScriptSearchSource(source).use { runtime ->
                runtime.searchSongs(keyword, pageSize = pageSizePerSource).map { result ->
                    PluginSearchHit(source.manifest.id, source.manifest.name, result)
                }
            }
        }
    }

    suspend fun getLyrics(hit: PluginSearchHit): PluginLyricsResult? = withContext(Dispatchers.IO) {
        val source = availableSources().firstOrNull { it.manifest.id == hit.sourceId } ?: return@withContext null
        ScriptSearchSource(source).use { runtime -> runtime.getLyrics(hit.song) }
    }

    companion object {
        val DEFAULT_ENABLED_IDS = setOf(
            "com.neteasecloudmusic.source",
            "com.qqmusic.source",
            "com.kugou.source",
            "com.sodamusic.source"
        )

        fun normalizeEnabledIds(raw: String?): Set<String> =
            raw.orEmpty()
                .split(',', '，', ';', '；', '\n')
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .toSet()
                .ifEmpty { DEFAULT_ENABLED_IDS }
    }
}
