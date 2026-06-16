package com.ella.music.ui.search

import android.content.Context
import com.ella.music.R
import com.ella.music.data.decodeNeteaseKey
import com.ella.music.data.model.Album
import com.ella.music.data.model.Artist
import com.ella.music.data.model.LyricLine
import com.ella.music.data.model.Song
import com.ella.music.data.model.SongTagInfo
import org.json.JSONArray
import org.json.JSONObject

internal enum class SearchFilter {
    All,
    Songs,
    Artists,
    Albums,
    Playlists,
    Folders,
    Composers,
    Lyricists,
    Lyrics,
    Genres,
    Years;

    companion object {
        fun fromRouteType(type: String?): SearchFilter {
            return when (type?.trim()?.lowercase()) {
                null, "", "all" -> All
                "song", "songs" -> Songs
                "artist", "artists" -> Artists
                "album", "albums" -> Albums
                "playlist", "playlists" -> Playlists
                "folder", "folders" -> Folders
                "composer", "composers" -> Composers
                "lyricist", "lyricists" -> Lyricists
                "lyric", "lyrics" -> Lyrics
                "genre", "genres" -> Genres
                "year", "years" -> Years
                "duplicate", "duplicates" -> Songs
                else -> All
            }
        }
    }
}

internal val SearchFilter.acceptsSongResults: Boolean
    get() = this in listOf(SearchFilter.All, SearchFilter.Songs, SearchFilter.Lyrics)

internal val SearchFilter.supportsDuplicateFilter: Boolean
    get() = this in listOf(SearchFilter.All, SearchFilter.Songs)

internal data class ArtistSearchResult(
    val artist: Artist,
    val representativeSong: Song?,
    val participatedAlbumCount: Int = artist.albumCount
)

internal data class SongSearchResult(
    val song: Song,
    val lyricSnippet: String? = null,
    val matches: List<SongSearchMatch> = song.directSearchMatches("")
) {
    val primaryLabelRes: Int
        get() = when {
            lyricSnippet != null -> R.string.library_search_lyrics
            matches.isNotEmpty() -> matches.first().labelRes
            else -> R.string.library_search_songs
        }
}

internal data class SongSearchMatch(
    val labelRes: Int,
    val value: String
)

internal data class SongSearchGroupEntry(
    val result: SongSearchResult,
    val match: SongSearchMatch?,
    val keySuffix: String
)

internal fun SongSearchResult.toSearchGroupEntries(filter: SearchFilter): List<Pair<Int, SongSearchGroupEntry>> {
    if (lyricSnippet != null) {
        return listOf(
            R.string.library_search_lyrics to SongSearchGroupEntry(
                result = this,
                match = null,
                keySuffix = "lyrics:${lyricSnippet.hashCode()}"
            )
        )
    }
    if (filter == SearchFilter.Lyrics) return emptyList()
    if (matches.isEmpty()) {
        return listOf(
            R.string.library_search_songs to SongSearchGroupEntry(
                result = this,
                match = null,
                keySuffix = "song"
            )
        )
    }
    return matches.mapIndexed { index, match ->
        match.labelRes to SongSearchGroupEntry(
            result = this,
            match = match,
            keySuffix = "$index:${match.labelRes}:${match.value.hashCode()}"
        )
    }
}

internal fun Song.directSearchMatches(
    query: String,
    tagInfo: SongTagInfo? = null,
    includeSnapshotTag: Boolean = false
): List<SongSearchMatch> {
    val target = query.trim()
    if (target.isBlank()) return emptyList()
    return buildList {
        addMatch(R.string.library_search_match_title, title, target)
        addMatch(R.string.library_search_match_artist, artist, target)
        addMatch(R.string.library_search_match_album, album, target)
        addMatch(R.string.library_search_match_album_artist, albumArtist, target)
        addMatch(R.string.library_search_match_genre, genre, target)
        addMatch(R.string.library_search_match_year, year, target)
        addMatch(R.string.library_search_match_composer, composer, target)
        addMatch(R.string.library_search_match_lyricist, lyricist, target)
        addMatch(R.string.library_search_match_file_name, fileName, target)
        tagInfo?.displayComment?.let { addMatch(R.string.library_search_match_comment, it, target) }
        decodeNeteaseKey(tagInfo?.neteaseKey.orEmpty())
            ?.aliases
            .orEmpty()
            .forEach { alias -> addMatch(R.string.library_search_match_alias, alias, target) }
        if (includeSnapshotTag && isEmpty()) {
            add(SongSearchMatch(R.string.library_search_match_tag, target))
        }
    }
}

private fun MutableList<SongSearchMatch>.addMatch(labelRes: Int, value: String, query: String) {
    val trimmed = value.trim()
    if (trimmed.isNotBlank() && trimmed.contains(query, ignoreCase = true)) {
        add(SongSearchMatch(labelRes, trimmed))
    }
}

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
    buildList {
        val firstByKey = HashMap<String, Song>()
        val duplicatesByKey = LinkedHashMap<String, MutableList<Song>>()
        this@duplicateTitleAlbumSongs.forEach { song ->
            val key = "${song.title.trim().lowercase()}|${song.album.trim().lowercase()}"
            val first = firstByKey.putIfAbsent(key, song)
            if (first != null) {
                val duplicates = duplicatesByKey.getOrPut(key) { mutableListOf(first) }
                duplicates += song
            }
        }
        duplicatesByKey.values.forEach(::addAll)
    }
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
                val snippet = item.optString("lyricSnippet").takeIf { it.isNotBlank() }
                add(SongSearchResult(song, snippet, song.directSearchMatches(query)))
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
