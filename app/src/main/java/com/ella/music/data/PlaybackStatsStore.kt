package com.ella.music.data

import android.content.Context
import android.util.Log
import com.ella.music.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class SongPlaybackStats(
    val songId: Long,
    val title: String,
    val artist: String,
    val album: String,
    val playCount: Int,
    val listenedMs: Long,
    val lastPlayedAt: Long
)

data class PlaybackHistoryEntry(
    val songId: Long,
    val title: String,
    val artist: String,
    val album: String,
    val playedAt: Long
)

class PlaybackStatsStore private constructor(context: Context) {
    private val statsFile = File(context.applicationContext.filesDir, "playback_stats.json")
    private val historyFile = File(context.applicationContext.filesDir, "playback_history.json")
    private val dailyStatsFile = File(context.applicationContext.filesDir, "playback_daily_stats.json")
    private val _stats = MutableStateFlow<List<SongPlaybackStats>>(emptyList())
    val stats: StateFlow<List<SongPlaybackStats>> = _stats.asStateFlow()
    private val _history = MutableStateFlow<List<PlaybackHistoryEntry>>(emptyList())
    val history: StateFlow<List<PlaybackHistoryEntry>> = _history.asStateFlow()
    private val _dailyListenMs = MutableStateFlow<Map<String, Long>>(emptyMap())
    val dailyListenMs: StateFlow<Map<String, Long>> = _dailyListenMs.asStateFlow()

    init {
        loadStats()
        loadHistory()
        loadDailyStats()
    }

    suspend fun recordPlay(song: Song) {
        val now = System.currentTimeMillis()
        update(song) { current ->
            current.copy(
                playCount = current.playCount + 1,
                lastPlayedAt = now
            )
        }
        appendHistory(song, now)
    }

    suspend fun addListenTime(song: Song, listenedMs: Long) {
        if (listenedMs <= 0) return
        val now = System.currentTimeMillis()
        update(song) { current ->
            current.copy(
                listenedMs = current.listenedMs + listenedMs,
                lastPlayedAt = now
            )
        }
        addDailyListenTime(now, listenedMs)
    }

    suspend fun exportJson(): JSONObject = withContext(Dispatchers.IO) {
        JSONObject()
            .put("stats", statsToJson(_stats.value))
            .put("history", historyToJson(_history.value))
            .put("dailyListenMs", dailyStatsToJson(_dailyListenMs.value))
    }

    suspend fun restoreJson(payload: JSONObject) = withContext(Dispatchers.IO) {
        val stats = payload.optJSONArray("stats")?.toStatsList().orEmpty()
        val history = payload.optJSONArray("history")?.toHistoryList().orEmpty()
        val daily = payload.optJSONObject("dailyListenMs")?.toDailyStatsMap().orEmpty()

        _stats.value = stats
        _history.value = history
        _dailyListenMs.value = daily.toSortedMap()

        save(stats)
        saveHistory(history)
        saveDailyStats(daily)
    }

    private suspend fun update(
        song: Song,
        transform: (SongPlaybackStats) -> SongPlaybackStats
    ) = withContext(Dispatchers.IO) {
        val current = _stats.value.associateBy { it.songId }.toMutableMap()
        val existing = current[song.id] ?: SongPlaybackStats(
            songId = song.id,
            title = song.title,
            artist = song.artist,
            album = song.album,
            playCount = 0,
            listenedMs = 0L,
            lastPlayedAt = 0L
        )
        current[song.id] = transform(
            existing.copy(
                title = song.title,
                artist = song.artist,
                album = song.album
            )
        )
        val sorted = current.values.sortedByDescending { it.lastPlayedAt }
        _stats.value = sorted
        save(sorted)
    }

    private suspend fun appendHistory(song: Song, playedAt: Long) = withContext(Dispatchers.IO) {
        val updated = (listOf(
            PlaybackHistoryEntry(
                songId = song.id,
                title = song.title,
                artist = song.artist,
                album = song.album,
                playedAt = playedAt
            )
        ) + _history.value)
            .distinctBy { "${it.songId}:${it.playedAt}" }
            .take(MAX_HISTORY_ITEMS)
        _history.value = updated
        saveHistory(updated)
    }

    private suspend fun addDailyListenTime(timestampMs: Long, listenedMs: Long) = withContext(Dispatchers.IO) {
        val key = timestampMs.toDateKey()
        val updated = _dailyListenMs.value.toMutableMap()
        updated[key] = (updated[key] ?: 0L) + listenedMs
        _dailyListenMs.value = updated.toSortedMap()
        saveDailyStats(_dailyListenMs.value)
    }

    private fun loadStats() {
        if (!statsFile.exists()) return
        runCatching {
            val array = JSONArray(statsFile.readText())
            _stats.value = array.toStatsList()
        }.onFailure {
            Log.w("PlaybackStatsStore", "Failed to load playback stats", it)
        }
    }

    private fun loadHistory() {
        if (!historyFile.exists()) return
        runCatching {
            val array = JSONArray(historyFile.readText())
            _history.value = array.toHistoryList()
        }.onFailure {
            Log.w("PlaybackStatsStore", "Failed to load playback history", it)
        }
    }

    private fun loadDailyStats() {
        if (!dailyStatsFile.exists()) return
        runCatching {
            val payload = JSONObject(dailyStatsFile.readText())
            _dailyListenMs.value = payload.toDailyStatsMap().toSortedMap()
        }.onFailure {
            Log.w("PlaybackStatsStore", "Failed to load daily playback stats", it)
        }
    }

    private fun save(stats: List<SongPlaybackStats>) {
        runCatching {
            statsFile.writeText(statsToJson(stats).toString())
        }.onFailure {
            Log.w("PlaybackStatsStore", "Failed to save playback stats", it)
        }
    }

    private fun saveHistory(history: List<PlaybackHistoryEntry>) {
        runCatching {
            historyFile.writeText(historyToJson(history).toString())
        }.onFailure {
            Log.w("PlaybackStatsStore", "Failed to save playback history", it)
        }
    }

    private fun saveDailyStats(dailyStats: Map<String, Long>) {
        runCatching {
            dailyStatsFile.writeText(dailyStatsToJson(dailyStats).toString())
        }.onFailure {
            Log.w("PlaybackStatsStore", "Failed to save daily playback stats", it)
        }
    }

    private fun statsToJson(stats: List<SongPlaybackStats>): JSONArray {
        val array = JSONArray()
        stats.forEach { stat ->
            array.put(
                JSONObject()
                    .put("songId", stat.songId)
                    .put("title", stat.title)
                    .put("artist", stat.artist)
                    .put("album", stat.album)
                    .put("playCount", stat.playCount)
                    .put("listenedMs", stat.listenedMs)
                    .put("lastPlayedAt", stat.lastPlayedAt)
            )
        }
        return array
    }

    private fun historyToJson(history: List<PlaybackHistoryEntry>): JSONArray {
        val array = JSONArray()
        history.forEach { entry ->
            array.put(
                JSONObject()
                    .put("songId", entry.songId)
                    .put("title", entry.title)
                    .put("artist", entry.artist)
                    .put("album", entry.album)
                    .put("playedAt", entry.playedAt)
            )
        }
        return array
    }

    private fun dailyStatsToJson(dailyStats: Map<String, Long>): JSONObject {
        val payload = JSONObject()
        dailyStats.forEach { (date, listenedMs) ->
            payload.put(date, listenedMs)
        }
        return payload
    }

    private fun JSONArray.toStatsList(): List<SongPlaybackStats> =
        List(length()) { index ->
            val item = getJSONObject(index)
            SongPlaybackStats(
                songId = item.getLong("songId"),
                title = item.optString("title"),
                artist = item.optString("artist"),
                album = item.optString("album"),
                playCount = item.optInt("playCount"),
                listenedMs = item.optLong("listenedMs"),
                lastPlayedAt = item.optLong("lastPlayedAt")
            )
        }.sortedByDescending { it.lastPlayedAt }

    private fun JSONArray.toHistoryList(): List<PlaybackHistoryEntry> =
        List(length()) { index ->
            val item = getJSONObject(index)
            PlaybackHistoryEntry(
                songId = item.optLong("songId"),
                title = item.optString("title"),
                artist = item.optString("artist"),
                album = item.optString("album"),
                playedAt = item.optLong("playedAt")
            )
        }.filter { it.playedAt > 0L }
            .sortedByDescending { it.playedAt }
            .take(MAX_HISTORY_ITEMS)

    private fun JSONObject.toDailyStatsMap(): Map<String, Long> {
        val parsed = mutableMapOf<String, Long>()
        keys().forEach { key ->
            parsed[key] = optLong(key)
        }
        return parsed
    }

    private fun Long.toDateKey(): String {
        val calendar = java.util.Calendar.getInstance()
        calendar.timeInMillis = this
        val year = calendar.get(java.util.Calendar.YEAR)
        val month = calendar.get(java.util.Calendar.MONTH) + 1
        val day = calendar.get(java.util.Calendar.DAY_OF_MONTH)
        return "%04d-%02d-%02d".format(year, month, day)
    }

    companion object {
        private const val MAX_HISTORY_ITEMS = 200

        @Volatile
        private var instance: PlaybackStatsStore? = null

        fun getInstance(context: Context): PlaybackStatsStore {
            return instance ?: synchronized(this) {
                instance ?: PlaybackStatsStore(context.applicationContext).also { instance = it }
            }
        }
    }
}
