package com.ella.music.viewmodel

import com.ella.music.data.model.LyricLine
import com.ella.music.data.model.LyricWord
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerLyricDisplayPreparationTest {
    @Test
    fun sameTimestampUntimedCompanionDisplaysAsTranslation() {
        val lyrics = listOf(
            LyricLine(
                timeMs = 698L,
                text = "揺籃のうたをカナリヤが歌うよ",
                words = listOf(
                    LyricWord("揺", 698L, 1_546L),
                    LyricWord("籃", 1_546L, 2_762L)
                ),
                endMs = 11_677L
            ),
            LyricLine(
                timeMs = 698L,
                text = "树上的金丝雀 轻唱着摇篮曲",
                endMs = 12_508L
            )
        )

        val prepared = lyrics.preparedForDisplay(emptyList())

        assertEquals(1, prepared.size)
        assertEquals("揺籃のうたをカナリヤが歌うよ", prepared.single().text)
        assertEquals("树上的金丝雀 轻唱着摇篮曲", prepared.single().translation)
        assertEquals(listOf("揺", "籃"), prepared.single().words.map { it.text })
        assertEquals(12_508L, prepared.single().endMs)
    }
}
