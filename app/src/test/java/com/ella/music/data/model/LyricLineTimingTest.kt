package com.ella.music.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class LyricLineTimingTest {
    @Test
    fun primaryEndIgnoresTrailingTtmlContainerSilenceAfterMainWords() {
        val line = LyricLine(
            timeMs = 0L,
            text = "main",
            words = listOf(LyricWord("main", 0L, 2_000L)),
            endMs = 3_000L,
            isTtml = true
        )

        assertEquals(2_000L, line.primaryEndMs(nextLineStartMs = 3_000L))
    }

    @Test
    fun primaryEndKeepsBackgroundVocalsVisibleUntilBackgroundEnds() {
        val line = LyricLine(
            timeMs = 0L,
            text = "main",
            words = listOf(LyricWord("main", 0L, 2_000L)),
            backgroundText = "ah",
            backgroundWords = listOf(LyricWord("ah", 1_500L, 2_800L)),
            backgroundStartMs = 1_500L,
            backgroundEndMs = 2_800L,
            endMs = 5_000L,
            isTtml = true
        )

        assertEquals(2_800L, line.primaryEndMs(nextLineStartMs = 3_000L))
    }

    @Test
    fun primaryEndClampsBackgroundOnlyLineToNextLineStartWhenItOverlaps() {
        val line = LyricLine(
            timeMs = 0L,
            text = "",
            backgroundText = "ah",
            backgroundEndMs = 5_000L,
            endMs = 5_000L,
            isTtml = true
        )

        assertEquals(3_000L, line.primaryEndMs(nextLineStartMs = 3_000L))
    }
}
