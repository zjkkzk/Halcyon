package com.ella.music.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

object LibrarySortUiState {
    var librarySongSortIndex by mutableIntStateOf(0)
    var albumListSortIndex by mutableIntStateOf(0)
    var albumListFirstVisibleItemIndex by mutableIntStateOf(0)
    var albumListFirstVisibleItemScrollOffset by mutableIntStateOf(0)
    val albumListScrollPositions = mutableMapOf<String, Pair<Int, Int>>()
    var artistListSortIndex by mutableIntStateOf(0)
    var artistListFirstVisibleItemIndex by mutableIntStateOf(0)
    var artistListFirstVisibleItemScrollOffset by mutableIntStateOf(0)
    val artistListScrollPositions = mutableMapOf<String, Pair<Int, Int>>()
    var albumDetailSongSortIndex by mutableIntStateOf(0)
    var artistDetailSongSortIndex by mutableIntStateOf(0)
    var artistDetailAlbumSortIndex by mutableIntStateOf(0)
    var folderListSortIndex by mutableIntStateOf(0)
    var folderListFirstVisibleItemIndex by mutableIntStateOf(0)
    var folderListFirstVisibleItemScrollOffset by mutableIntStateOf(0)
    val folderListScrollPositions = mutableMapOf<String, Pair<Int, Int>>()
    var folderDetailSongSortIndex by mutableIntStateOf(0)
    var folderPlaylistListSortIndex by mutableIntStateOf(2)
    var playlistListSortIndex by mutableIntStateOf(2)

    val metadataCategoryScrollPositions = mutableMapOf<String, Pair<Int, Int>>()
    val metadataCategoryDetailScrollPositions = mutableMapOf<String, Pair<Int, Int>>()
}
