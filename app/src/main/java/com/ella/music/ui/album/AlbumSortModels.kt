package com.ella.music.ui.album

import com.ella.music.R
import com.ella.music.data.model.Album
import com.ella.music.data.model.formatPlaybackDuration
import com.ella.music.ui.components.toFastIndexSection
import com.ella.music.ui.listmodel.MusicSortKeyNormalizer

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
    return source.musicSortKey().toFastIndexSection()
}

internal fun String.musicSortKey(): String {
    return MusicSortKeyNormalizer.normalize(this)
}
