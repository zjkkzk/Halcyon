package com.ella.music.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class LyricLineTimingTest {
    @Test
    fun primaryEndIgnoresExtendedBackgroundVocals() {
        val line = LyricLine(
            timeMs = 0L,
            text = "main",
            words = listOf(LyricWord("main", 0L, 2_000L)),
            backgroundText = "ah",
            backgroundWords = listOf(LyricWord("ah", 1_500L, 5_000L)),
            backgroundStartMs = 1_500L,
            backgroundEndMs = 5_000L,
            endMs = 5_000L,
            isTtml = true
        )

        assertEquals(2_000L, line.primaryEndMs(nextLineStartMs = 3_000L))
    }

    @Test
    fun primaryEndFallsBackToNextLineWhenUntimedLineOverlaps() {
        val line = LyricLine(
            timeMs = 0L,
            text = "main",
            backgroundText = "ah",
            backgroundEndMs = 5_000L,
            endMs = 5_000L,
            isTtml = true
        )

        assertEquals(3_000L, line.primaryEndMs(nextLineStartMs = 3_000L))
    }
}
