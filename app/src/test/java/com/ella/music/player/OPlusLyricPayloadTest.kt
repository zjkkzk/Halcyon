package com.ella.music.player

import com.ella.music.data.model.LyricLine
import com.ella.music.data.model.LyricWord
import com.ella.music.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OPlusLyricPayloadTest {
    @Test
    fun payloadSplitsNativeLyricWordTimedRawLyricAndTranslation() {
        val song = song()
        val payload = OPlusLyricPayload.build(
            song = song,
            lyrics = listOf(
                LyricLine(
                    timeMs = 1_000L,
                    text = "Hello world",
                    words = listOf(
                        LyricWord("Hello", 1_000L, 1_500L),
                        LyricWord("world", 1_500L, 2_200L)
                    ),
                    translation = "你好世界",
                    endMs = 2_200L
                )
            )
        )

        val json = payload ?: error("payload is null")
        assertEquals("[00:01.00]Hello world", OPlusLyricPayload.stringField(json, "lyric"))
        assertEquals(
            "[00:01.000]Hello [00:01.500]world[00:02.200]\n[00:01.000]你好世界",
            OPlusLyricPayload.stringField(json, "rawLyric")
        )
        assertEquals(null, OPlusLyricPayload.stringField(json, "translationLyric"))
        assertTrue(OPlusLyricPayload.matchesSong(json, song))
    }

    @Test
    fun translationIsEmbeddedInRawLyricForModuleWordModel() {
        val payload = OPlusLyricPayload.build(
            song = song(),
            lyrics = listOf(
                LyricLine(
                    timeMs = 2_000L,
                    text = "Sincronia; Ascenso; Lazo",
                    words = listOf(
                        LyricWord("Sincronia; ", 2_000L, 2_500L),
                        LyricWord("Ascenso; ", 2_500L, 3_000L),
                        LyricWord("Lazo", 3_000L, 3_500L)
                    ),
                    translation = "同步，飞升，链接",
                    endMs = 3_500L
                )
            )
        )

        val json = payload ?: error("payload is null")
        assertEquals(
            "[00:02.000]Sincronia; [00:02.500]Ascenso; [00:03.000]Lazo[00:03.500]\n" +
                "[00:02.000]同步，飞升，链接",
            OPlusLyricPayload.stringField(json, "rawLyric")
        )
        assertEquals(null, OPlusLyricPayload.stringField(json, "translationLyric"))
    }

    @Test
    fun rawLyricFallsBackToPlainTimedLineWhenWordsAreMissing() {
        val payload = OPlusLyricPayload.build(
            song = song(),
            lyrics = listOf(LyricLine(timeMs = 2_345L, text = "Plain line"))
        )

        val json = payload ?: error("payload is null")
        assertEquals("[00:02.34]Plain line", OPlusLyricPayload.stringField(json, "lyric"))
        assertEquals("[00:02.345]Plain line", OPlusLyricPayload.stringField(json, "rawLyric"))
        assertEquals(null, OPlusLyricPayload.stringField(json, "translationLyric"))
    }

    private fun song(): Song = Song(
        id = 42L,
        title = "Test Song",
        artist = "Test Artist",
        album = "Test Album",
        albumId = 7L,
        duration = 180_000L,
        path = "/music/test.flac",
        fileName = "test.flac"
    )
}
