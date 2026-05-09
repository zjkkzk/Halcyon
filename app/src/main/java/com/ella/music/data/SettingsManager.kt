package com.ella.music.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "ella_settings")

class SettingsManager(private val context: Context) {

    companion object {
        val KEY_LYRICON_ENABLED = booleanPreferencesKey("lyricon_enabled")
        val KEY_LYRICON_TRANSLATION = booleanPreferencesKey("lyricon_translation")
        val KEY_AUTO_SCAN = booleanPreferencesKey("auto_scan")
        val KEY_GAPLESS = booleanPreferencesKey("gapless_playback")
        val KEY_THEME_MODE = intPreferencesKey("theme_mode")
        val KEY_TICKER_ENABLED = booleanPreferencesKey("ticker_enabled")
        val KEY_MIN_DURATION = intPreferencesKey("min_duration_sec")
        val KEY_REPLAYGAIN_ENABLED = booleanPreferencesKey("replaygain_enabled")
        val KEY_LYRIC_PAGE_TRANSLATION = booleanPreferencesKey("lyric_page_translation")
        val KEY_PLAYER_HDR_GLOW = booleanPreferencesKey("player_hdr_glow")
        val KEY_WEBDAV_URL = stringPreferencesKey("webdav_url")
        val KEY_WEBDAV_USERNAME = stringPreferencesKey("webdav_username")
        val KEY_WEBDAV_PASSWORD = stringPreferencesKey("webdav_password")
        val KEY_WEBDAV_LAST_URL = stringPreferencesKey("webdav_last_url")
        val KEY_LX_SOURCE_URL = stringPreferencesKey("lx_source_url")
        val KEY_LX_SOURCE_NAME = stringPreferencesKey("lx_source_name")
        val KEY_LX_SOURCE_SCRIPT = stringPreferencesKey("lx_source_script")
        val KEY_LYRIC_FONT_NAME = stringPreferencesKey("lyric_font_name")
        val KEY_LYRIC_FONT_PATH = stringPreferencesKey("lyric_font_path")

        val KEY_BLUETOOTH_LYRIC_ENABLED = booleanPreferencesKey("bluetooth_lyric_enabled")
    }

    val lyriconEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_LYRICON_ENABLED] ?: true }
    val lyriconTranslation: Flow<Boolean> = context.dataStore.data.map { it[KEY_LYRICON_TRANSLATION] ?: true }
    val autoScan: Flow<Boolean> = context.dataStore.data.map { it[KEY_AUTO_SCAN] ?: true }
    val gaplessPlayback: Flow<Boolean> = context.dataStore.data.map { it[KEY_GAPLESS] ?: true }
    val themeMode: Flow<Int> = context.dataStore.data.map { it[KEY_THEME_MODE] ?: 0 }
    val tickerEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_TICKER_ENABLED] ?: true }
    val minDurationSec: Flow<Int> = context.dataStore.data.map { it[KEY_MIN_DURATION] ?: 15 }
    val replayGainEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_REPLAYGAIN_ENABLED] ?: false }
    val lyricPageTranslation: Flow<Boolean> = context.dataStore.data.map { it[KEY_LYRIC_PAGE_TRANSLATION] ?: true }
    val playerHdrGlow: Flow<Boolean> = context.dataStore.data.map { it[KEY_PLAYER_HDR_GLOW] ?: false }
    val webDavUrl: Flow<String> = context.dataStore.data.map { it[KEY_WEBDAV_URL] ?: "" }
    val webDavUsername: Flow<String> = context.dataStore.data.map { it[KEY_WEBDAV_USERNAME] ?: "" }
    val webDavPassword: Flow<String> = context.dataStore.data.map { it[KEY_WEBDAV_PASSWORD] ?: "" }
    val webDavLastUrl: Flow<String> = context.dataStore.data.map { it[KEY_WEBDAV_LAST_URL] ?: "" }
    val lxSourceUrl: Flow<String> = context.dataStore.data.map { it[KEY_LX_SOURCE_URL] ?: "" }
    val lxSourceName: Flow<String> = context.dataStore.data.map { it[KEY_LX_SOURCE_NAME] ?: "" }
    val lxSourceScript: Flow<String> = context.dataStore.data.map { it[KEY_LX_SOURCE_SCRIPT] ?: "" }
    val lyricFontName: Flow<String> = context.dataStore.data.map { it[KEY_LYRIC_FONT_NAME] ?: "" }
    val lyricFontPath: Flow<String> = context.dataStore.data.map { it[KEY_LYRIC_FONT_PATH] ?: "" }

    val bluetoothLyricEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_BLUETOOTH_LYRIC_ENABLED] ?: false }
    suspend fun setLyriconEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_LYRICON_ENABLED] = enabled }
    }

    suspend fun setLyriconTranslation(enabled: Boolean) {
        context.dataStore.edit { it[KEY_LYRICON_TRANSLATION] = enabled }
    }

    suspend fun setAutoScan(enabled: Boolean) {
        context.dataStore.edit { it[KEY_AUTO_SCAN] = enabled }
    }

    suspend fun setGaplessPlayback(enabled: Boolean) {
        context.dataStore.edit { it[KEY_GAPLESS] = enabled }
    }

    suspend fun setThemeMode(mode: Int) {
        context.dataStore.edit { it[KEY_THEME_MODE] = mode }
    }

    suspend fun setTickerEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_TICKER_ENABLED] = enabled }
    }

    suspend fun setBluetoothLyricEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_BLUETOOTH_LYRIC_ENABLED] = enabled }
    }

    suspend fun setMinDurationSec(seconds: Int) {
        context.dataStore.edit { it[KEY_MIN_DURATION] = seconds }
    }

    suspend fun setReplayGainEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_REPLAYGAIN_ENABLED] = enabled }
    }

    suspend fun setLyricPageTranslation(enabled: Boolean) {
        context.dataStore.edit { it[KEY_LYRIC_PAGE_TRANSLATION] = enabled }
    }

    suspend fun setPlayerHdrGlow(enabled: Boolean) {
        context.dataStore.edit { it[KEY_PLAYER_HDR_GLOW] = enabled }
    }

    suspend fun setWebDavConfig(url: String, username: String, password: String) {
        context.dataStore.edit {
            it[KEY_WEBDAV_URL] = url.trim()
            it[KEY_WEBDAV_USERNAME] = username
            it[KEY_WEBDAV_PASSWORD] = password
            it[KEY_WEBDAV_LAST_URL] = url.trim()
        }
    }

    suspend fun setWebDavLastUrl(url: String) {
        context.dataStore.edit {
            if (url.isBlank()) it.remove(KEY_WEBDAV_LAST_URL) else it[KEY_WEBDAV_LAST_URL] = url.trim()
        }
    }

    suspend fun clearWebDavConfig() {
        context.dataStore.edit {
            it.remove(KEY_WEBDAV_URL)
            it.remove(KEY_WEBDAV_USERNAME)
            it.remove(KEY_WEBDAV_PASSWORD)
            it.remove(KEY_WEBDAV_LAST_URL)
        }
    }

    suspend fun setLxSource(url: String, name: String, script: String) {
        context.dataStore.edit {
            it[KEY_LX_SOURCE_URL] = url.trim()
            it[KEY_LX_SOURCE_NAME] = name.ifBlank { "落雪源" }
            it[KEY_LX_SOURCE_SCRIPT] = script
        }
    }

    suspend fun clearLxSource() {
        context.dataStore.edit {
            it.remove(KEY_LX_SOURCE_URL)
            it.remove(KEY_LX_SOURCE_NAME)
            it.remove(KEY_LX_SOURCE_SCRIPT)
        }
    }

    suspend fun setLyricFont(name: String, path: String) {
        context.dataStore.edit {
            it[KEY_LYRIC_FONT_NAME] = name.ifBlank { "自定义字体" }
            it[KEY_LYRIC_FONT_PATH] = path
        }
    }

    suspend fun clearLyricFont() {
        context.dataStore.edit {
            it.remove(KEY_LYRIC_FONT_NAME)
            it.remove(KEY_LYRIC_FONT_PATH)
        }
    }
}
