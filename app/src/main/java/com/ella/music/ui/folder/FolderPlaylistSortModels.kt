package com.ella.music.ui.folder

import com.ella.music.R
import com.ella.music.data.model.FolderPlaylist

internal enum class FolderPlaylistSortMode(val labelRes: Int) {
    Custom(R.string.playlist_sort_custom),
    DateUpdated(R.string.playlist_sort_updated_at),
    DateCreatedDesc(R.string.playlist_sort_created_at_desc),
    DateCreated(R.string.playlist_sort_created_at),
    Name(R.string.playlist_sort_name),
    FolderCount(R.string.folder_playlist_sort_folder_count),
    SongCount(R.string.playlist_sort_song_count),
    Duration(R.string.playlist_sort_duration)
}

internal fun List<FolderPlaylist>.sortedForFolderPlaylists(
    mode: FolderPlaylistSortMode,
    songCountProvider: (FolderPlaylist) -> Int,
    durationProvider: (FolderPlaylist) -> Long,
    pinnedId: String? = null
): List<FolderPlaylist> {
    val sorted = when (mode) {
        FolderPlaylistSortMode.Custom -> sortedBy { it.name.musicSortKey() }
        FolderPlaylistSortMode.DateUpdated -> sortedWith(compareByDescending<FolderPlaylist> { it.updatedAt }.thenBy { it.name.musicSortKey() })
        FolderPlaylistSortMode.DateCreatedDesc -> sortedWith(compareByDescending<FolderPlaylist> { it.createdAt }.thenBy { it.name.musicSortKey() })
        FolderPlaylistSortMode.DateCreated -> sortedWith(compareBy<FolderPlaylist> { it.createdAt }.thenBy { it.name.musicSortKey() })
        FolderPlaylistSortMode.Name -> sortedBy { it.name.musicSortKey() }
        FolderPlaylistSortMode.FolderCount -> sortedWith(compareByDescending<FolderPlaylist> { it.folders.size }.thenBy { it.name.musicSortKey() })
        FolderPlaylistSortMode.SongCount -> sortedWith(compareByDescending<FolderPlaylist> { songCountProvider(it) }.thenBy { it.name.musicSortKey() })
        FolderPlaylistSortMode.Duration -> sortedWith(compareByDescending<FolderPlaylist> { durationProvider(it) }.thenBy { it.name.musicSortKey() })
    }
    if (pinnedId.isNullOrBlank()) return sorted
    val pinned = sorted.firstOrNull { it.id == pinnedId } ?: return sorted
    return listOf(pinned) + sorted.filterNot { it.id == pinnedId }
}
