package io.github.proify.lyricon.lyric.view

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricViewHighlightPolicyTest {
    @Test
    fun previewedFutureLineStaysHighlightedAlongsideActiveLine() {
        val active = setOf(0)

        assertTrue(
            isLyricViewLineHighlighted(
                index = 0,
                currentIndex = 1,
                activeHighlightIndices = active
            )
        )
        assertFalse(
            isLyricViewLineHighlighted(
                index = 2,
                currentIndex = 1,
                activeHighlightIndices = active
            )
        )
        assertTrue(
            isLyricViewLineHighlighted(
                index = 1,
                currentIndex = 1,
                activeHighlightIndices = active
            )
        )
    }

    @Test
    fun currentLineStaysHighlightedWhenNoLineIsActive() {
        assertTrue(
            isLyricViewLineHighlighted(
                index = 3,
                currentIndex = 3,
                activeHighlightIndices = emptySet()
            )
        )
        assertFalse(
            isLyricViewLineHighlighted(
                index = 2,
                currentIndex = 3,
                activeHighlightIndices = emptySet()
            )
        )
    }
}
