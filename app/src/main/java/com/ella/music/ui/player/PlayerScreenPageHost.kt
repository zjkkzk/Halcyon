package com.ella.music.ui.player

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier

@Composable
internal fun PlayerPagerSyncEffects(
    immersiveAlbumCover: Boolean,
    showLyrics: Boolean,
    pagerState: PagerState,
    onShowLyricsChange: (Boolean) -> Unit
) {
    LaunchedEffect(showLyrics) {
        if (immersiveAlbumCover) return@LaunchedEffect
        val target = if (showLyrics) PLAYER_PAGE_LYRICS else PLAYER_PAGE_COVER
        if (showLyrics && pagerState.currentPage != target && !pagerState.isScrollInProgress) {
            pagerState.animateScrollToPage(target)
        } else if (!showLyrics && pagerState.currentPage == PLAYER_PAGE_LYRICS && !pagerState.isScrollInProgress) {
            pagerState.animateScrollToPage(target)
        }
    }
    LaunchedEffect(pagerState.currentPage) {
        if (immersiveAlbumCover) return@LaunchedEffect
        val lyricPageVisible = pagerState.currentPage == PLAYER_PAGE_LYRICS
        if (showLyrics != lyricPageVisible) {
            onShowLyricsChange(lyricPageVisible)
        }
    }
    LaunchedEffect(immersiveAlbumCover) {
        if (immersiveAlbumCover && pagerState.currentPage != PLAYER_PAGE_COVER) {
            onShowLyricsChange(false)
            pagerState.scrollToPage(PLAYER_PAGE_COVER)
        }
    }
}

@Composable
internal fun PlayerScreenPageHost(
    immersiveAlbumCover: Boolean,
    showLyrics: Boolean,
    pagerState: PagerState,
    userScrollEnabled: Boolean,
    onShowImmersiveLyrics: () -> Unit,
    onDismissImmersiveLyrics: () -> Unit,
    onShowPagedLyrics: () -> Unit,
    onDismissPagedLyrics: () -> Unit,
    coverPage: @Composable (onShowLyrics: () -> Unit, Modifier) -> Unit,
    lyricsPage: @Composable (onDismissLyrics: () -> Unit, enableSwipeDismiss: Boolean, Modifier) -> Unit,
    detailPage: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier
) {
    if (immersiveAlbumCover) {
        if (showLyrics) {
            lyricsPage(
                onDismissImmersiveLyrics,
                true,
                modifier.fillMaxSize()
            )
        } else {
            coverPage(
                onShowImmersiveLyrics,
                modifier.fillMaxSize()
            )
        }
    } else {
        HorizontalPager(
            state = pagerState,
            modifier = modifier.fillMaxSize(),
            userScrollEnabled = userScrollEnabled,
            beyondViewportPageCount = 1
        ) { page ->
            when (page) {
                PLAYER_PAGE_COVER -> coverPage(
                    onShowPagedLyrics,
                    Modifier.fillMaxSize()
                )
                PLAYER_PAGE_LYRICS -> lyricsPage(
                    onDismissPagedLyrics,
                    false,
                    Modifier.fillMaxSize()
                )
                PLAYER_PAGE_DETAILS -> detailPage(Modifier.fillMaxSize())
            }
        }
    }
}

internal const val PLAYER_PAGE_DETAILS = 0
internal const val PLAYER_PAGE_COVER = 1
internal const val PLAYER_PAGE_LYRICS = 2
internal const val PLAYER_PAGE_COUNT = 3
