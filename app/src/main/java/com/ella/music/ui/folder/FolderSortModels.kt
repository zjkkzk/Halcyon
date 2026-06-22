package com.ella.music.ui.folder

import android.content.Context
import com.ella.music.R
import com.ella.music.data.model.formatPlaybackDuration
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal enum class FolderListSortMode(val labelRes: Int) {
    Name(R.string.playlist_sort_name),
    NameDesc(R.string.playlist_sort_name),
    SongCount(R.string.playlist_sort_song_count),
    AlbumCount(R.string.folder_sort_album_count),
    Duration(R.string.playlist_song_sort_duration),
    DateModified(R.string.playlist_song_sort_date_modified),
    DateModifiedAsc(R.string.playlist_song_sort_date_modified_asc)
}

internal enum class FolderListSortField(val labelRes: Int) {
    Name(R.string.playlist_sort_name),
    SongCount(R.string.playlist_sort_song_count),
    AlbumCount(R.string.folder_sort_album_count),
    Duration(R.string.playlist_song_sort_duration),
    DateModified(R.string.playlist_song_sort_date_modified)
}

internal fun List<FolderTreeEntry>.sortedForFolderList(
    mode: FolderListSortMode,
    pinnedPath: String? = null
): List<FolderTreeEntry> {
    val sorted = when (mode) {
        FolderListSortMode.Name -> sortedBy { it.name.musicSortKey() }
        FolderListSortMode.NameDesc -> sortedByDescending { it.name.musicSortKey() }
        FolderListSortMode.SongCount -> sortedWith(compareByDescending<FolderTreeEntry> { it.songCount }.thenBy { it.name.musicSortKey() })
        FolderListSortMode.Duration -> sortedWith(compareByDescending<FolderTreeEntry> { it.duration }.thenBy { it.name.musicSortKey() })
        FolderListSortMode.AlbumCount -> sortedWith(compareByDescending<FolderTreeEntry> { it.albumCount }.thenBy { it.name.musicSortKey() })
        FolderListSortMode.DateModified -> sortedWith(compareByDescending<FolderTreeEntry> { it.dateModified }.thenBy { it.name.musicSortKey() })
        FolderListSortMode.DateModifiedAsc -> sortedWith(compareBy<FolderTreeEntry> { it.dateModified }.thenBy { it.name.musicSortKey() })
    }
    if (pinnedPath.isNullOrBlank()) return sorted
    val pinned = sorted.firstOrNull { it.path.equals(pinnedPath, ignoreCase = true) } ?: return sorted
    return listOf(pinned) + sorted.filterNot { it.path.equals(pinnedPath, ignoreCase = true) }
}

internal fun FolderTreeEntry.summaryFor(context: android.content.Context, mode: FolderListSortMode): String {
    return when (mode) {
        FolderListSortMode.Duration -> duration.formatFolderDuration(context)
        FolderListSortMode.AlbumCount -> context.getString(R.string.album_count, albumCount)
        FolderListSortMode.DateModified,
        FolderListSortMode.DateModifiedAsc -> dateModified.formatFolderDateTime(context)
        else -> context.getString(R.string.song_count, songCount)
    }
}

internal fun FolderListSortMode.sortField(): FolderListSortField = when (this) {
    FolderListSortMode.Name,
    FolderListSortMode.NameDesc -> FolderListSortField.Name
    FolderListSortMode.SongCount -> FolderListSortField.SongCount
    FolderListSortMode.AlbumCount -> FolderListSortField.AlbumCount
    FolderListSortMode.Duration -> FolderListSortField.Duration
    FolderListSortMode.DateModified,
    FolderListSortMode.DateModifiedAsc -> FolderListSortField.DateModified
}

internal fun FolderListSortMode.isDescending(): Boolean = when (this) {
    FolderListSortMode.NameDesc,
    FolderListSortMode.SongCount,
    FolderListSortMode.AlbumCount,
    FolderListSortMode.Duration,
    FolderListSortMode.DateModified -> true
    else -> false
}

internal fun FolderListSortField.toMode(directionDescending: Boolean): FolderListSortMode = when (this) {
    FolderListSortField.Name -> if (directionDescending) FolderListSortMode.NameDesc else FolderListSortMode.Name
    FolderListSortField.SongCount -> FolderListSortMode.SongCount
    FolderListSortField.AlbumCount -> FolderListSortMode.AlbumCount
    FolderListSortField.Duration -> FolderListSortMode.Duration
    FolderListSortField.DateModified -> if (directionDescending) FolderListSortMode.DateModified else FolderListSortMode.DateModifiedAsc
}

internal fun Long.formatFolderDuration(context: android.content.Context): String {
    return formatPlaybackDuration()
}

internal fun Long.formatFolderDateTime(context: android.content.Context): String {
    if (this <= 0L) return context.getString(R.string.folder_unknown_modified_time)
    val millis = if (this < 10_000_000_000L) this * 1000L else this
    return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(millis))
}
