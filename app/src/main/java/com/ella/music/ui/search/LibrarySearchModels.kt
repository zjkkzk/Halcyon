package com.ella.music.ui.search

import android.content.Context
import com.ella.music.data.model.Album
import com.ella.music.data.model.Artist
import com.ella.music.data.model.LyricLine
import com.ella.music.data.model.Song
import org.json.JSONArray
import org.json.JSONObject

internal enum class SearchFilter {
    All,
    Songs,
    Albums,
    Artists,
    Duplicates;

    companion object {
        fun fromRouteType(type: String?): SearchFilter {
            return when (type?.trim()?.lowercase()) {
                null, "", "all" -> All
                "song", "songs" -> Songs
                "album", "albums" -> Albums
                "artist", "artists" -> Artists
                "duplicate", "duplicates" -> Duplicates
                else -> All
            }
        }
    }
}

internal data class ArtistSearchResult(
    val artist: Artist,
    val representativeSong: Song?,
    val participatedAlbumCount: Int = artist.albumCount
)

internal data class SongSearchResult(
    val song: Song,
    val lyricSnippet: String? = null
)

internal fun List<LyricLine>.firstMatchingLyricSnippet(query: String): String? {
    return asSequence()
        .flatMap { line ->
            sequenceOf(
                line.text,
                line.translation.orEmpty(),
                line.pronunciation.orEmpty(),
                line.backgroundText.orEmpty(),
                line.backgroundTranslation.orEmpty()
            )
        }
        .map { it.trim() }
        .filter { it.isNotBlank() && it.contains(query, ignoreCase = true) }
        .firstOrNull()
        ?.compactSearchSnippet(query)
}

private fun String.compactSearchSnippet(query: String): String {
    val normalized = replace(Regex("\\s+"), " ").trim()
    if (normalized.length <= 52) return normalized
    val index = normalized.indexOf(query, ignoreCase = true).coerceAtLeast(0)
    val start = (index - 18).coerceAtLeast(0)
    val end = (index + query.length + 28).coerceAtMost(normalized.length)
    return buildString {
        if (start > 0) append("...")
        append(normalized.substring(start, end))
        if (end < normalized.length) append("...")
    }
}

internal fun Song.searchIdentityKey(): String = "$id|$path"

internal fun Album.matchesLibrarySearch(query: String): Boolean =
    name.contains(query, ignoreCase = true) ||
        artist.contains(query, ignoreCase = true) ||
        albumArtist.contains(query, ignoreCase = true)

internal fun List<Song>.duplicateTitleAlbumSongs(): List<Song> =
    groupBy { "${it.title.trim().lowercase()}|${it.album.trim().lowercase()}" }
        .values
        .filter { it.size > 1 }
        .flatten()
        .sortedWith(compareBy<Song> { it.album.lowercase() }.thenBy { it.title.lowercase() }.thenBy { it.artist.lowercase() })

internal fun loadSearchHistory(context: Context): List<String> =
    context.getSharedPreferences(SEARCH_PREFS, Context.MODE_PRIVATE)
        .getString(SEARCH_HISTORY_KEY, "")
        .orEmpty()
        .split("\n")
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()

internal fun saveSearchHistory(context: Context, query: String): List<String> {
    val next = (listOf(query.trim()) + loadSearchHistory(context))
        .filter { it.isNotBlank() }
        .distinct()
        .take(20)
    saveSearchHistory(context, next)
    return next
}

internal fun saveSearchHistory(context: Context, history: List<String>) {
    context.getSharedPreferences(SEARCH_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(SEARCH_HISTORY_KEY, history.joinToString("\n"))
        .apply()
}

internal fun loadCachedSongSearchResults(
    context: Context,
    songs: List<Song>,
    query: String,
    filter: SearchFilter
): List<SongSearchResult> {
    if (query.isBlank() || filter !in listOf(SearchFilter.All, SearchFilter.Songs)) return emptyList()
    val raw = context.getSharedPreferences(SEARCH_PREFS, Context.MODE_PRIVATE)
        .getString(searchResultCacheKey(query, filter), null)
        ?: return emptyList()
    val byKey = songs.associateBy { it.searchIdentityKey() }
    return runCatching {
        val array = JSONArray(raw)
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val song = byKey[item.optString("key")] ?: continue
                add(SongSearchResult(song, item.optString("lyricSnippet").takeIf { it.isNotBlank() }))
                if (size >= 80) break
            }
        }
    }.getOrDefault(emptyList())
}

internal fun saveCachedSongSearchResults(
    context: Context,
    query: String,
    filter: SearchFilter,
    results: List<SongSearchResult>
) {
    if (query.isBlank() || filter !in listOf(SearchFilter.All, SearchFilter.Songs) || results.isEmpty()) return
    val array = JSONArray()
    results.take(80).forEach { result ->
        array.put(
            JSONObject()
                .put("key", result.song.searchIdentityKey())
                .put("lyricSnippet", result.lyricSnippet.orEmpty())
        )
    }
    context.getSharedPreferences(SEARCH_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(searchResultCacheKey(query, filter), array.toString())
        .apply()
}

private fun searchResultCacheKey(query: String, filter: SearchFilter): String =
    "results:${filter.name.lowercase()}:${query.trim().lowercase()}"

private const val SEARCH_PREFS = "library_search"
private const val SEARCH_HISTORY_KEY = "history"
