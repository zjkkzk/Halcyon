package com.ella.music.data.repository

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.util.LruCache
import androidx.documentfile.provider.DocumentFile
import com.ella.music.data.exception.WritePermissionRequiredException
import com.ella.music.data.AppLogStore
import com.ella.music.data.AppLogType
import com.ella.music.data.AppNetworkLoggingInterceptor
import com.ella.music.data.LibraryAlbumAggregator
import com.ella.music.data.LibraryNormalizer
import com.ella.music.data.SettingsManager
import com.ella.music.data.isContentAudioSource
import com.ella.music.data.isHttpAudioSource
import com.ella.music.data.isMediaStoreContentAudioSource
import com.ella.music.data.looksLikeNeteaseKeyValue
import com.ella.music.data.model.Album
import com.ella.music.data.model.AudioInfo
import com.ella.music.data.model.LyricLine
import com.ella.music.data.model.Song
import com.ella.music.data.model.SongTagInfo
import com.ella.music.data.model.albumIdentityId
import com.ella.music.data.model.searchableTagValues
import com.ella.music.data.metadata.AudioCoverInfo
import com.ella.music.data.metadata.AudioTagInfo
import com.ella.music.data.metadata.AudioTagRepository
import com.ella.music.data.metadata.LyricoAudioTagReaderWriter
import com.ella.music.data.metadata.WavMetadataReader
import com.ella.music.data.parser.LrcParser
import com.ella.music.data.parser.EllaLyricsParser
import com.ella.music.data.scanner.MediaStoreAudioItem
import com.ella.music.data.scanner.MusicScanner
import com.ella.music.data.webdav.WebDavClient
import com.ella.music.data.webdav.WebDavConfig
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentHashMap

enum class CoverUsage {
    ListThumbnail,
    AlbumGrid,
    Player,
    Notification,
    ShareCard
}

private sealed class CoverDataState {
    data object Found : CoverDataState()
    data object Missing : CoverDataState()
    data class Error(val message: String?) : CoverDataState()
}

private val embeddedArtworkThumbnailExtensions = setOf(
    "m4a",
    "mp4",
    "alac",
    "flac",
    "wav",
    "wave",
    "aif",
    "aiff"
)

class MusicRepository(private val context: Context) {
    companion object {
        @Volatile
        private var instance: MusicRepository? = null

        fun getInstance(context: Context): MusicRepository =
            instance ?: synchronized(this) {
                instance ?: MusicRepository(context.applicationContext).also { instance = it }
            }
    }

    data class LyricFormatAvailability(
        val hasTtml: Boolean = false,
        val hasPlain: Boolean = false
    ) {
        val hasBoth: Boolean get() = hasTtml && hasPlain
    }


    private val scanner = MusicScanner(context)
    private val audioTagRepository = AudioTagRepository(
        primary = LyricoAudioTagReaderWriter()
    )
    private val settingsManager = SettingsManager.getInstance(context)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .addInterceptor(AppNetworkLoggingInterceptor("MusicRepoNetwork"))
        .build()

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    private val _albums = MutableStateFlow<List<Album>>(emptyList())
    val albums: StateFlow<List<Album>> = _albums.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _scanProgress = MutableStateFlow(0)
    val scanProgress: StateFlow<Int> = _scanProgress.asStateFlow()

    private val lyricsCache = mutableMapOf<String, List<LyricLine>>()
    private val lyricFormatAvailabilityCache = mutableMapOf<String, LyricFormatAvailability>()
    private val audioInfoCache = mutableMapOf<Long, AudioInfo>()
    private val tagInfoCache = mutableMapOf<String, SongTagInfo>()
    private val replayGainCache = mutableMapOf<Long, Float?>()
    private val coverArtCache = object : LruCache<String, ByteArray>(8 * 1024) {
        override fun sizeOf(key: String, value: ByteArray): Int = value.size / 1024
    }
    private val coverBitmapCache = object : LruCache<String, Bitmap>(16 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }
    private val coverArtLock = Any()
    private val coverDataStates = ConcurrentHashMap<String, CoverDataState>()
    private val libraryCacheFile = File(context.filesDir, "music_library_cache.json")
    private val librarySearchSnapshotFile = File(context.filesDir, "library_search_snapshot.json")
    private val searchTextCache = ConcurrentHashMap<String, String>()
    @Volatile
    private var searchSnapshotLoaded = false
    @Volatile
    private var searchSnapshotDirty = false
    private val remoteAudioCacheDir = File(context.cacheDir, "webdav_audio")
    private val remoteMetadataHeaderCacheDir = File(context.cacheDir, "webdav_metadata_headers")

    suspend fun scanMusic(
        minDurationMs: Long = 0,
        includeFolders: List<String> = emptyList(),
        excludeFolders: List<String> = emptyList(),
        fullRescan: Boolean = false,
        deepRescan: Boolean = fullRescan
    ): Int {
        _isScanning.value = true
        _scanProgress.value = 0
        try {
            val mode = if (includeFolders.isEmpty()) "media_library" else "custom_folders"
            AppLogStore.info(
                context,
                "MusicScanner",
                "Start scan mode=$mode minDuration=${minDurationMs}ms include=${includeFolders.size} exclude=${excludeFolders.size} fullRescan=$fullRescan deepRescan=$deepRescan",
                AppLogType.LIBRARY
            )
            val scannedSongs = if (fullRescan || deepRescan) {
                scanner.scanAllSongs(
                    minDurationMs = minDurationMs,
                    includeFolders = includeFolders,
                    excludeFolders = excludeFolders,
                    deepMetadata = true
                ) { count -> _scanProgress.value = count }
                    .map { song -> song.withRepositoryTags() }
            } else {
                synchronizeLibrary(
                    minDurationMs = minDurationMs,
                    includeFolders = includeFolders,
                    excludeFolders = excludeFolders
                )
            }
            _songs.value = scannedSongs
            _albums.value = scannedSongs.toAlbums()
            saveLibraryCache(scannedSongs, _albums.value)
            AppLogStore.info(
                context,
                "MusicScanner",
                "Scan finished mode=$mode songs=${scannedSongs.size} albums=${_albums.value.size}",
                AppLogType.LIBRARY
            )
            return scannedSongs.size
        } catch (error: Throwable) {
            AppLogStore.error(
                context,
                "MusicScanner",
                "Scan failed: ${error.message ?: error.javaClass.name}",
                error,
                AppLogType.LIBRARY
            )
            throw error
        } finally {
            _isScanning.value = false
        }
    }

    /**
     * Scan USB folders via SAF and merge the results into the current library.
     */
    suspend fun scanUsbFolders(
        usbUris: List<android.net.Uri>,
        minDurationMs: Long = 0,
        deepMetadata: Boolean = false
    ): Int {
        if (usbUris.isEmpty()) return _songs.value.size
        _isScanning.value = true
        _scanProgress.value = 0
        try {
            val existingSongs = _songs.value
            val existingPaths = existingSongs.map { it.path }.toSet()
            val usbSongs = mutableListOf<Song>()
            for (uri in usbUris) {
                val accessible = scanner.isUsbUriAccessible(uri)
                if (!accessible) {
                    AppLogStore.info(
                        context,
                        "MusicScanner",
                        "USB URI not accessible, skipping: $uri",
                        AppLogType.LIBRARY
                    )
                    continue
                }
                val found = scanner.scanUsbFolder(
                    treeUri = uri,
                    minDurationMs = minDurationMs,
                    deepMetadata = deepMetadata
                ) { count -> _scanProgress.value = count }
                usbSongs.addAll(found.filter { it.path !in existingPaths })
            }
            if (usbSongs.isNotEmpty()) {
                val merged = existingSongs + usbSongs
                _songs.value = merged
                _albums.value = merged.toAlbums()
                saveLibraryCache(merged, _albums.value)
                AppLogStore.info(
                    context,
                    "MusicScanner",
                    "USB scan finished: ${usbSongs.size} new songs from ${usbUris.size} folders, total=${merged.size}",
                    AppLogType.LIBRARY
                )
            }
            return _songs.value.size
        } catch (error: Throwable) {
            AppLogStore.error(
                context,
                "MusicScanner",
                "USB scan failed: ${error.message ?: error.javaClass.name}",
                error,
                AppLogType.LIBRARY
            )
            throw error
        } finally {
            _isScanning.value = false
        }
    }

    private suspend fun synchronizeLibrary(
        minDurationMs: Long,
        includeFolders: List<String>,
        excludeFolders: List<String>
    ): List<Song> = withContext(Dispatchers.IO) {
        val cachedSongs = _songs.value.takeIf { it.isNotEmpty() } ?: readCachedSongs()
        val cachedBySyncKey = cachedSongs.associateBy { it.librarySyncKey() }
        val cachedByPath = cachedSongs.associateBy { it.path }
        val currentItems = scanner.enumerateAudioFiles(
            includeFolders = includeFolders,
            excludeFolders = excludeFolders
        )
        val currentKeys = currentItems.map { it.librarySyncKey() }.toSet()
        val currentPaths = currentItems.map { it.path }.toSet()
        val mergedSongs = ArrayList<Song>(currentItems.size)
        var addedCount = 0
        var updatedCount = 0
        var reusedCount = 0
        var failedCount = 0

        currentItems.forEachIndexed { index, item ->
            val cached = cachedBySyncKey[item.librarySyncKey()] ?: cachedByPath[item.path]
            val mediaStoreSaysTooShort = item.duration > 0L && item.duration < minDurationMs
            if (mediaStoreSaysTooShort) {
                _scanProgress.value = index + 1
                return@forEachIndexed
            }

            val currentInfo = item.toLibrarySyncInfo()
            val cachedInfo = cached?.toLibrarySyncInfo()
            val needsUpdate = cachedInfo == null ||
                cachedInfo.key != currentInfo.key ||
                cachedInfo.path != currentInfo.path ||
                cachedInfo.fileSize != currentInfo.fileSize ||
                cachedInfo.dateModified != currentInfo.dateModified

            if (needsUpdate) {
                val scanned = runCatching {
                    scanner.scanAudioItem(
                        item = item,
                        minDurationMs = minDurationMs,
                        deepMetadata = true
                    )?.withRepositoryTags()
                }.onFailure { error ->
                    failedCount++
                    AppLogStore.warn(
                        context,
                        "MusicScanner",
                        "Incremental item failed path=${item.path}: ${error.message ?: error.javaClass.name}",
                        type = AppLogType.LIBRARY
                    )
                }.getOrNull()

                if (scanned != null) {
                    cached?.let(::clearMetadataCache)
                    clearMetadataCache(scanned)
                    mergedSongs += scanned
                    if (cached == null) addedCount++ else updatedCount++
                } else if (cached != null) {
                    mergedSongs += cached
                }
            } else {
                val reused = cached.copy(
                    albumId = item.albumId,
                    fileName = item.fileName.ifBlank { cached.fileName },
                    mimeType = item.mimeType.ifBlank { cached.mimeType },
                    dateAdded = item.dateAdded.takeIf { it > 0L } ?: cached.dateAdded,
                    trackNumber = item.trackNumber.takeIf { it > 0 } ?: cached.trackNumber,
                    discNumber = item.discNumber.takeIf { it > 0 } ?: cached.discNumber
                ).withRepositoryTags()
                if (reused.duration >= minDurationMs) {
                    mergedSongs += reused
                    if (cached.hasSameLibraryTags(reused)) reusedCount++ else updatedCount++
                }
            }
            _scanProgress.value = index + 1
        }

        val deletedSongs = cachedSongs.filter { song ->
            song.librarySyncKey() !in currentKeys && song.path !in currentPaths
        }
        deletedSongs.forEach(::clearMetadataCache)
        val deletedCount = deletedSongs.size

        AppLogStore.info(
            context,
            "MusicScanner",
            "Incremental scan finished total=${currentItems.size} added=$addedCount updated=$updatedCount reused=$reusedCount deleted=$deletedCount failed=$failedCount",
            AppLogType.LIBRARY
        )
        Log.d(
            "MusicScanner",
            "Incremental scan finished total=${currentItems.size} added=$addedCount updated=$updatedCount reused=$reusedCount deleted=$deletedCount failed=$failedCount"
        )
        mergedSongs
    }

    suspend fun refreshSongAfterExternalEdit(song: Song): Song? = withContext(Dispatchers.IO) {
        if (song.path.isHttpAudioSource()) return@withContext null

        clearMetadataCache(song)
        scanEditedFile(song)
        delay(350)

        val updated = querySystemSong(song) ?: song
        clearMetadataCache(updated)

        val currentSongs = _songs.value
        if (currentSongs.isNotEmpty()) {
            val nextSongs = currentSongs.map { existing ->
                if (existing.id == song.id || existing.path == song.path) updated else existing
            }
            _songs.value = nextSongs
            _albums.value = nextSongs.toAlbums()
            saveLibraryCache(nextSongs, _albums.value)
        }
        updated
    }

    suspend fun loadCachedLibrary() = withContext(Dispatchers.IO) {
        if (!libraryCacheFile.exists()) return@withContext

        runCatching {
            val songs = readLibraryCacheSongs(libraryCacheFile)
            _songs.value = songs
            _albums.value = songs.toAlbums()
        }.onFailure {
            Log.w("MusicRepo", "Failed to load music library cache", it)
        }
    }

    private fun readCachedSongs(): List<Song> {
        if (!libraryCacheFile.exists()) return emptyList()
        return runCatching {
            readLibraryCacheSongs(libraryCacheFile)
        }.getOrElse {
            Log.w("MusicRepo", "Failed to read music library cache for sync", it)
            emptyList()
        }
    }

    suspend fun getLyrics(
        song: Song,
        sourceMode: Int = SettingsManager.LYRIC_SOURCE_AUTO
    ): List<LyricLine> = withContext(Dispatchers.IO) {
        val safeMode = sourceMode.coerceIn(SettingsManager.LYRIC_SOURCE_AUTO, SettingsManager.LYRIC_SOURCE_EMBEDDED)
        val sourcePriority = settingsManager.lyricSourcePriority.first()
        val ignoreSplMetadataLines = settingsManager.ignoreSplMetadataLines.first()
        val cacheKey = "${song.id}:$safeMode:$sourcePriority:spl=$ignoreSplMetadataLines"
        lyricsCache[cacheKey]?.let { return@withContext it }

        Log.d("MusicRepo", "Loading lyrics for: ${song.title} path=${song.path}")

        if (safeMode == SettingsManager.LYRIC_SOURCE_AUTO) {
            fetchOnlineLyrics(song)?.let { onlineLyrics ->
                lyricsCache[cacheKey] = onlineLyrics
                return@withContext onlineLyrics
            }
        }

        val effectivePath = song.effectiveLocalPathForMetadata()
        for (sourceId in orderedLyricSourceIds(sourcePriority, safeMode)) {
            loadLyricsBySourceId(song, effectivePath, sourceId, ignoreSplMetadataLines)?.let { lyrics ->
                lyricsCache[cacheKey] = lyrics
                return@withContext lyrics
            }
        }

        Log.d("MusicRepo", "No lyrics found for ${song.title}")
        lyricsCache[cacheKey] = emptyList()
        emptyList()
    }

    private fun orderedLyricSourceIds(priority: String, sourceMode: Int): List<String> {
        val ordered = SettingsManager.normalizeLyricSourcePriority(priority).split(',')
        return when (sourceMode) {
            SettingsManager.LYRIC_SOURCE_EXTERNAL -> ordered.filter {
                it == SettingsManager.LYRIC_SOURCE_EXTERNAL_TTML ||
                    it == SettingsManager.LYRIC_SOURCE_EXTERNAL_PLAIN
            }
            SettingsManager.LYRIC_SOURCE_EMBEDDED -> ordered.filter {
                it == SettingsManager.LYRIC_SOURCE_EMBEDDED_TTML ||
                    it == SettingsManager.LYRIC_SOURCE_EMBEDDED_PLAIN
            }
            else -> ordered
        }
    }

    private fun loadLyricsBySourceId(
        song: Song,
        effectivePath: String,
        sourceId: String,
        ignoreSplMetadataLines: Boolean
    ): List<LyricLine>? {
        return when (sourceId) {
            SettingsManager.LYRIC_SOURCE_EMBEDDED_TTML ->
                loadEmbeddedLyricsByFormat(song, effectivePath, preferTtml = true, ignoreSplMetadataLines)
            SettingsManager.LYRIC_SOURCE_EMBEDDED_PLAIN ->
                loadEmbeddedLyricsByFormat(song, effectivePath, preferTtml = false, ignoreSplMetadataLines)
            SettingsManager.LYRIC_SOURCE_EXTERNAL_TTML ->
                loadExternalLyricsByFormat(song, effectivePath, preferTtml = true, ignoreSplMetadataLines)
            SettingsManager.LYRIC_SOURCE_EXTERNAL_PLAIN ->
                loadExternalLyricsByFormat(song, effectivePath, preferTtml = false, ignoreSplMetadataLines)
            else -> null
        }
    }

    private fun loadExternalLyricsByFormat(
        song: Song,
        effectivePath: String,
        preferTtml: Boolean,
        ignoreSplMetadataLines: Boolean
    ): List<LyricLine>? {
        val content = findExternalLyricContentByFormat(effectivePath, preferTtml) ?: return null
        val parsed = LrcParser.parse(
            content,
            ignoreSplMetadataLines = ignoreSplMetadataLines
        )
        val lyrics = parsed.lyrics.takeIf { it.isNotEmpty() } ?: return null
        return lyrics.takeIf { lines -> lines.any { it.isTtml } == preferTtml }
            .also { Log.d("MusicRepo", "External lyric format ${if (preferTtml) "TTML" else "LRC/ELRC"} parsed: ${lyrics.size} lines for ${song.title}") }
    }

    private fun loadEmbeddedLyricsByFormat(
        song: Song,
        effectivePath: String,
        preferTtml: Boolean,
        ignoreSplMetadataLines: Boolean
    ): List<LyricLine>? {
        val embedded = audioTagRepository.readTagsBlocking(effectivePath)
            ?.embeddedLyricsContent(preferTtml = preferTtml)
            ?: return null
        val parsed = parseEmbeddedLyrics(song, embedded, ignoreSplMetadataLines) ?: return null
        return parsed.takeIf { lines -> lines.any { it.isTtml } == preferTtml }
    }

    private fun parseEmbeddedLyrics(
        song: Song,
        embedded: String,
        ignoreSplMetadataLines: Boolean
    ): List<LyricLine>? {
        val parsed = LrcParser.parse(embedded, ignoreSplMetadataLines = ignoreSplMetadataLines)
        if (parsed.lyrics.isNotEmpty()) {
            Log.d("MusicRepo", "Embedded lyrics parsed: ${parsed.lyrics.size} lines for ${song.title}")
            return parsed.lyrics
        }

        Log.d("MusicRepo", "Embedded lyrics not synchronized format, using plain text")
        val result = mutableListOf<LyricLine>()
        var timeOffset = 0L
        embedded.lines().forEach { line ->
            val trimmed = line.trim()
            if (ignoreSplMetadataLines && EllaLyricsParser.isSplMetadataLine(trimmed)) return@forEach
            if (trimmed.isNotEmpty()) {
                result.add(LyricLine(timeMs = timeOffset, text = trimmed, words = emptyList()))
                timeOffset += 3000L
            }
        }
        return result.takeIf { it.isNotEmpty() }
    }

    suspend fun reloadLyrics(song: Song, sourceMode: Int): List<LyricLine> = withContext(Dispatchers.IO) {
        val safeMode = sourceMode.coerceIn(SettingsManager.LYRIC_SOURCE_AUTO, SettingsManager.LYRIC_SOURCE_EMBEDDED)
        val sourcePriority = settingsManager.lyricSourcePriority.first()
        val ignoreSplMetadataLines = settingsManager.ignoreSplMetadataLines.first()
        lyricsCache.remove("${song.id}:$safeMode:$sourcePriority:spl=$ignoreSplMetadataLines")
        lyricFormatAvailabilityCache.keys.removeAll { it.startsWith("${song.id}:availability:") }
        getLyrics(song, safeMode)
    }

    suspend fun getLyricFormatAvailability(song: Song): LyricFormatAvailability = withContext(Dispatchers.IO) {
        val ignoreSplMetadataLines = settingsManager.ignoreSplMetadataLines.first()
        val cacheKey = "${song.id}:availability:spl=$ignoreSplMetadataLines"
        lyricFormatAvailabilityCache[cacheKey]?.let { return@withContext it }
        val effectivePath = song.effectiveLocalPathForMetadata()
        val ttml = loadExternalLyricsByFormat(song, effectivePath, preferTtml = true, ignoreSplMetadataLines)
            ?: loadEmbeddedLyricsByFormat(song, effectivePath, preferTtml = true, ignoreSplMetadataLines)
        val plain = loadExternalLyricsByFormat(song, effectivePath, preferTtml = false, ignoreSplMetadataLines)
            ?: loadEmbeddedLyricsByFormat(song, effectivePath, preferTtml = false, ignoreSplMetadataLines)
        LyricFormatAvailability(hasTtml = !ttml.isNullOrEmpty(), hasPlain = !plain.isNullOrEmpty())
            .also { lyricFormatAvailabilityCache[cacheKey] = it }
    }

    suspend fun reloadLyricsByFormat(song: Song, preferTtml: Boolean): List<LyricLine> = withContext(Dispatchers.IO) {
        val sourcePriority = settingsManager.lyricSourcePriority.first()
        val ignoreSplMetadataLines = settingsManager.ignoreSplMetadataLines.first()
        val cacheKey = "${song.id}:format:$preferTtml:$sourcePriority:spl=$ignoreSplMetadataLines"
        lyricsCache.remove(cacheKey)
        lyricFormatAvailabilityCache.keys.removeAll { it.startsWith("${song.id}:availability:") }
        val effectivePath = song.effectiveLocalPathForMetadata()
        val lyrics = orderedLyricSourceIds(sourcePriority, SettingsManager.LYRIC_SOURCE_AUTO)
            .filter { id ->
                if (preferTtml) {
                    id == SettingsManager.LYRIC_SOURCE_EMBEDDED_TTML || id == SettingsManager.LYRIC_SOURCE_EXTERNAL_TTML
                } else {
                    id == SettingsManager.LYRIC_SOURCE_EMBEDDED_PLAIN || id == SettingsManager.LYRIC_SOURCE_EXTERNAL_PLAIN
                }
            }
            .firstNotNullOfOrNull { sourceId -> loadLyricsBySourceId(song, effectivePath, sourceId, ignoreSplMetadataLines) }
            ?: emptyList()
        lyricsCache[cacheKey] = lyrics
        lyrics
    }

    private fun fetchOnlineLyrics(song: Song): List<LyricLine>? {
        if (song.onlineSource != "kw" || song.onlineId.isBlank()) return null
        val request = Request.Builder()
            .url("https://www.kuwo.cn/newh5/singles/songinfoandlrc?musicId=${song.onlineId}")
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 Halcyon/1.0")
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
                rawLines.takeIf { it.isNotEmpty() }
            }
        }.getOrElse {
            Log.w("MusicRepo", "Failed to fetch online lyrics for ${song.title}", it)
            null
        }
    }

    fun getReplayGain(song: Song): Float? {
        replayGainCache[song.id]?.let { return it }
        val gain = scanner.extractReplayGain(song.effectiveLocalPathForMetadata())
        replayGainCache[song.id] = gain
        return gain
    }

    fun getAudioInfo(song: Song): AudioInfo {
        audioInfoCache[song.id]?.let { return it }
        val replayGainDb = getReplayGain(song)
        val metadataPath = song.effectiveLocalPathForMetadata()
        val wavMetadata = WavMetadataReader.read(metadataPath)
        audioTagRepository.readQualityInfoBlocking(metadataPath)?.let { quality ->
            val info = AudioInfo(
                format = song.audioFormatLabel(quality.mimeType),
                bitRate = quality.bitRate.takeIf { it > 0 }
                    ?: wavMetadata?.bitRate?.takeIf { it > 0 }
                    ?: song.estimatedBitRate(),
                sampleRate = quality.sampleRate.takeIf { it > 0 } ?: wavMetadata?.sampleRate ?: 0,
                bitDepth = quality.bitDepth.takeIf { it > 0 } ?: wavMetadata?.bitDepth ?: 0,
                channels = quality.channels.takeIf { it > 0 } ?: wavMetadata?.channels ?: 0,
                replayGainDb = replayGainDb
            )
            audioInfoCache[song.id] = info
            return info
        }
        wavMetadata?.takeIf { it.hasQuality }?.let { quality ->
            val info = AudioInfo(
                format = song.audioFormatLabel("audio/wav"),
                bitRate = quality.bitRate.takeIf { it > 0 } ?: song.estimatedBitRate(),
                sampleRate = quality.sampleRate,
                bitDepth = quality.bitDepth,
                channels = quality.channels,
                replayGainDb = replayGainDb
            )
            audioInfoCache[song.id] = info
            return info
        }
        val info = runCatching {
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(metadataPath)
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
                val formatLabel = song.audioFormatLabel(format?.getString(MediaFormat.KEY_MIME))
                val extractedBitRate = format?.getIntOrZero(MediaFormat.KEY_BIT_RATE) ?: 0
                val bitRate = extractedBitRate.takeIf { it > 0 } ?: song.estimatedBitRate()
                AudioInfo(
                    format = formatLabel,
                    bitRate = bitRate,
                    sampleRate = (format?.getIntOrZero(MediaFormat.KEY_SAMPLE_RATE) ?: 0)
                        .takeIf { it > 0 } ?: wavMetadata?.sampleRate ?: 0,
                    bitDepth = (format?.getIntOrZero("bits-per-sample") ?: 0)
                        .takeIf { it > 0 } ?: wavMetadata?.bitDepth ?: 0,
                    channels = (format?.getIntOrZero(MediaFormat.KEY_CHANNEL_COUNT) ?: 0)
                        .takeIf { it > 0 } ?: wavMetadata?.channels ?: 0,
                    replayGainDb = replayGainDb
                )
            } finally {
                extractor.release()
            }
        }.getOrElse {
            Log.w("MusicRepo", "Failed to read audio info for ${song.path}", it)
            AudioInfo(format = song.audioFormatLabel(null), replayGainDb = replayGainDb)
        }
        audioInfoCache[song.id] = info
        return info
    }

    fun getSongTagInfo(song: Song): SongTagInfo {
        val cacheKey = "${song.id}:${song.dateModified}:${song.fileSize}"
        tagInfoCache[cacheKey]?.let { return it }
        val info = runCatching {
            audioTagRepository.readTagsBlocking(song.effectiveLocalPathForMetadata())?.toSongTagInfo() ?: SongTagInfo()
        }.getOrElse {
            Log.w("MusicRepo", "Failed to read tag info for ${song.path}", it)
            SongTagInfo()
        }
        tagInfoCache[cacheKey] = info
        return info
    }

    suspend fun songMatchesSearchSnapshot(song: Song, query: String): Boolean = withContext(Dispatchers.IO) {
        val normalizedQuery = query.trim().lowercase()
        if (normalizedQuery.isBlank()) return@withContext false
        getSongSearchText(song).contains(normalizedQuery)
    }

    suspend fun filterSongsBySearchSnapshot(songs: List<Song>, query: String): List<Song> = withContext(Dispatchers.IO) {
        val normalizedQuery = query.trim().lowercase()
        if (normalizedQuery.isBlank()) return@withContext songs
        ensureSearchSnapshotLoaded()
        var changed = false
        val matches = songs.filter { song ->
            val key = song.searchSnapshotKey()
            val text = searchTextCache[key] ?: buildSongSearchText(song).also {
                searchTextCache[key] = it
                changed = true
            }
            text.contains(normalizedQuery)
        }
        if (changed) searchSnapshotDirty = true
        matches
    }

    suspend fun getSongSearchText(song: Song): String = withContext(Dispatchers.IO) {
        ensureSearchSnapshotLoaded()
        val key = song.searchSnapshotKey()
        searchTextCache[key]?.let { return@withContext it }
        val text = buildSongSearchText(song)
        searchTextCache[key] = text
        searchSnapshotDirty = true
        text
    }

    suspend fun preloadLibrarySearchSnapshot(songs: List<Song>) = withContext(Dispatchers.IO) {
        if (songs.isEmpty()) return@withContext
        ensureSearchSnapshotLoaded()
        var changed = false
        songs.forEach { song ->
            val key = song.searchSnapshotKey()
            if (!searchTextCache.containsKey(key)) {
                searchTextCache[key] = buildSongSearchText(song)
                changed = true
            }
        }
        if (changed) {
            searchSnapshotDirty = true
        }
        if (searchSnapshotDirty) {
            saveSearchSnapshot()
        }
    }

    private fun buildSongSearchText(song: Song): String =
        song.searchableTagValues(getSongTagInfo(song))
            .joinToString(separator = "\n")
            .lowercase()

    suspend fun clearLibrarySnapshotCache() = withContext(Dispatchers.IO) {
        searchTextCache.clear()
        searchSnapshotLoaded = true
        searchSnapshotDirty = false
        if (librarySearchSnapshotFile.exists()) librarySearchSnapshotFile.delete()
    }

    fun getSongRating(song: Song): Int {
        return getSongTagInfo(song).rating
    }

    suspend fun writeSongRating(song: Song, rating: Int): Result<Song?> = withContext(Dispatchers.IO) {
        val safeRating = rating.coerceIn(0, 5)
        val result = try {
            writeSongTags(
                song,
                AudioTagInfo(rating = safeRating)
            )
        } catch (e: SecurityException) {
            val sender = createWritePermissionIntentSender(song)
                ?: return@withContext Result.failure(e)
            return@withContext Result.failure(WritePermissionRequiredException(sender))
        }
        result.writePermissionRequestIfNeeded(song)?.let { return@withContext it }
        result.map {
            val immediate = updateSongAfterLocalTagWrite(song)
            refreshSongAfterExternalEdit(immediate) ?: immediate
        }
    }

    suspend fun writeSongCustomTag(song: Song, key: String, value: String): Result<Song?> = withContext(Dispatchers.IO) {
        val tagKey = key.trim()
        if (tagKey.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Tag name is blank"))
        }
        val result = try {
            writeSongTags(
                song,
                AudioTagInfo(customTags = mapOf(tagKey to listOf(value)))
            )
        } catch (e: SecurityException) {
            val sender = createWritePermissionIntentSender(song)
                ?: return@withContext Result.failure(e)
            return@withContext Result.failure(WritePermissionRequiredException(sender))
        }
        result.writePermissionRequestIfNeeded(song)?.let { return@withContext it }
        result.map {
            val immediate = updateSongAfterLocalTagWrite(song)
            refreshSongAfterExternalEdit(immediate) ?: immediate
        }
    }

    suspend fun writeSongMetadata(song: Song, tags: AudioTagInfo): Result<Song?> = withContext(Dispatchers.IO) {
        val result = try {
            writeSongTags(song, tags)
        } catch (e: SecurityException) {
            val sender = createWritePermissionIntentSender(song)
                ?: return@withContext Result.failure(e)
            return@withContext Result.failure(WritePermissionRequiredException(sender))
        }
        result.writePermissionRequestIfNeeded(song)?.let { return@withContext it }
        result.map {
            val immediate = updateSongAfterLocalTagWrite(song)
            refreshSongAfterExternalEdit(immediate) ?: immediate
        }
    }

    suspend fun writeSongEmbeddedCover(song: Song, cover: AudioCoverInfo?): Result<Song?> = withContext(Dispatchers.IO) {
        val result = try {
            writeSongCover(song, cover)
        } catch (e: SecurityException) {
            val sender = createWritePermissionIntentSender(song)
                ?: return@withContext Result.failure(e)
            return@withContext Result.failure(WritePermissionRequiredException(sender))
        }
        result.writePermissionRequestIfNeeded(song)?.let { return@withContext it }
        result.map {
            val immediate = updateSongAfterLocalTagWrite(song)
            refreshSongAfterExternalEdit(immediate) ?: immediate
        }
    }

    private suspend fun updateSongAfterLocalTagWrite(song: Song): Song = withContext(Dispatchers.IO) {
        clearMetadataCache(song)
        val updated = song.withCurrentFileSnapshot()
        val currentSongs = _songs.value
        if (currentSongs.isNotEmpty()) {
            val nextSongs = currentSongs.map { existing ->
                if (existing.id == song.id || existing.path == song.path) updated else existing
            }
            _songs.value = nextSongs
            _albums.value = nextSongs.toAlbums()
            saveLibraryCache(nextSongs, _albums.value)
        }
        updated
    }

    private fun Song.withCurrentFileSnapshot(): Song {
        if (path.isHttpAudioSource()) return this
        val file = File(path)
        if (!file.exists()) return copy(dateModified = System.currentTimeMillis())
        return copy(
            fileSize = file.length().takeIf { it > 0L } ?: fileSize,
            dateModified = file.lastModified().takeIf { it > 0L } ?: System.currentTimeMillis()
        )
    }

    private suspend fun writeSongTags(song: Song, tags: AudioTagInfo): Result<Unit> {
        if (song.isWebDavRemoteSong()) {
            return Result.failure(IllegalArgumentException("Online / WebDAV songs are not supported for tag editing"))
        }
        val path = song.effectiveLocalPathForMetadata()
        val writableUri = song.writableAudioUri()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && writableUri != null) {
            val uriResult = runCatching {
                val pfd = context.contentResolver.openFileDescriptor(writableUri, "rw")
                    ?: error("Unable to open audio file for editing")
                pfd.use { descriptor ->
                    audioTagRepository.writeTags(descriptor, tags).getOrThrow()
                }
            }
            if (uriResult.isSuccess) {
                audioTagRepository.clear(path)
                return Result.success(Unit)
            }

            val error = uriResult.exceptionOrNull()
            if (error is SecurityException || error?.isWritePermissionError() == true) {
                return Result.failure(error)
            }
            Log.w("MusicRepo", "MediaStore tag write failed for ${song.path}, falling back to file path", error)
        }
        return audioTagRepository.writeTags(path, tags)
    }

    private suspend fun writeSongCover(song: Song, cover: AudioCoverInfo?): Result<Unit> {
        if (song.isWebDavRemoteSong()) {
            return Result.failure(IllegalArgumentException("Online / WebDAV songs are not supported for cover editing"))
        }
        val path = song.effectiveLocalPathForMetadata()
        val writableUri = song.writableAudioUri()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && writableUri != null) {
            val uriResult = runCatching {
                val pfd = context.contentResolver.openFileDescriptor(writableUri, "rw")
                    ?: error("Unable to open audio file for cover editing")
                pfd.use { descriptor ->
                    if (cover == null) {
                        audioTagRepository.removeEmbeddedCover(descriptor, path).getOrThrow()
                    } else {
                        audioTagRepository.writeEmbeddedCover(descriptor, path, cover).getOrThrow()
                    }
                }
            }
            if (uriResult.isSuccess) {
                clearMetadataCache(song)
                return Result.success(Unit)
            }

            val error = uriResult.exceptionOrNull()
            if (error is SecurityException || error?.isWritePermissionError() == true) {
                return Result.failure(error)
            }
            Log.w("MusicRepo", "MediaStore cover write failed for ${song.path}, falling back to file path", error)
        }
        return if (cover == null) {
            audioTagRepository.removeEmbeddedCover(path)
        } else {
            audioTagRepository.writeEmbeddedCover(path, cover)
        }
    }

    fun getFullAudioTagInfo(song: Song): AudioTagInfo? {
        return runCatching {
            audioTagRepository.readTagsBlocking(song.effectiveLocalPathForMetadata())
        }.getOrNull()
    }

    private fun createWritePermissionIntentSender(song: Song): android.content.IntentSender? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val uri = song.writableAudioUri() ?: return null
        return runCatching {
            MediaStore.createWriteRequest(context.contentResolver, listOf(uri)).intentSender
        }.getOrNull()
    }

    private fun Song.writableAudioUri(): Uri? {
        if (path.isContentAudioSource()) return Uri.parse(path)
        if (id > 0L) return ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
        return null
    }

    private fun Result<Unit>.writePermissionRequestIfNeeded(song: Song): Result<Song?>? {
        val error = exceptionOrNull() ?: return null
        if (!error.isWritePermissionError()) return null
        val sender = createWritePermissionIntentSender(song) ?: return null
        return Result.failure(WritePermissionRequiredException(sender))
    }

    private fun Throwable.isWritePermissionError(): Boolean {
        var current: Throwable? = this
        while (current != null) {
            if (current is SecurityException) return true
            val message = current.message.orEmpty()
            if (
                message.contains("permission", ignoreCase = true) ||
                message.contains("denied", ignoreCase = true) ||
                message.contains("EACCES", ignoreCase = true) ||
                message.contains("EPERM", ignoreCase = true) ||
                message.contains("Operation not permitted", ignoreCase = true)
            ) {
                return true
            }
            current = current.cause
        }
        return false
    }

    private fun Song.estimatedBitRate(): Int {
        if (fileSize <= 0L || duration <= 0L) return 0
        return ((fileSize * 8_000L) / duration).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    fun getCoverArt(song: Song): ByteArray? {
        val cacheKey = song.coverDataCacheKey()
        coverArtCache.get(cacheKey)?.let { return it }
        when (coverDataStates[cacheKey]) {
            CoverDataState.Missing,
            is CoverDataState.Error -> return null
            CoverDataState.Found,
            null -> Unit
        }
        synchronized(coverArtLock) {
            coverArtCache.get(cacheKey)?.let { return it }
            val metadataPath = song.effectiveLocalPathForMetadata()
            val shouldPersistFailureState = !(song.isWebDavRemoteSong() && metadataPath == song.path)
            val art = try {
                if (song.isWebDavRemoteSong() && metadataPath == song.path) {
                    null
                } else {
                    audioTagRepository.readEmbeddedCoverDataBlocking(metadataPath)
                        ?: if (metadataPath.isHttpAudioSource()) {
                            null
                        } else {
                            readEmbeddedPictureWithRetriever(metadataPath)
                        }
                }
            } catch (error: Throwable) {
                if (error is OutOfMemoryError) {
                    coverArtCache.evictAll()
                    coverBitmapCache.evictAll()
                }
                Log.w("MusicRepo", "Failed to extract cover art for ${song.path}", error)
                if (shouldPersistFailureState) {
                    coverDataStates[cacheKey] = CoverDataState.Error(error.message)
                }
                null
            }
            if (art != null) {
                coverArtCache.put(cacheKey, art)
                coverDataStates[cacheKey] = CoverDataState.Found
            } else if (shouldPersistFailureState) {
                coverDataStates.putIfAbsent(cacheKey, CoverDataState.Missing)
            }
            return art
        }
    }

    private fun readEmbeddedPictureWithRetriever(path: String): ByteArray? {
        if (path.isBlank()) return null
        return runCatching {
            val retriever = MediaMetadataRetriever()
            try {
                if (path.isContentAudioSource()) {
                    retriever.setDataSource(context, Uri.parse(path))
                } else {
                    retriever.setDataSource(path)
                }
                retriever.embeddedPicture?.takeIf { it.isNotEmpty() }
            } finally {
                retriever.release()
            }
        }.getOrElse { error ->
            Log.d("MusicRepo", "MediaMetadataRetriever embedded picture unavailable for $path", error)
            null
        }
    }

    fun getCoverArtBitmap(
        song: Song,
        maxSize: Int = 512,
        usage: CoverUsage = CoverUsage.ListThumbnail
    ): Bitmap? {
        val targetSize = maxSize.coerceIn(64, 3000)
        val cacheKey = "${song.coverDataCacheKey()}:${usage.name}:$targetSize"
        coverBitmapCache.get(cacheKey)?.let { return it }
        return synchronized(coverArtLock) {
            coverBitmapCache.get(cacheKey)?.let { return it }
            if (usage == CoverUsage.ListThumbnail) {
                val thumbnailBitmap = decodeExternalThumbnailBitmap(song, targetSize, cacheKey)
                if (thumbnailBitmap != null) {
                    return thumbnailBitmap
                }
            }
            if (usage == CoverUsage.ListThumbnail && !song.prefersEmbeddedArtworkForThumbnail()) {
                val albumBitmap = decodeAlbumArtBitmap(song.albumId, targetSize, usage)
                if (albumBitmap != null) {
                    return albumBitmap
                }
            }
            val data = getCoverArt(song)
            if (data == null) {
                return decodeAlbumArtBitmap(song.albumId, targetSize, usage)
            }
            runCatching {
                val bounds = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

                var sampleSize = 1
                while ((bounds.outWidth / sampleSize) > targetSize || (bounds.outHeight / sampleSize) > targetSize) {
                    sampleSize *= 2
                }

                val options = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize.coerceAtLeast(1)
                    inPreferredConfig = if (usage == CoverUsage.ListThumbnail) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888
                }
                BitmapFactory.decodeByteArray(data, 0, data.size, options)
                    ?.also { coverBitmapCache.put(cacheKey, it) }
            }.getOrElse { error ->
                if (error is OutOfMemoryError) {
                    coverArtCache.evictAll()
                    coverBitmapCache.evictAll()
                }
                Log.w("MusicRepo", "Failed to decode cover bitmap for ${song.path}", error)
                null
            }
        }
    }

    private fun decodeExternalThumbnailBitmap(
        song: Song,
        targetSize: Int,
        cacheKey: String
    ): Bitmap? {
        val thumbnail = song.externalThumbnailCandidates()
            .firstOrNull { it.exists() && it.isFile && it.length() > 0L }
            ?: return null
        return runCatching {
            decodeBitmapFile(thumbnail, targetSize, Bitmap.Config.RGB_565)
                ?.also { coverBitmapCache.put(cacheKey, it) }
        }.getOrElse { error ->
            Log.d("MusicRepo", "Failed to decode external thumbnail ${thumbnail.absolutePath}", error)
            null
        }
    }

    private fun decodeBitmapFile(
        file: File,
        targetSize: Int,
        preferredConfig: Bitmap.Config
    ): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sampleSize = 1
        while ((bounds.outWidth / sampleSize) > targetSize || (bounds.outHeight / sampleSize) > targetSize) {
            sampleSize *= 2
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize.coerceAtLeast(1)
            inPreferredConfig = preferredConfig
        }
        return BitmapFactory.decodeFile(file.absolutePath, options)
    }

    private fun decodeAlbumArtBitmap(
        albumId: Long,
        targetSize: Int,
        usage: CoverUsage
    ): Bitmap? {
        if (albumId <= 0L) return null
        val albumCacheKey = "album:$albumId:${usage.name}:$targetSize"
        coverBitmapCache.get(albumCacheKey)?.let { return it }
        val albumArtUri = getAlbumArtUri(albumId) ?: return null
        return runCatching {
            val bounds = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(albumArtUri)?.use { input ->
                BitmapFactory.decodeStream(input, null, bounds)
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            var sampleSize = 1
            while ((bounds.outWidth / sampleSize) > targetSize || (bounds.outHeight / sampleSize) > targetSize) {
                sampleSize *= 2
            }

            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSize.coerceAtLeast(1)
                inPreferredConfig = if (usage == CoverUsage.ListThumbnail) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888
            }
            context.contentResolver.openInputStream(albumArtUri)?.use { input ->
                BitmapFactory.decodeStream(input, null, options)
            }?.also { coverBitmapCache.put(albumCacheKey, it) }
        }.getOrElse { error ->
            if (error is OutOfMemoryError) {
                coverArtCache.evictAll()
                coverBitmapCache.evictAll()
            }
            Log.d("MusicRepo", "Failed to decode album art bitmap for albumId=$albumId", error)
            null
        }
    }

    private fun Song.prefersEmbeddedArtworkForThumbnail(): Boolean =
        fileName.substringAfterLast('.', path.substringAfterLast('.'))
            .lowercase() in embeddedArtworkThumbnailExtensions

    private fun Song.externalThumbnailCandidates(): List<File> {
        val metadataPath = effectiveLocalPathForMetadata()
        val songFile = File(metadataPath)
        if (!songFile.isFile) return emptyList()
        val fileNameBase = fileName.ifBlank { songFile.name }
        val stem = fileNameBase.substringBeforeLast('.').ifBlank { songFile.nameWithoutExtension }
        val directories = buildList {
            songFile.parentFile?.let { add(File(it, ".thumbnails")) }
            add(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), ".thumbnails"))
        }.distinctBy { it.absolutePath }
        val keys = listOf(
            stem,
            fileNameBase,
            id.takeIf { it > 0L }?.toString().orEmpty(),
            albumId.takeIf { it > 0L }?.toString().orEmpty(),
            path.sha256()
        ).filter { it.isNotBlank() }.distinct()
        val extensions = listOf("jpg", "jpeg", "png", "webp")
        return directories.flatMap { dir ->
            keys.flatMap { key ->
                extensions.map { ext -> File(dir, "$key.$ext") }
            }
        }
    }

    fun getAlbumArtUri(albumId: Long): Uri? {
        if (albumId <= 0L) return null
        return ContentUris.withAppendedId(
            Uri.parse("content://media/external/audio/albumart"),
            albumId
        )
    }

    fun getSongsForAlbum(albumId: Long): List<Song> {
        return _songs.value
            .filter { it.albumIdentityId() == albumId }
            .sortedWith(
                compareBy<Song> { it.discNumber <= 0 && it.trackNumber <= 0 }
                    .thenBy { if (it.discNumber > 0) it.discNumber else Int.MAX_VALUE }
                    .thenBy { if (it.trackNumber > 0) it.trackNumber else Int.MAX_VALUE }
                    .thenBy { it.title.lowercase() }
                    .thenBy { it.id }
            )
    }

    suspend fun deleteSongs(songs: Collection<Song>): Int = withContext(Dispatchers.IO) {
        var deleted = 0
        val deletedSongs = mutableListOf<Song>()
        val mediaStoreUrisNeedingPermission = mutableListOf<Uri>()

        songs.forEach { song ->
            if (tryDeleteSongDirect(song)) {
                deleted++
                deletedSongs += song
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                song.mediaStoreDeleteUriOrNull()?.let { mediaStoreUrisNeedingPermission += it }
            }
        }

        if (deletedSongs.isNotEmpty()) {
            removeDeletedSongsFromState(deletedSongs)
        }

        if (mediaStoreUrisNeedingPermission.isNotEmpty()) {
            val request = MediaStore.createDeleteRequest(context.contentResolver, mediaStoreUrisNeedingPermission.distinct())
            throw WritePermissionRequiredException(request.intentSender)
        }

        deleted
    }

    private fun tryDeleteSongDirect(song: Song): Boolean {
        if (song.onlineSource.isNotBlank()) return false
        val path = song.path.trim()
        if (path.isContentAudioSource()) {
            val uri = Uri.parse(path)
            val documentDeleted = runCatching {
                DocumentFile.fromSingleUri(context, uri)?.delete() == true
            }.getOrDefault(false)
            if (documentDeleted) return true
            return runCatching { context.contentResolver.delete(uri, null, null) > 0 }.getOrDefault(false)
        }

        val fileDeleted = runCatching {
            val file = File(path)
            file.exists() && file.delete()
        }.getOrDefault(false)
        if (fileDeleted) return true

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R && song.id > 0L) {
            return runCatching {
                val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, song.id)
                context.contentResolver.delete(uri, null, null) > 0
            }.getOrDefault(false)
        }

        return false
    }

    private fun Song.mediaStoreDeleteUriOrNull(): Uri? {
        if (onlineSource.isNotBlank() || id <= 0L) return null
        if (path.isContentAudioSource() && !path.isMediaStoreContentAudioSource()) {
            return null
        }
        return ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
    }

    private suspend fun removeDeletedSongsFromState(deletedSongs: Collection<Song>) {
        val deletedKeys = deletedSongs.map { it.deleteIdentityKey() }.toSet()
        val deletedIds = deletedSongs.map { it.id }.filter { it > 0L }.toSet()
        _songs.value = _songs.value.filterNot { song ->
            (song.id > 0L && song.id in deletedIds) || song.deleteIdentityKey() in deletedKeys
        }
        _albums.value = _songs.value.toAlbums()
        saveLibraryCache(_songs.value, _albums.value)
    }

    private fun Song.deleteIdentityKey(): String = "$id|$path"

    suspend fun removeSongsFromLibrary(songs: Collection<Song>): Unit = withContext(Dispatchers.IO) {
        if (songs.isEmpty()) return@withContext
        removeDeletedSongsFromState(songs)
        Unit
    }

    fun clearCache() {
        lyricsCache.clear()
        lyricFormatAvailabilityCache.clear()
        audioInfoCache.clear()
        tagInfoCache.clear()
        replayGainCache.clear()
        coverArtCache.evictAll()
        coverBitmapCache.evictAll()
        coverDataStates.clear()
    }

    fun clearMetadataCache(song: Song) {
        lyricsCache.keys.removeAll { it.startsWith("${song.id}:") }
        lyricFormatAvailabilityCache.keys.removeAll { it.startsWith("${song.id}:") }
        audioInfoCache.remove(song.id)
        tagInfoCache.keys.removeAll { it.startsWith("${song.id}:") }
        replayGainCache.remove(song.id)
        ensureSearchSnapshotLoaded()
        searchTextCache.keys.removeAll { it == song.searchSnapshotKey() || it.startsWith("${song.id}|") }
        saveSearchSnapshot()
        audioTagRepository.clear(song.effectiveLocalPathForMetadata())
        if (song.isWebDavRemoteSong()) {
            song.webDavHeaderCacheFile().delete()
            song.webDavFullCacheFile().delete()
        }
        val keyPrefix = song.coverCacheKey()
        coverDataStates.keys.removeAll { it.startsWith(keyPrefix) }
        coverArtCache.remove(song.coverDataCacheKey())
        val bitmapKeyPrefix = "${song.coverDataCacheKey()}:"
        val bitmapKeys = mutableListOf<String>()
        synchronized(coverArtLock) {
            for (key in coverBitmapCache.snapshot().keys) {
                if (key.startsWith(bitmapKeyPrefix)) bitmapKeys += key
            }
            bitmapKeys.forEach(coverBitmapCache::remove)
        }
    }

    fun clearRemoteMetadataCache() {
        clearCache()
        runCatching {
            if (remoteAudioCacheDir.exists()) {
                remoteAudioCacheDir.deleteRecursively()
            }
            if (remoteMetadataHeaderCacheDir.exists()) {
                remoteMetadataHeaderCacheDir.deleteRecursively()
            }
        }.onFailure {
            Log.w("MusicRepo", "Failed to clear online metadata cache", it)
        }
    }

    suspend fun resolveSongForPlayback(song: Song): Song = withContext(Dispatchers.IO) {
        runCatching {
            song.withRepositoryTags(allowFullDownload = song.isWebDavRemoteSong() && song.isLikelyWavAudio())
        }.getOrElse { error ->
            Log.w("MusicRepo", "Failed to resolve playback song for ${song.path}", error)
            song
        }
    }

    suspend fun prefetchWebDavMetadataHeaders(songs: List<Song>, maxItems: Int = 80) = supervisorScope {
        val targets = songs
            .asSequence()
            .filter { it.isWebDavRemoteSong() }
            .distinctBy { it.path }
            .take(maxItems.coerceIn(1, 100))
            .toList()
        if (targets.isEmpty()) return@supervisorScope
        val config = loadWebDavConfig() ?: return@supervisorScope
        val semaphore = Semaphore(3)
        targets.forEach { song ->
            launch(Dispatchers.IO) {
                runCatching {
                    semaphore.withPermit {
                        val headerFile = song.webDavHeaderCacheFile()
                        if (headerFile.exists() && headerFile.length() > 0L) {
                            Log.d("MusicRepo", "WebDAV header prefetch hit cache url=${song.path.webDavSafeLogUrl()}")
                            return@withPermit
                        }
                        Log.d("MusicRepo", "WebDAV header prefetch start url=${song.path.webDavSafeLogUrl()}")
                        val cached = downloadWebDavMetadataHeader(song, config)
                        if (cached != null) {
                            Log.d("MusicRepo", "WebDAV header prefetch success url=${song.path.webDavSafeLogUrl()} bytes=${headerFile.length()}")
                        } else {
                            Log.d("MusicRepo", "WebDAV header prefetch skipped url=${song.path.webDavSafeLogUrl()}")
                        }
                    }
                }.onFailure { error ->
                    AppLogStore.warn(
                        context,
                        "MusicRepoWebDav",
                        "WebDAV header prefetch failed url=${song.path.webDavSafeLogUrl()}",
                        error,
                        AppLogType.NETWORK
                    )
                }
            }
        }
    }

    private fun Song.effectiveLocalPathForMetadata(allowFullDownload: Boolean = false): String {
        if (path.isContentAudioSource()) return path
        if (!isWebDavRemoteSong()) return path
        val fullCache = webDavFullCacheFile()
        if (fullCache.exists() && fullCache.length() > 0L) return fullCache.absolutePath
        val headerCache = webDavHeaderCacheFile()
        if (headerCache.exists() && headerCache.length() > 0L) return headerCache.absolutePath
        val config = runBlocking(Dispatchers.IO) { loadWebDavConfig() } ?: return path
        downloadWebDavMetadataHeader(this, config)?.let { return it.absolutePath }
        if (!allowFullDownload) return path
        return runCatching {
            WebDavClient.downloadToFile(path, config, fullCache).absolutePath
        }.getOrElse {
            Log.w("MusicRepo", "Failed to cache remote metadata file for $path", it)
            path
        }
    }

    private fun String.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private suspend fun loadWebDavConfig(): WebDavConfig? {
        val url = settingsManager.webDavUrl.first().trim()
        if (url.isBlank()) return null
        return WebDavConfig(
            url = url,
            username = settingsManager.webDavUsername.first(),
            password = settingsManager.webDavPassword.first()
        )
    }

    private fun Song.isWebDavRemoteSong(): Boolean =
        path.isHttpAudioSource() &&
            onlineSource.isBlank()

    private fun Song.webDavCacheExtension(): String =
        fileName.substringAfterLast('.', path.substringBefore('?').substringBefore('#').substringAfterLast('.', "audio"))
            .ifBlank { "audio" }

    private fun Song.isLikelyWavAudio(): Boolean =
        webDavCacheExtension().lowercase() in setOf("wav", "wave") ||
            mimeType.contains("wav", ignoreCase = true) ||
            mimeType.contains("wave", ignoreCase = true)

    private fun Song.webDavFullCacheFile(): File =
        File(remoteAudioCacheDir, "${path.sha256()}.${webDavCacheExtension()}")

    private fun Song.webDavHeaderCacheFile(): File =
        File(remoteMetadataHeaderCacheDir, "${path.sha256()}.${webDavCacheExtension()}")

    private fun downloadWebDavMetadataHeader(song: Song, config: WebDavConfig): File? {
        val target = song.webDavHeaderCacheFile()
        if (target.exists() && target.length() > 0L) return target
        return WebDavClient.downloadHeaderToFile(song.path, config, target)
    }

    private fun String.webDavSafeLogUrl(): String =
        runCatching {
            val uri = java.net.URI(this)
            if (uri.userInfo == null) {
                this
            } else {
                java.net.URI(uri.scheme, "***", uri.host, uri.port, uri.path, uri.query, uri.fragment).toString()
            }
        }.getOrDefault(this)

    private fun MediaFormat.getIntOrZero(key: String): Int {
        return if (containsKey(key)) runCatching { getInteger(key) }.getOrDefault(0) else 0
    }

    private fun Song.audioFormatLabel(mime: String?): String {
        val source = (mime ?: mimeType).lowercase()
        val extensionSource = fileName.takeIf { it.substringAfterLast('.', "").isNotBlank() }
            ?: path.substringBefore('?').substringBefore('#')
        val extension = extensionSource.substringAfterLast('.', "").lowercase()
        return when {
            "flac" in source || extension == "flac" -> "FLAC"
            "mpeg" in source || "mp3" in source || extension == "mp3" -> "MP3"
            "wav" in source || extension == "wav" -> "WAV"
            "eac3" in source || "e-ac-3" in source || "ec-3" in source || extension == "ec3" || extension == "eac3" -> "EC3"
            "ac3" in source || "ac-3" in source || extension == "ac3" -> "AC3"
            "aac" in source || extension == "aac" -> "AAC"
            "alac" in source || "audio/alac" in source -> "ALAC"
            extension == "m4a" && estimatedBitRate() >= 700_000 -> "ALAC"
            extension == "m4a" -> "AAC"
            "mp4" in source || "m4a" in source || extension == "m4a" || extension == "mp4" -> "M4A"
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
                .put("songs", songsToLibraryCacheJsonArray(songs))
                .put("albums", albumsToLibraryCacheJsonArray(albums))
            libraryCacheFile.writeText(root.toString())
        }.onFailure {
            Log.w("MusicRepo", "Failed to save music library cache", it)
        }
    }

    private fun List<Song>.toAlbums(): List<Album> {
        return LibraryAlbumAggregator.toAlbums(this)
    }

    private fun Song.withRepositoryTags(allowFullDownload: Boolean = false): Song {
        val metadataPath = effectiveLocalPathForMetadata(allowFullDownload)
        val tagInfo = runCatching {
            audioTagRepository.readTagsBlocking(metadataPath)
        }.getOrElse { error ->
            Log.w("MusicRepo", "Failed to refresh library tags for $path", error)
            null
        }
        val wavMetadata = runCatching { WavMetadataReader.read(metadataPath) }
            .getOrNull()

        val mergedArtist = tagInfo?.artist.takeIf { it.isUsableTagText() }
            ?: wavMetadata?.artist.takeIf { it.isUsableTagText() }
            ?: artist.takeIf { it.isUsableTagText() }
            ?: "Unknown Artist"
        val mergedAlbum = tagInfo?.album.takeIf { it.isUsableAlbumText() }
            ?: wavMetadata?.album.takeIf { it.isUsableAlbumText() }
            ?: album.takeIf { it.isUsableAlbumText() && !it.looksLikeLastFolderName(path) }
            ?: "Unknown Album"
        val mergedAlbumArtist = tagInfo?.albumArtist.takeIf { it.isUsableTagText() }
            ?: wavMetadata?.albumArtist.takeIf { it.isUsableTagText() }
            ?: albumArtist.takeIf { it.isUsableTagText() }
            ?: ""

        return copy(
            title = tagInfo?.title.takeIf { it.isUsableTagText() }
                ?: wavMetadata?.title.takeIf { it.isUsableTagText() }
                ?: title.takeIf { it.isUsableTagText() }
                ?: fileName.substringBeforeLast('.').ifBlank { path.substringAfterLast('/') },
            artist = mergedArtist,
            album = mergedAlbum,
            albumArtist = mergedAlbumArtist,
            genre = tagInfo?.genre.takeIf { it.isUsableTagText() } ?: wavMetadata?.genre.takeIf { it.isUsableTagText() } ?: genre,
            year = tagInfo?.year.takeIf { it.isUsableTagText() } ?: wavMetadata?.year.takeIf { it.isUsableTagText() } ?: year,
            composer = tagInfo?.composer.takeIf { it.isUsableTagText() } ?: wavMetadata?.composer.takeIf { it.isUsableTagText() } ?: composer,
            lyricist = tagInfo?.lyricist.takeIf { it.isUsableTagText() } ?: wavMetadata?.lyricist.takeIf { it.isUsableTagText() } ?: lyricist,
            trackNumber = tagInfo?.trackNumber ?: wavMetadata?.trackNumber ?: trackNumber,
            discNumber = tagInfo?.discNumber ?: wavMetadata?.discNumber ?: discNumber
        ).withFinalLibraryFallbacks()
    }

    private fun Song.withFinalLibraryFallbacks(): Song {
        val fallbackArtist = artist.takeIf { it.isUsableTagText() } ?: "Unknown Artist"
        val fallbackAlbum = album.takeIf { it.isUsableAlbumText() && !it.looksLikeLastFolderName(path) }
            ?: "Unknown Album"
        return copy(
            title = title.takeIf { it.isUsableTagText() } ?: fileName.substringBeforeLast('.').ifBlank { path.substringAfterLast('/') },
            artist = fallbackArtist,
            album = fallbackAlbum,
            albumArtist = albumArtist.takeIf { it.isUsableTagText() }.orEmpty()
        )
    }

    private fun Song.hasSameLibraryTags(other: Song): Boolean =
        title == other.title &&
            artist == other.artist &&
            album == other.album &&
            albumArtist == other.albumArtist &&
            genre == other.genre &&
            year == other.year &&
            composer == other.composer &&
            lyricist == other.lyricist &&
            trackNumber == other.trackNumber &&
            discNumber == other.discNumber

    private suspend fun scanEditedFile(song: Song) = suspendCoroutine<Unit> { continuation ->
        val path = song.path.takeIf { it.isNotBlank() }
        if (path == null || path.isContentAudioSource()) {
            continuation.resume(Unit)
            return@suspendCoroutine
        }
        val mimeTypes = song.mimeType.takeIf { it.isNotBlank() }?.let { arrayOf(it) }
        MediaScannerConnection.scanFile(
            context,
            arrayOf(path),
            mimeTypes,
        ) { _, _ ->
            continuation.resume(Unit)
        }
    }

    private fun querySystemSong(song: Song): Song? {
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.TRACK
        )
        val uri = if (song.id > 0L) {
            ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, song.id)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        val selection = if (song.id > 0L) null else "${MediaStore.Audio.Media.DATA} = ?"
        val selectionArgs = if (song.id > 0L) null else arrayOf(song.path)

        return context.contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val tagInfo = runCatching {
                audioTagRepository.readTagsBlocking(song.effectiveLocalPathForMetadata())?.toSongTagInfo()
            }.getOrNull() ?: SongTagInfo()
            val wavInfo = runCatching { WavMetadataReader.read(song.effectiveLocalPathForMetadata()) }
                .getOrNull()
            Song(
                id = cursor.getLong(0),
                title = tagInfo.title.usableTagText().ifBlank {
                    wavInfo?.title.usableTagText().ifBlank {
                        cursor.getString(1)?.usableTagText().orEmpty().ifBlank { song.title }
                    }
                },
                artist = tagInfo.artist.usableTagText().ifBlank {
                    wavInfo?.artist.usableTagText().ifBlank {
                        cursor.getString(2)?.usableTagText().orEmpty().ifBlank { song.artist }
                    }
                },
                album = tagInfo.album.usableTagText().ifBlank {
                    wavInfo?.album.usableTagText().ifBlank {
                        cursor.getString(3)?.usableTagText().orEmpty().ifBlank { song.album }
                    }
                },
                albumId = cursor.getLong(4),
                duration = cursor.getLong(5).takeIf { it > 0L } ?: song.duration,
                path = cursor.getString(6).orEmpty().ifBlank { song.path },
                fileName = cursor.getString(7).orEmpty().ifBlank { song.fileName },
                fileSize = cursor.getLong(8),
                mimeType = cursor.getString(9).orEmpty().ifBlank { song.mimeType },
                dateAdded = cursor.getLong(10) * 1000L,
                dateModified = cursor.getLong(11) * 1000L,
                trackNumber = tagInfo.track.takeIf { it.isNotBlank() }?.toIntOrNull()
                    ?: wavInfo?.trackNumber
                    ?: cursor.getInt(12).let { if (it > 1000) it % 1000 else it },
                discNumber = wavInfo?.discNumber
                    ?: cursor.getInt(12).let { if (it >= 1000) it / 1000 else song.discNumber },
                albumArtist = tagInfo.albumArtist.ifBlank { wavInfo?.albumArtist.orEmpty().ifBlank { song.albumArtist } },
                genre = tagInfo.genre.ifBlank { wavInfo?.genre.orEmpty().ifBlank { song.genre } },
                year = tagInfo.year.ifBlank { wavInfo?.year.orEmpty().ifBlank { song.year } },
                composer = tagInfo.composer.ifBlank { wavInfo?.composer.orEmpty().ifBlank { song.composer } },
                lyricist = tagInfo.lyricist.ifBlank { wavInfo?.lyricist.orEmpty().ifBlank { song.lyricist } },
                coverUrl = song.coverUrl,
                onlineSource = song.onlineSource,
                onlineId = song.onlineId,
                onlineLyrics = song.onlineLyrics,
                onlineLyricTranslation = song.onlineLyricTranslation
            )
        }
    }

    private fun Song.coverCacheKey(): String {
        val source = when {
            path.isNotBlank() -> path
            onlineSource.isNotBlank() || onlineId.isNotBlank() -> "$onlineSource:$onlineId"
            else -> "$id:$title:$artist:$album"
        }
        return source.sha256()
    }

    private fun Song.coverDataCacheKey(): String =
        "${coverCacheKey()}:$dateModified:$fileSize"

    private fun Song.searchSnapshotKey(): String =
        "${id}|${path.sha256()}"

    private fun AudioTagInfo.embeddedLyricsContent(preferTtml: Boolean): String? {
        val names = if (preferTtml) {
            listOf("TTML LYRICS", "TTML LYRIC", "TTMLLYRICS", "TTMLLYRIC", "TTML")
        } else {
            listOf("SPL LYRICS", "SPLLYRICS", "SYNCEDLYRICS", "UNSYNCEDLYRICS", "UNSYNCED LYRICS", "LYRICS", "USLT", "SYLT", "LYRIC")
        }
        names.forEach { target ->
            customTags.firstMatchingTagValue(target)?.let { return it }
        }
        return lyrics?.takeIf { it.isNotBlank() && (preferTtml == it.contains("<tt", ignoreCase = true)) }
    }

    private fun Map<String, List<String>>.firstMatchingTagValue(target: String): String? {
        val normalizedTarget = target.normalizedTagName()
        return entries.firstOrNull { (key, values) ->
            key.normalizedTagName() == normalizedTarget && values.any { it.isNotBlank() }
        }?.value?.firstOrNull { it.isNotBlank() }
    }

    private fun String.normalizedTagName(): String =
        uppercase().filter { it.isLetterOrDigit() }

    private fun findExternalLyricContentByFormat(songPath: String, preferTtml: Boolean): String? {
        val extensions = if (preferTtml) listOf("ttml") else listOf("lrc", "elrc", "spl")
        val baseName = songPath.substringBeforeLast('.')
        extensions.forEach { ext ->
            readTextIfExists("$baseName.$ext")?.let { return it }
        }

        val parentDir = File(songPath).parentFile ?: return null
        val songName = File(songPath).nameWithoutExtension
        return runCatching {
            parentDir.listFiles()
                ?.filter { file -> extensions.any { file.extension.equals(it, ignoreCase = true) } }
                ?.sortedWith(compareBy<File> { extensions.indexOf(it.extension.lowercase()) }.thenBy { it.name })
                ?.firstOrNull { it.nameWithoutExtension.contains(songName, ignoreCase = true) }
                ?.let { readTextIfExists(it.absolutePath) }
        }.getOrNull()
    }

    private fun readTextIfExists(path: String): String? =
        runCatching {
            val file = File(path)
            if (!file.exists()) return null
            file.readText()
        }.getOrNull()

    private fun ensureSearchSnapshotLoaded() {
        if (searchSnapshotLoaded) return
        synchronized(searchTextCache) {
            if (searchSnapshotLoaded) return
            if (librarySearchSnapshotFile.exists()) {
                runCatching {
                    val root = JSONObject(librarySearchSnapshotFile.readText())
                    root.keys().forEach { key ->
                        val value = root.optString(key)
                        val parts = key.split('|')
                        val stableKey = if (parts.size >= 2) "${parts[0]}|${parts[1]}" else key
                        searchTextCache[stableKey] = value
                    }
                }.onFailure {
                    Log.w("MusicRepo", "Failed to load library search snapshot", it)
                    searchTextCache.clear()
                }
            }
            searchSnapshotLoaded = true
        }
    }

    private fun saveSearchSnapshot() {
        if (!searchSnapshotDirty) return
        runCatching {
            val root = JSONObject()
            searchTextCache.forEach { (key, value) -> root.put(key, value) }
            librarySearchSnapshotFile.writeText(root.toString())
            searchSnapshotDirty = false
        }.onFailure {
            Log.w("MusicRepo", "Failed to save library search snapshot", it)
        }
    }

    private fun Song.librarySyncKey(): String =
        if (id > 0L) {
            ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id).toString()
        } else {
            path
        }

    private fun MediaStoreAudioItem.librarySyncKey(): String =
        if (id > 0L) {
            ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id).toString()
        } else {
            path
        }

    private fun Song.toLibrarySyncInfo(): LibrarySyncInfo =
        LibrarySyncInfo(
            key = librarySyncKey(),
            path = path,
            fileSize = fileSize,
            dateModified = dateModified
        )

    private fun MediaStoreAudioItem.toLibrarySyncInfo(): LibrarySyncInfo =
        LibrarySyncInfo(
            key = librarySyncKey(),
            path = path,
            fileSize = fileSize,
            dateModified = dateModified
        )

    private fun String.extractYearInt(): Int? =
        Regex("""\d{4}""").find(this)?.value?.toIntOrNull()

    private fun String?.usableTagText(): String {
        return LibraryNormalizer.cleanedTagText(this)
    }

    private fun String?.isUsableTagText(): Boolean =
        usableTagText().isNotBlank()

    private fun String?.isUsableAlbumText(): Boolean {
        return usableTagText().isNotBlank()
    }

    private fun String.looksLikeLastFolderName(path: String): Boolean {
        return LibraryNormalizer.looksLikeLastFolderName(this, path)
    }

    private fun AudioTagInfo.toSongTagInfo(): SongTagInfo =
        SongTagInfo(
            title = title.orEmpty(),
            artist = artist.orEmpty(),
            album = album.orEmpty(),
            albumArtist = albumArtist.orEmpty(),
            genre = genre.orEmpty(),
            year = year.orEmpty(),
            composer = composer.orEmpty(),
            lyricist = lyricist.orEmpty(),
            track = trackNumber?.toString().orEmpty(),
            comment = comment.orEmpty(),
            copyright = copyright.orEmpty(),
            neteaseKey = neteaseKey.orEmpty(),
            rating = rating.normalizeTagRatingToStars(),
            customTagText = customTags.flattenForSearch()
        )

    private fun Map<String, List<String>>.flattenForSearch(): String =
        entries.asSequence()
            .filterNot { (key, _) -> key.isIgnoredSearchTagKey() }
            .flatMap { (key, values) ->
                sequence {
                    yield(key)
                    values.forEach { value ->
                        val text = value.trim()
                        if (text.isNotBlank() && !text.looksLikeNeteaseKeyValue()) yield(text)
                    }
                }
            }
            .distinct()
            .take(80)
            .joinToString(" ")

    private fun Int?.normalizeTagRatingToStars(): Int {
        val raw = this ?: return 0
        return when {
            raw <= 0 -> 0
            raw <= 5 -> raw
            raw <= 100 -> kotlin.math.round(raw / 20f).toInt()
            raw <= 255 -> kotlin.math.round(raw / 255f * 5f).toInt()
            else -> 0
        }.coerceIn(0, 5)
    }

    private fun String.isIgnoredSearchTagKey(): Boolean {
        val normalized = trim().lowercase()
        return normalized in setOf(
            "apic",
            "covr",
            "picture",
            "metadata_block_picture",
            "unsyncedlyrics",
            "uslt",
            "lyrics",
            "lyric",
            "syncedlyrics",
            "replaygain_track_gain",
            "replaygain_track_peak",
            "replaygain_album_gain",
            "replaygain_album_peak",
            "replaygain_reference_loudness"
        )
    }

    private data class LibrarySyncInfo(
        val key: String,
        val path: String,
        val fileSize: Long,
        val dateModified: Long
    )
}
