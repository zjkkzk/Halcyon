package com.ella.music.ui.home

import android.icu.text.Transliterator
import com.ella.music.R
import com.ella.music.data.model.Song
import com.ella.music.ui.components.toFastIndexSection
import com.ella.music.ui.components.toFastIndexSortableKey
import java.io.File
import java.util.Locale
import org.json.JSONObject

internal enum class HomeSortMode(val labelRes: Int) {
    Title(R.string.playlist_song_sort_title),
    FileName(R.string.playlist_song_sort_file_name),
    DateAdded(R.string.playlist_song_sort_date_added),
    DateAddedAsc(R.string.playlist_song_sort_date_added_asc),
    DateModified(R.string.playlist_song_sort_date_modified),
    DateModifiedAsc(R.string.playlist_song_sort_date_modified_asc),
    YearAsc(R.string.playlist_song_sort_year_asc),
    YearDesc(R.string.playlist_song_sort_year_desc)
}

internal fun List<Song>.sortedForHomeMode(sortMode: HomeSortMode): HomeSortedSongs =
    when (sortMode) {
        HomeSortMode.Title -> sortedByMusicKey { it.title }
        HomeSortMode.FileName -> sortedByMusicKey { it.fileName.ifBlank { it.path.substringAfterLast('/') } }
        HomeSortMode.YearAsc -> HomeSortedSongs(sortedByReleaseDate(ascending = true), emptyMap())
        HomeSortMode.YearDesc -> HomeSortedSongs(sortedByReleaseDate(ascending = false), emptyMap())
        HomeSortMode.DateAdded -> HomeSortedSongs(sortedByDescending { it.dateAdded }, emptyMap())
        HomeSortMode.DateAddedAsc -> HomeSortedSongs(sortedBy { it.dateAdded }, emptyMap())
        HomeSortMode.DateModified -> HomeSortedSongs(sortedByDescending { it.dateModified }, emptyMap())
        HomeSortMode.DateModifiedAsc -> HomeSortedSongs(sortedBy { it.dateModified }, emptyMap())
    }

internal fun List<Song>.cachedSortedForHomeMode(sortMode: HomeSortMode): HomeSortedSongs =
    HomeSortResultCache.getOrPut(this, sortMode) { sortedForHomeMode(sortMode) }

private fun List<Song>.sortedByReleaseDate(ascending: Boolean): List<Song> {
    val comparator = if (ascending) {
        compareBy<Song> { it.releaseYearOrNull() == null }
            .thenBy { it.releaseYearOrNull() ?: Int.MAX_VALUE }
    } else {
        compareBy<Song> { it.releaseYearOrNull() == null }
            .thenByDescending { it.releaseYearOrNull() ?: Int.MIN_VALUE }
    }
    return sortedWith(
        comparator
            .thenBy { it.album.lowercase(Locale.ROOT) }
            .thenBy { if (it.discNumber > 0) it.discNumber else Int.MAX_VALUE }
            .thenBy { if (it.trackNumber > 0) it.trackNumber else Int.MAX_VALUE }
            .thenBy { it.title.lowercase(Locale.ROOT) }
    )
}

private fun Song.releaseYearOrNull(): Int? =
    Regex("""\d{4}""").find(year)?.value?.toIntOrNull()

internal fun Song.indexLetter(sortKey: String? = null): String {
    return (sortKey ?: title.musicSortKey()).toFastIndexSection()
}

private fun String.musicSortKey(): String {
    val text = trim()
    if (text.isBlank()) return ""
    if (text.isAsciiSortable()) return text.toFastIndexSortableKey()
    MusicSortKeyCache[text]?.let { return it }
    val latin = runCatching { MusicSortTransliterator.value.transliterate(text) }.getOrDefault(text)
    return latin.toFastIndexSortableKey().also { MusicSortKeyCache[text] = it }
}

private inline fun List<Song>.sortedByMusicKey(crossinline selector: (Song) -> String): HomeSortedSongs {
    val entries = map { song ->
        val raw = selector(song)
        SongSortEntry(
            song = song,
            sortKey = raw.musicSortKey(),
            fallback = raw
        )
    }.sortedWith(
        compareBy<SongSortEntry> { it.sortKey }
            .thenBy { it.fallback }
    )
    return HomeSortedSongs(
        songs = entries.map { it.song },
        sortKeysBySongId = entries.associate { it.song.id to it.sortKey }
    )
}

internal data class HomeSortedSongs(
    val songs: List<Song>,
    val sortKeysBySongId: Map<Long, String>
)

private data class SongSortEntry(
    val song: Song,
    val sortKey: String,
    val fallback: String
)

private object HomeSortResultCache {
    private const val MaxSize = 8
    private const val MaxCachedSongCount = 10_000
    private val lock = Any()
    private val values = object : LinkedHashMap<HomeSortCacheKey, HomeSortedSongs>(MaxSize, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<HomeSortCacheKey, HomeSortedSongs>?): Boolean {
            return size > MaxSize
        }
    }

    fun getOrPut(songs: List<Song>, sortMode: HomeSortMode, builder: () -> HomeSortedSongs): HomeSortedSongs {
        if (songs.size > MaxCachedSongCount) return builder()
        val key = songs.cacheKey(sortMode)
        synchronized(lock) {
            values[key]?.let { return it }
        }
        val sorted = builder()
        synchronized(lock) {
            values[key] = sorted
        }
        return sorted
    }

    private fun List<Song>.cacheKey(sortMode: HomeSortMode): HomeSortCacheKey {
        var hash = 1125899906842597L
        forEach { song ->
            hash = hash.mix(song.hashCode().toLong())
                .mix(song.id)
                .mix(song.dateAdded)
                .mix(song.dateModified)
                .mix(song.fileSize)
                .mix(song.title.hashCode().toLong())
                .mix(song.fileName.hashCode().toLong())
                .mix(song.album.hashCode().toLong())
                .mix(song.year.hashCode().toLong())
                .mix(song.discNumber.toLong())
                .mix(song.trackNumber.toLong())
        }
        return HomeSortCacheKey(sortMode, size, hash)
    }

    private fun Long.mix(value: Long): Long =
        (this xor value).let { mixed -> mixed * 1099511628211L }
}

private data class HomeSortCacheKey(
    val sortMode: HomeSortMode,
    val size: Int,
    val fingerprint: Long
)

private fun String.isAsciiSortable(): Boolean {
    return all { it.code in 0x20..0x7E }
}

private object MusicSortTransliterator {
    val value: Transliterator by lazy {
        Transliterator.getInstance("Any-Latin; Latin-ASCII; NFD; [:Nonspacing Mark:] Remove; NFC")
    }
}

/**
 * Caches the (expensive) ICU transliteration used to build A-Z sort keys for non-ASCII titles.
 * Persisted to disk so the keys are not rebuilt from scratch on every cold launch — for a large
 * CJK library that meant running the transliterator for hundreds of titles each startup.
 */
internal object MusicSortKeyCache {
    private const val MaxSize = 8192
    private val lock = Any()
    private val values = object : LinkedHashMap<String, String>(MaxSize, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean {
            return size > MaxSize
        }
    }

    @Volatile private var file: File? = null
    private var loaded = false
    private var dirty = false

    /** Register the backing file (no I/O); the contents are loaded lazily on first access. */
    fun configure(storeFile: File) {
        file = storeFile
    }

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        val f = file ?: return
        if (!f.exists()) return
        runCatching {
            val obj = JSONObject(f.readText())
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                values[key] = obj.optString(key)
            }
        }
    }

    operator fun get(key: String): String? = synchronized(lock) {
        ensureLoaded()
        values[key]
    }

    operator fun set(key: String, value: String) {
        synchronized(lock) {
            ensureLoaded()
            if (values[key] != value) {
                values[key] = value
                dirty = true
            }
        }
    }

    /** Flush newly computed keys to disk. Safe to call from a background thread. */
    fun persist() {
        val f = file ?: return
        synchronized(lock) {
            if (!dirty) return
            runCatching {
                val obj = JSONObject()
                values.forEach { (key, value) -> obj.put(key, value) }
                f.writeText(obj.toString())
                dirty = false
            }
        }
    }
}
