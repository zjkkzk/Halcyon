package com.ella.music.player

import org.junit.Assert.assertEquals
import org.junit.Test

class OPlusLyricPublishPolicyTest {
    @Test
    fun writesWhenLyricInfoIsMissing() {
        assertEquals(
            OPlusLyricPublishAction.Write,
            OPlusLyricPublishPolicy.actionFor(
                currentLyricInfo = null,
                currentRawLyric = null,
                targetLyricInfo = """{"lyric":"[00:01.00]Hi"}""",
                targetRawLyric = "[00:01.000]Hi"
            )
        )
    }

    @Test
    fun skipsWhenLyricInfoAlreadyMatches() {
        assertEquals(
            OPlusLyricPublishAction.None,
            OPlusLyricPublishPolicy.actionFor(
                currentLyricInfo = """{"lyric":"[00:01.00]Hi"}""",
                currentRawLyric = "[00:01.000]Hi",
                targetLyricInfo = """{"lyric":"[00:01.00]Hi"}""",
                targetRawLyric = "[00:01.000]Hi"
            )
        )
    }

    @Test
    fun writesWhenRawLyricChanges() {
        assertEquals(
            OPlusLyricPublishAction.Write,
            OPlusLyricPublishPolicy.actionFor(
                currentLyricInfo = """{"lyric":"[00:01.00]Hi"}""",
                currentRawLyric = "[00:01.000]Hi",
                targetLyricInfo = """{"lyric":"[00:01.00]Hi"}""",
                targetRawLyric = "[00:01.000]H[00:01.200]i"
            )
        )
    }

    @Test
    fun clearsWhenTargetHasNoLyrics() {
        assertEquals(
            OPlusLyricPublishAction.Clear,
            OPlusLyricPublishPolicy.actionFor(
                currentLyricInfo = """{"lyric":"[00:01.00]Hi"}""",
                currentRawLyric = "[00:01.000]Hi",
                targetLyricInfo = null,
                targetRawLyric = null
            )
        )
    }

    @Test
    fun skipsClearWhenAlreadyEmpty() {
        assertEquals(
            OPlusLyricPublishAction.None,
            OPlusLyricPublishPolicy.actionFor(
                currentLyricInfo = null,
                currentRawLyric = null,
                targetLyricInfo = null,
                targetRawLyric = null
            )
        )
    }
}
