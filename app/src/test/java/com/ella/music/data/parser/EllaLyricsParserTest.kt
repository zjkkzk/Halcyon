package com.ella.music.data.parser

import org.junit.Assert.assertEquals
import org.junit.Test

class EllaLyricsParserTest {
    @Test
    fun placeholderOnlyTimedLinesAreIgnored() {
        val result = LrcParser.parse(
            """
            [00:00.539]花篝り (篝火) - 滴草由实 (しずくさ ゆみ)
            [00:00.539]//
            [00:04.097]词：滴草由実
            [00:04.097]//
            [00:05.785]曲：大野愛果
            [00:05.785]//
            """.trimIndent()
        )

        assertEquals(
            listOf("花篝り (篝火) - 滴草由实 (しずくさ ゆみ)", "词：滴草由実", "曲：大野愛果"),
            result.lyrics.map { it.text }
        )
        assertEquals(listOf(539L, 4_097L, 5_785L), result.lyrics.map { it.timeMs })
    }

    @Test
    fun translationHeaderAndPlaceholderBlockAreIgnored() {
        val result = LrcParser.parse(
            """
            [04:16.712](I need your love)
            [trans:]
            [00:00.724]//
            [00:07.960]//
            [00:10.204]//
            """.trimIndent()
        )

        assertEquals(listOf("(I need your love)"), result.lyrics.map { it.text })
        assertEquals(listOf(256_712L), result.lyrics.map { it.timeMs })
    }

    @Test
    fun untimedLinesDoNotAttachToPreviousLyricLine() {
        val result = LrcParser.parse(
            """
            [00:01.00]第一句
            [trans:]
            无时间戳翻译
            [00:03.00]第二句
            """.trimIndent()
        )

        assertEquals(listOf("第一句", "第二句"), result.lyrics.map { it.text })
        assertEquals(listOf(null, null), result.lyrics.map { it.translation })
    }

    @Test
    fun synchronizedCreditAndCopyrightLinesArePreserved() {
        val result = LrcParser.parse(
            """
            [00:01.00]QQ音乐享有本翻译作品的著作权
            [00:02.00]作词：Someone
            [00:03.00]正常歌词
            """.trimIndent()
        )

        assertEquals(
            listOf("QQ音乐享有本翻译作品的著作权", "作词：Someone", "正常歌词"),
            result.lyrics.map { it.text }
        )
        assertEquals(listOf(1_000L, 2_000L, 3_000L), result.lyrics.map { it.timeMs })
    }

    @Test
    fun sameTimestampWordLineAndBlankPronunciationAttachTranslation() {
        val result = LrcParser.parse(
            """
            [00:41.373] <00:41.373>wake <00:41.949>me <00:42.502>up <00:43.040>
            [00:41.373]
            [00:41.373]叫醒我
            """.trimIndent()
        )

        assertEquals(1, result.lyrics.size)
        assertEquals("wake me up", result.lyrics.single().text)
        assertEquals("叫醒我", result.lyrics.single().translation)
        assertEquals(listOf("wake ", "me ", "up"), result.lyrics.single().words.map { it.text })
    }

    @Test
    fun sameTimestampWordLineRomanizationAndTranslationAreMerged() {
        val result = LrcParser.parse(
            """
            [00:21.853] <00:21.853>覚<00:22.261>醒 <00:22.719>READY <00:23.379>OK <00:23.935>
            [00:21.853]ka ku se i READY OK
            [00:21.853]该觉醒了 Ready，ok？
            """.trimIndent()
        )

        assertEquals(1, result.lyrics.size)
        assertEquals("覚醒 READY OK", result.lyrics.single().text)
        assertEquals("ka ku se i READY OK", result.lyrics.single().pronunciation)
        assertEquals("该觉醒了 Ready，ok？", result.lyrics.single().translation)
    }

    @Test
    fun sameTimestampJapaneseWordLineKeepsChineseAsTranslation() {
        val result = LrcParser.parse(
            """
            [00:00.698]揺[00:01.546]籃[00:02.762]の[00:03.541]う[00:04.652]た[00:05.182]を[00:05.669][00:06.452]カ[00:07.165]ナ[00:07.485]リ[00:07.972]ヤ[00:08.701]が[00:09.501]歌[00:10.604]う[00:11.132]よ[00:11.677]
            [00:00.698]树上的金丝雀 轻唱着摇篮曲[00:12.508]
            """.trimIndent()
        )

        assertEquals(1, result.lyrics.size)
        assertEquals("揺籃のうたをカナリヤが歌うよ", result.lyrics.single().text)
        assertEquals("树上的金丝雀 轻唱着摇篮曲", result.lyrics.single().translation)
        assertEquals("揺", result.lyrics.single().words.first().text)
    }

    @Test
    fun kugouKrcWordTimingAndTranslationAreParsed() {
        val result = LrcParser.parse(
            """
            [language:eyJjb250ZW50IjpbeyJ0eXBlIjoxLCJseXJpY0NvbnRlbnQiOltbIuS9oOWlveS4lueVjCJdXX1dfQ==]
            [1000,2000]<0,500,0>Hel<500,500,0>lo
            """.trimIndent()
        )

        assertEquals(1, result.lyrics.size)
        assertEquals("Hello", result.lyrics.single().text)
        assertEquals("你好世界", result.lyrics.single().translation)
        assertEquals(listOf("Hel", "lo"), result.lyrics.single().words.map { it.text })
        assertEquals(listOf(1000L, 1500L), result.lyrics.single().words.map { it.startMs })
    }

    @Test
    fun accompanistTtmlPreservesLatinWordSpacesAndDropsPlaceholders() {
        val result = LrcParser.parse(
            """
            <tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata">
              <body>
                <div>
                  <p begin="00:00.000" end="00:04.000">
                    <span begin="00:00.000" end="00:01.000">That</span>
                    <span begin="00:01.000" end="00:02.000">we</span>
                    <span begin="00:02.000" end="00:03.000">shoot</span>
                    <span begin="00:03.000" end="00:04.000">across</span>
                    <span ttm:role="x-translation">我们划过天际</span>
                  </p>
                  <p begin="00:05.000" end="00:06.000">//</p>
                </div>
              </body>
            </tt>
            """.trimIndent()
        )

        assertEquals(1, result.lyrics.size)
        assertEquals("That we shoot across", result.lyrics.single().text)
        assertEquals("我们划过天际", result.lyrics.single().translation)
        assertEquals(listOf("That", " we", " shoot", " across"), result.lyrics.single().words.map { it.text })
    }

    @Test
    fun accompanistElrcAgentPrefixesAreHiddenAndKeptAsAlignment() {
        val result = LrcParser.parse(
            """
            [00:01.000]<00:01.000>v1:<00:01.100>Hello <00:01.600>again
            [00:02.000]<00:02.000>v2:<00:02.100>Answer <00:02.600>line
            """.trimIndent()
        )

        assertEquals(listOf("Hello again", "Answer line"), result.lyrics.map { it.text })
        assertEquals(listOf("v1", "v2"), result.lyrics.map { it.agent })
        assertEquals(listOf("Hello ", "again"), result.lyrics.first().words.map { it.text })
    }
}
