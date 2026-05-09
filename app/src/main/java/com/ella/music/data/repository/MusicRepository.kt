package com.ella.music.data.repository

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import android.util.LruCache
import com.ella.music.data.SettingsManager
import com.ella.music.data.model.Album
import com.ella.music.data.model.AudioInfo
import com.ella.music.data.model.LyricLine
import com.ella.music.data.model.Song
import com.ella.music.data.parser.LrcParser
import com.ella.music.data.scanner.MusicScanner
import com.ella.music.data.webdav.WebDavClient
import com.ella.music.data.webdav.WebDavConfig
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class MusicRepository(private val context: Context) {

    private val scanner = MusicScanner(context)
    private val settingsManager = SettingsManager(context)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    private val _albums = MutableStateFlow<List<Album>>(emptyList())
    val albums: StateFlow<List<Album>> = _albums.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _scanProgress = MutableStateFlow(0)
    val scanProgress: StateFlow<Int> = _scanProgress.asStateFlow()

    private val lyricsCache = mutableMapOf<Long, List<LyricLine>>()
    private val audioInfoCache = mutableMapOf<Long, AudioInfo>()
    private val replayGainCache = mutableMapOf<Long, Float?>()
    private val coverArtCache = mutableMapOf<Long, ByteArray?>()
    private val coverBitmapCache = object : LruCache<Long, Bitmap>(16 * 1024) {
        override fun sizeOf(key: Long, value: Bitmap): Int = value.byteCount / 1024
    }
    private val libraryCacheFile = File(context.filesDir, "music_library_cache.json")
    private val remoteAudioCacheDir = File(context.cacheDir, "webdav_audio")

    suspend fun scanMusic(minDurationMs: Long = 0) {
        _isScanning.value = true
        _scanProgress.value = 0
        try {
            _songs.value = scanner.scanAllSongs(minDurationMs) { count ->
                _scanProgress.value = count
            }
            _albums.value = scanner.scanAlbums()
            saveLibraryCache(_songs.value, _albums.value)
        } finally {
            _isScanning.value = false
        }
    }

    suspend fun loadCachedLibrary() = withContext(Dispatchers.IO) {
        if (!libraryCacheFile.exists()) return@withContext

        runCatching {
            val root = JSONObject(libraryCacheFile.readText())
            val songs = root.getJSONArray("songs").toSongList()
            val albums = root.optJSONArray("albums")?.toAlbumList() ?: songs.toAlbums()
            _songs.value = songs
            _albums.value = albums
        }.onFailure {
            Log.w("MusicRepo", "Failed to load music library cache", it)
        }
    }

    suspend fun getLyrics(song: Song): List<LyricLine> = withContext(Dispatchers.IO) {
        lyricsCache[song.id]?.let { return@withContext it }

        Log.d("MusicRepo", "Loading lyrics for: ${song.title} path=${song.path}")

        fetchOnlineLyrics(song)?.let { onlineLyrics ->
            lyricsCache[song.id] = onlineLyrics
            return@withContext onlineLyrics
        }

        val effectivePath = song.effectiveLocalPathForMetadata()
        val lrcContent = LrcParser.findLrcFile(effectivePath)
        if (lrcContent != null) {
            val parsed = LrcParser.parse(lrcContent)
            Log.d("MusicRepo", "LRC parsed: ${parsed.lyrics.size} lines for ${song.title}")
            lyricsCache[song.id] = parsed.lyrics
            return@withContext parsed.lyrics
        }

        Log.d("MusicRepo", "No LRC file found, trying embedded lyrics for ${song.title}")
        val embedded = scanner.extractEmbeddedLyrics(effectivePath)
        if (!embedded.isNullOrBlank()) {
            Log.d("MusicRepo", "Embedded lyrics found (${embedded.length} chars) for ${song.title}")
            val parsed = LrcParser.parse(embedded)
            if (parsed.lyrics.isNotEmpty()) {
                Log.d("MusicRepo", "Embedded lyrics parsed as LRC: ${parsed.lyrics.size} lines")
                lyricsCache[song.id] = parsed.lyrics
                return@withContext parsed.lyrics
            }

            Log.d("MusicRepo", "Embedded lyrics not LRC format, using plain text")
            val result = mutableListOf<LyricLine>()
            val lines = embedded.lines()
            var timeOffset = 0L
            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.isNotEmpty()) {
                    result.add(LyricLine(timeMs = timeOffset, text = trimmed, words = emptyList()))
                    timeOffset += 3000L
                }
            }
            if (result.isNotEmpty()) {
                Log.d("MusicRepo", "Plain text lyrics: ${result.size} lines")
                lyricsCache[song.id] = result
                return@withContext result
            }
        }

        Log.d("MusicRepo", "No lyrics found for ${song.title}")
        lyricsCache[song.id] = emptyList()
        emptyList()
    }

    private fun fetchOnlineLyrics(song: Song): List<LyricLine>? {
        if (song.onlineSource != "kw" || song.onlineId.isBlank()) return null
        val request = Request.Builder()
            .url("https://www.kuwo.cn/newh5/singles/songinfoandlrc?musicId=${song.onlineId}")
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 EllaMusic/1.0")
            .build()
        return runCatching {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val root = JSONObject(response.body?.string().orEmpty())
                val list = root.optJSONObject("data")?.optJSONArray("lrclist") ?: return@use null
                val rawLines = List(list.length()) { index ->
                    val item = list.getJSONObject(index)
                    val timeMs = ((item.optString("time").toDoubleOrNull() ?: 0.0) * 1000).toLong()
                    LyricLine(
                        timeMs = timeMs,
                        text = item.optString("lineLyric").trim()
                    )
                }.filter { it.text.isNotBlank() }
                val lyrics = mergeKuwoTranslatedLyrics(rawLines)
                lyrics.takeIf { it.isNotEmpty() }
            }
        }.getOrElse {
            Log.w("MusicRepo", "Failed to fetch online lyrics for ${song.title}", it)
            null
        }
    }

    private fun mergeKuwoTranslatedLyrics(lines: List<LyricLine>): List<LyricLine> {
        val merged = mutableListOf<LyricLine>()
        for (line in lines.sortedBy { it.timeMs }) {
            val previous = merged.lastOrNull()
            if (
                previous != null &&
                previous.translation.isNullOrBlank() &&
                !previous.text.hasCjkOrHangul() &&
                line.text.hasCjkOrHangul()
            ) {
                merged[merged.lastIndex] = previous.copy(translation = line.text)
            } else {
                merged += line
            }
        }
        return merged
    }

    private fun String.hasCjkOrHangul(): Boolean = any { char ->
        Character.UnicodeBlock.of(char) in setOf(
            Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS,
            Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A,
            Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B,
            Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS,
            Character.UnicodeBlock.HIRAGANA,
            Character.UnicodeBlock.KATAKANA,
            Character.UnicodeBlock.HANGUL_SYLLABLES
        )
    }

    fun getReplayGain(song: Song): Float? {
        replayGainCache[song.id]?.let { return it }
        val gain = scanner.extractReplayGain(song.effectiveLocalPathForMetadata())
        replayGainCache[song.id] = gain
        return gain
    }

    fun getAudioInfo(song: Song): AudioInfo {
        audioInfoCache[song.id]?.let { return it }
        val info = runCatching {
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(song.effectiveLocalPathForMetadata())
                var audioFormat: MediaFormat? = null
                for (index in 0 until extractor.trackCount) {
                    val format = extractor.getTrackFormat(index)
                    val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                    if (mime.startsWith("audio/")) {
                        audioFormat = format
                        break
                    }
                }

                val format = audioFormat
                AudioInfo(
                    format = song.audioFormatLabel(format?.getString(MediaFormat.KEY_MIME)),
                    bitRate = format?.getIntOrZero(MediaFormat.KEY_BIT_RATE) ?: 0,
                    sampleRate = format?.getIntOrZero(MediaFormat.KEY_SAMPLE_RATE) ?: 0,
                    bitDepth = format?.getIntOrZero("bits-per-sample") ?: 0,
                    channels = format?.getIntOrZero(MediaFormat.KEY_CHANNEL_COUNT) ?: 0
                )
            } finally {
                extractor.release()
            }
        }.getOrElse {
            Log.w("MusicRepo", "Failed to read audio info for ${song.path}", it)
            AudioInfo(format = song.audioFormatLabel(null))
        }
        audioInfoCache[song.id] = info
        return info
    }

    fun getCoverArt(song: Song): ByteArray? {
        coverArtCache[song.id]?.let { return it }
        val art = scanner.extractCoverArt(song.effectiveLocalPathForMetadata())
        coverArtCache[song.id] = art
        return art
    }

    fun getCoverArtBitmap(song: Song): Bitmap? {
        coverBitmapCache.get(song.id)?.let { return it }
        val data = getCoverArt(song) ?: return null
        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeByteArray(data, 0, data.size, bounds)

        val maxSize = 512
        var sampleSize = 1
        while ((bounds.outWidth / sampleSize) > maxSize || (bounds.outHeight / sampleSize) > maxSize) {
            sampleSize *= 2
        }

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize.coerceAtLeast(1)
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        return BitmapFactory.decodeByteArray(data, 0, data.size, options)
            ?.also { coverBitmapCache.put(song.id, it) }
    }

    fun getAlbumArtUri(albumId: Long): Uri? {
        if (albumId <= 0L) return null
        return ContentUris.withAppendedId(
            Uri.parse("content://media/external/audio/albumart"),
            albumId
        )
    }

    fun getSongsForAlbum(albumId: Long): List<Song> {
        return _songs.value.filter { it.albumId == albumId }
    }

    suspend fun deleteSongs(songs: Collection<Song>): Int = withContext(Dispatchers.IO) {
        var deleted = 0
        songs.forEach { song ->
            val deletedFromStore = runCatching {
                val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, song.id)
                context.contentResolver.delete(uri, null, null) > 0
            }.getOrDefault(false)

            val deletedFromFile = if (!deletedFromStore) {
                runCatching {
                    val file = File(song.path)
                    file.exists() && file.delete()
                }.getOrDefault(false)
            } else {
                true
            }

            if (deletedFromFile) deleted++
        }

        if (deleted > 0) {
            val deletedIds = songs.map { it.id }.toSet()
            _songs.value = _songs.value.filterNot { it.id in deletedIds }
            _albums.value = _songs.value.toAlbums()
            saveLibraryCache(_songs.value, _albums.value)
        }
        deleted
    }

    fun clearCache() {
        lyricsCache.clear()
        audioInfoCache.clear()
        replayGainCache.clear()
        coverArtCache.clear()
        coverBitmapCache.evictAll()
    }

    fun clearRemoteMetadataCache() {
        clearCache()
        runCatching {
            if (remoteAudioCacheDir.exists()) {
                remoteAudioCacheDir.deleteRecursively()
            }
        }.onFailure {
            Log.w("MusicRepo", "Failed to clear remote metadata cache", it)
        }
    }

    private fun Song.effectiveLocalPathForMetadata(): String {
        if (!path.startsWith("http://") && !path.startsWith("https://")) return path
        val target = File(remoteAudioCacheDir, "${path.sha256()}.${fileName.substringAfterLast('.', "audio")}")
        if (target.exists() && target.length() > 0L) return target.absolutePath
        return runCatching {
            val config = runBlocking(Dispatchers.IO) {
                WebDavConfig(
                    url = settingsManager.webDavUrl.first(),
                    username = settingsManager.webDavUsername.first(),
                    password = settingsManager.webDavPassword.first()
                )
            }
            WebDavClient.downloadToFile(path, config, target).absolutePath
        }.getOrElse {
            Log.w("MusicRepo", "Failed to cache remote metadata file for $path", it)
            path
        }
    }

    private fun String.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun MediaFormat.getIntOrZero(key: String): Int {
        return if (containsKey(key)) runCatching { getInteger(key) }.getOrDefault(0) else 0
    }

    private fun Song.audioFormatLabel(mime: String?): String {
        val source = (mime ?: mimeType).lowercase()
        val extension = fileName.substringAfterLast('.', path.substringAfterLast('.')).lowercase()
        return when {
            "flac" in source || extension == "flac" -> "FLAC"
            "mpeg" in source || "mp3" in source || extension == "mp3" -> "MP3"
            "wav" in source || extension == "wav" -> "WAV"
            "aac" in source || extension == "aac" -> "AAC"
            "mp4" in source || "m4a" in source || extension == "m4a" -> "ALAC/M4A"
            "ogg" in source || extension == "ogg" -> "OGG"
            "opus" in source || extension == "opus" -> "OPUS"
            extension.isNotBlank() -> extension.uppercase()
            else -> "Audio"
        }
    }

    private suspend fun saveLibraryCache(songs: List<Song>, albums: List<Album>) = withContext(Dispatchers.IO) {
        runCatching {
            val root = JSONObject()
                .put("version", 1)
                .put("songs", songsToJsonArray(songs))
                .put("albums", albumsToJsonArray(albums))
            libraryCacheFile.writeText(root.toString())
        }.onFailure {
            Log.w("MusicRepo", "Failed to save music library cache", it)
        }
    }

    private fun List<Song>.toAlbums(): List<Album> {
        return groupBy { it.albumId }
            .map { (albumId, albumSongs) ->
                val first = albumSongs.first()
                Album(
                    id = albumId,
                    name = first.album,
                    artist = first.artist,
                    songCount = albumSongs.size
                )
            }
            .sortedBy { it.name.lowercase() }
    }

    private fun songsToJsonArray(songs: List<Song>): JSONArray {
        val array = JSONArray()
        songs.forEach { song ->
            array.put(
                JSONObject()
                    .put("id", song.id)
                    .put("title", song.title)
                    .put("artist", song.artist)
                    .put("album", song.album)
                    .put("albumId", song.albumId)
                    .put("duration", song.duration)
                    .put("path", song.path)
                    .put("fileName", song.fileName)
                    .put("fileSize", song.fileSize)
                    .put("mimeType", song.mimeType)
                    .put("dateAdded", song.dateAdded)
                    .put("dateModified", song.dateModified)
                    .put("coverUrl", song.coverUrl)
                    .put("onlineSource", song.onlineSource)
                    .put("onlineId", song.onlineId)
            )
        }
        return array
    }

    private fun albumsToJsonArray(albums: List<Album>): JSONArray {
        val array = JSONArray()
        albums.forEach { album ->
            array.put(
                JSONObject()
                    .put("id", album.id)
                    .put("name", album.name)
                    .put("artist", album.artist)
                    .put("songCount", album.songCount)
                    .put("year", album.year)
            )
        }
        return array
    }

    private fun JSONArray.toSongList(): List<Song> {
        return List(length()) { index ->
            val item = getJSONObject(index)
            Song(
                id = item.getLong("id"),
                title = item.optString("title"),
                artist = item.optString("artist"),
                album = item.optString("album"),
                albumId = item.optLong("albumId"),
                duration = item.optLong("duration"),
                path = item.optString("path"),
                fileName = item.optString("fileName"),
                fileSize = item.optLong("fileSize"),
                mimeType = item.optString("mimeType"),
                dateAdded = item.optLong("dateAdded"),
                dateModified = item.optLong("dateModified"),
                coverUrl = item.optString("coverUrl"),
                onlineSource = item.optString("onlineSource"),
                onlineId = item.optString("onlineId")
            )
        }
    }

    private fun JSONArray.toAlbumList(): List<Album> {
        return List(length()) { index ->
            val item = getJSONObject(index)
            Album(
                id = item.getLong("id"),
                name = item.optString("name"),
                artist = item.optString("artist"),
                songCount = item.optInt("songCount"),
                year = item.optInt("year")
            )
        }
    }
}
