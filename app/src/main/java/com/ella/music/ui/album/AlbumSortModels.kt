package com.ella.music.ui.album

import android.icu.text.Transliterator
import com.ella.music.R
import com.ella.music.data.model.Album
import com.ella.music.data.model.formatPlaybackDuration
import java.util.Locale

internal enum class AlbumSortMode(val labelRes: Int) {
    Name(R.string.album_sort_name),
    Artist(R.string.album_sort_artist),
    SongCount(R.string.playlist_sort_song_count),
    Duration(R.string.playlist_song_sort_duration),
    YearAsc(R.string.playlist_song_sort_year_asc),
    YearDesc(R.string.playlist_song_sort_year_desc)
}

internal fun Album.summaryForSort(context: android.content.Context, sortMode: AlbumSortMode, duration: Long): String {
    if (sortMode == AlbumSortMode.Artist) {
        return buildList {
            albumArtist.ifBlank { artist }.trim().takeIf { it.isNotBlank() }?.let(::add)
            if (year.isNotBlank()) add(year)
            add(context.getString(R.string.song_count, songCount))
        }.joinToString(" · ")
    }
    val first = if (sortMode == AlbumSortMode.Duration) {
        duration.formatAlbumDuration()
    } else {
        context.getString(R.string.song_count, songCount)
    }
    return buildList {
        add(first)
        if (year.isNotBlank()) add(year)
        val artistText = albumArtist.trim()
        if (artistText.isNotBlank()) add(artistText)
    }.joinToString(" · ")
}

private fun Long.formatAlbumDuration(): String {
    return formatPlaybackDuration()
}

internal fun Album.indexLetter(sortMode: AlbumSortMode): String {
    val source = if (sortMode == AlbumSortMode.Artist) albumArtist.ifBlank { artist } else name
    val first = source.musicSortKey().firstOrNull()?.uppercaseChar()
    return if (first != null && first in 'A'..'Z') first.toString() else "#"
}

internal fun String.musicSortKey(): String {
    val text = trim()
    if (text.isBlank()) return ""
    if (text.isAsciiSortable()) return text.lowercase(Locale.ROOT)

    AlbumSortKeyCache[text]?.let { return it }

    val latin = runCatching {
        AlbumSortTransliterator.value.transliterate(text)
    }.getOrDefault(text)

    return latin.lowercase(Locale.ROOT).also {
        AlbumSortKeyCache[text] = it
    }
}

private fun String.isAsciiSortable(): Boolean {
    return all { it.code in 0x20..0x7E }
}

private object AlbumSortTransliterator {
    val value: Transliterator by lazy {
        Transliterator.getInstance("Any-Latin; Latin-ASCII; NFD; [:Nonspacing Mark:] Remove; NFC")
    }
}

private object AlbumSortKeyCache {
    private const val MaxSize = 4096

    private val values = object : LinkedHashMap<String, String>(MaxSize, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean {
            return size > MaxSize
        }
    }

    operator fun get(key: String): String? = synchronized(values) { values[key] }

    operator fun set(key: String, value: String) {
        synchronized(values) { values[key] = value }
    }
}
