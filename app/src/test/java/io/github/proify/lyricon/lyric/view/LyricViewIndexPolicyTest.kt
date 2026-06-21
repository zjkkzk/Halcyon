package io.github.proify.lyricon.lyric.view

import org.junit.Assert.assertEquals
import org.junit.Test

class LyricViewIndexPolicyTest {
    private val lines = listOf(
        LyricViewLineWindow(begin = 0L, end = 4_000L),
        LyricViewLineWindow(begin = 4_500L, end = 7_000L),
        LyricViewLineWindow(begin = 7_200L, end = 9_000L)
    )

    @Test
    fun monotonicPlaybackDoesNotBounceBackAfterPreviewAdvance() {
        val firstAdvance = resolveLyricViewIndex(
            positionMs = 3_890L,
            previousPositionMs = 3_840L,
            currentIndex = 0,
            currentPreviewOffsetMs = computeLyricViewPreviewOffsetMs(0, lines),
            lines = lines
        )

        val stayOnPreviewedLine = resolveLyricViewIndex(
            positionMs = 3_915L,
            previousPositionMs = 3_890L,
            currentIndex = firstAdvance,
            currentPreviewOffsetMs = computeLyricViewPreviewOffsetMs(firstAdvance, lines),
            lines = lines
        )

        assertEquals(1, firstAdvance)
        assertEquals(1, stayOnPreviewedLine)
    }

    @Test
    fun backwardSeekCanReturnToPreviousLine() {
        val result = resolveLyricViewIndex(
            positionMs = 3_200L,
            previousPositionMs = 3_780L,
            currentIndex = 1,
            currentPreviewOffsetMs = computeLyricViewPreviewOffsetMs(1, lines),
            lines = lines
        )

        assertEquals(0, result)
    }
}
