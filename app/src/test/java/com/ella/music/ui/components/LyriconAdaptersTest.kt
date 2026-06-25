package com.ella.music.ui.components

import com.ella.music.data.model.LyricLine
import com.ella.music.data.model.LyricWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class LyriconAdaptersTest {
    @Test
    fun backgroundTranslationKeepsSecondaryKaraokeWords() {
        val song = listOf(
            LyricLine(
                timeMs = 1_000L,
                text = "To get respect from",
                backgroundText = "(Baby",
                backgroundWords = listOf(
                    LyricWord("(Baby", 2_000L, 2_800L)
                ),
                backgroundTranslation = "宝贝",
                endMs = 3_000L
            )
        ).toLyriconSong(songId = 1L, songTitle = "Let Me Hear", songArtist = "Fear, and Loathing in Las Vegas")

        val line = song.lyrics!!.single()
        assertEquals("Baby\u000B宝贝", line.secondary)
        assertNotNull(line.secondaryWords)
        assertEquals(listOf("Baby"), line.secondaryWords?.map { it.text })
    }

    @Test
    fun backgroundWithoutTranslationKeepsSecondaryKaraokeWords() {
        val song = listOf(
            LyricLine(
                timeMs = 3_000L,
                text = "Others To get closer",
                backgroundText = "(Yeah",
                backgroundWords = listOf(
                    LyricWord("(Yeah", 3_100L, 3_600L)
                ),
                endMs = 4_000L
            )
        ).toLyriconSong(songId = 2L, songTitle = "Let Me Hear", songArtist = "Fear, and Loathing in Las Vegas")

        val line = song.lyrics!!.single()
        assertEquals("Yeah", line.secondary)
        assertNotNull(line.secondaryWords)
        assertEquals(listOf("Yeah"), line.secondaryWords?.map { it.text })
    }

    @Test
    fun overlappingDuetLinesKeepIndependentHighlightWindows() {
        val song = listOf(
            LyricLine(
                timeMs = 158_424L,
                text = "パッと花火が",
                words = listOf(
                    LyricWord("パッと", 158_424L, 158_857L),
                    LyricWord("花火", 158_857L, 159_306L),
                    LyricWord("が", 159_306L, 160_548L)
                ),
                agent = "v1"
            ),
            LyricLine(
                timeMs = 159_490L,
                text = "パッと花火が",
                words = listOf(
                    LyricWord("パッと", 159_490L, 160_051L),
                    LyricWord("花火", 160_051L, 160_582L),
                    LyricWord("が", 160_582L, 161_521L)
                ),
                agent = "v2"
            )
        ).toLyriconSong(songId = 3L, songTitle = "打上花火", songArtist = "DAOKO×米津玄師")

        val lines = requireNotNull(song.lyrics)
        assertEquals(2, lines.size)
        assertEquals(false, lines[0].isAlignedRight)
        assertEquals(true, lines[1].isAlignedRight)
        assertEquals(160_548L, lines[0].end)
        assertEquals(159_490L, lines[1].begin)
        assert(lines[0].end > lines[1].begin)
    }

    @Test
    fun ttmlBackgroundKeepsLineHighlightedUntilBackgroundEnds() {
        val song = listOf(
            LyricLine(
                timeMs = 8_738L,
                text = "And you're the kind of guy the ladies want",
                words = listOf(
                    LyricWord("And ", 8_738L, 8_872L),
                    LyricWord("you're ", 8_872L, 9_058L),
                    LyricWord("the ", 9_058L, 9_154L),
                    LyricWord("kind ", 9_215L, 9_386L),
                    LyricWord("of ", 9_386L, 9_534L),
                    LyricWord("guy ", 9_581L, 9_886L),
                    LyricWord("the ", 9_886L, 10_118L),
                    LyricWord("ladies ", 10_191L, 10_760L),
                    LyricWord("want", 10_863L, 11_264L)
                ),
                backgroundText = "And there's a lot of cool chicks out there",
                backgroundWords = listOf(
                    LyricWord("And ", 11_357L, 11_458L),
                    LyricWord("there's ", 11_510L, 11_674L),
                    LyricWord("a ", 11_674L, 11_847L),
                    LyricWord("lot ", 11_847L, 12_009L),
                    LyricWord("of ", 12_009L, 12_102L),
                    LyricWord("cool ", 12_149L, 12_392L),
                    LyricWord("chicks ", 12_448L, 12_813L),
                    LyricWord("out ", 12_852L, 13_102L),
                    LyricWord("there", 13_148L, 13_638L)
                ),
                backgroundTranslation = "外面很多美女对你虎视眈眈",
                backgroundStartMs = 11_357L,
                backgroundEndMs = 13_638L,
                isTtml = true,
                endMs = 13_638L
            ),
            LyricLine(
                timeMs = 14_004L,
                text = "I know that I went psycho on the phone",
                words = listOf(LyricWord("I know that I went psycho on the phone", 14_004L, 16_409L)),
                isTtml = true,
                endMs = 16_409L
            )
        ).toLyriconSong(songId = 4L, songTitle = "ME!", songArtist = "Taylor Swift")

        val line = requireNotNull(song.lyrics).first()
        assertEquals(13_638L, line.end)
        assertEquals(
            listOf("And ", "there's ", "a ", "lot ", "of ", "cool ", "chicks ", "out ", "there"),
            line.secondaryWords?.map { it.text }
        )
    }
}
