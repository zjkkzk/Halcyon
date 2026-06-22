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
    Duration(R.string.playlist_sort_duration),
    CustomDesc(R.string.playlist_sort_custom_desc)
}

internal fun List<FolderPlaylist>.sortedForFolderPlaylists(
    mode: FolderPlaylistSortMode,
    songCountProvider: (FolderPlaylist) -> Int,
    durationProvider: (FolderPlaylist) -> Long,
    pinnedIds: List<String> = emptyList()
): List<FolderPlaylist> {
    val sorted = when (mode) {
        FolderPlaylistSortMode.Custom -> this
        FolderPlaylistSortMode.DateUpdated -> sortedWith(compareByDescending<FolderPlaylist> { it.updatedAt }.thenBy { it.name.musicSortKey() })
        FolderPlaylistSortMode.DateCreatedDesc -> sortedWith(compareByDescending<FolderPlaylist> { it.createdAt }.thenBy { it.name.musicSortKey() })
        FolderPlaylistSortMode.DateCreated -> sortedWith(compareBy<FolderPlaylist> { it.createdAt }.thenBy { it.name.musicSortKey() })
        FolderPlaylistSortMode.Name -> sortedBy { it.name.musicSortKey() }
        FolderPlaylistSortMode.FolderCount -> sortedWith(compareByDescending<FolderPlaylist> { it.folders.size }.thenBy { it.name.musicSortKey() })
        FolderPlaylistSortMode.SongCount -> sortedWith(compareByDescending<FolderPlaylist> { songCountProvider(it) }.thenBy { it.name.musicSortKey() })
        FolderPlaylistSortMode.Duration -> sortedWith(compareByDescending<FolderPlaylist> { durationProvider(it) }.thenBy { it.name.musicSortKey() })
        FolderPlaylistSortMode.CustomDesc -> asReversed()
    }
    if (pinnedIds.isEmpty()) return sorted
    val pinnedRank = pinnedIds.withIndex().associate { it.value to it.index }
    val pinnedSet = pinnedRank.keys
    val pinned = sorted
        .filter { it.id in pinnedSet }
        .sortedBy { pinnedRank[it.id] ?: Int.MAX_VALUE }
    return pinned + sorted.filterNot { it.id in pinnedSet }
}
