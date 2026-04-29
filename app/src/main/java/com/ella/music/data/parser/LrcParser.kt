package com.ella.music.data.parser

import com.ella.music.data.model.LyricLine
import com.ella.music.data.model.LyricWord
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

object LrcParser {

    private val timePattern = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")
    private val wordTimePattern = Regex("""<(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?>""")
    private val metaDataPattern = Regex("""\[(ti|ar|al|by|offset|re|ve):\s*(.*)]""", RegexOption.IGNORE_CASE)

    data class LrcResult(
        val lyrics: List<LyricLine>,
        val title: String? = null,
        val artist: String? = null,
        val album: String? = null,
        val offset: Long = 0L
    )

    fun parse(lrcContent: String): LrcResult {
        val lines = lrcContent.lines()
        val lyrics = mutableListOf<LyricLine>()
        var title: String? = null
        var artist: String? = null
        var album: String? = null
        var offset = 0L

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            val metaMatch = metaDataPattern.find(trimmed)
            if (metaMatch != null) {
                when (metaMatch.groupValues[1].lowercase()) {
                    "ti" -> title = metaMatch.groupValues[2].trim()
                    "ar" -> artist = metaMatch.groupValues[2].trim()
                    "al" -> album = metaMatch.groupValues[2].trim()
                    "offset" -> offset = metaMatch.groupValues[2].trim().toLongOrNull() ?: 0L
                }
                continue
            }

            val timeMatches = timePattern.findAll(trimmed).toList()
            if (timeMatches.isEmpty()) continue

            val textStart = timeMatches.last().range.last + 1
            val text = if (textStart < trimmed.length) trimmed.substring(textStart) else ""

            val hasWordTiming = wordTimePattern.containsMatchIn(text)

            for (timeMatch in timeMatches) {
                val timeMs = parseTime(timeMatch.groupValues)

                if (hasWordTiming) {
                    val words = parseEnhancedWords(text, timeMs)
                    val fullText = words.joinToString("") { it.text }
                    lyrics.add(LyricLine(timeMs, fullText, words))
                } else {
                    lyrics.add(LyricLine(timeMs, text.trim()))
                }
            }
        }

        return LrcResult(
            lyrics = lyrics.sortedBy { it.timeMs },
            title = title,
            artist = artist,
            album = album,
            offset = offset
        )
    }

    private fun parseEnhancedWords(text: String, lineStartMs: Long): List<LyricWord> {
        val words = mutableListOf<LyricWord>()
        val matches = wordTimePattern.findAll(text).toList()

        for (i in matches.indices) {
            val current = matches[i]
            val next = matches.getOrNull(i + 1)
            val wordText = text
                .substring(current.range.last + 1, next?.range?.first ?: text.length)
                .replace(wordTimePattern, "")
            if (wordText.isNotEmpty()) {
                val startMs = parseTime(current.groupValues)
                val endMs = if (next != null) {
                    parseTime(next.groupValues)
                } else {
                    startMs + estimateWordDuration(wordText)
                }
                words.add(LyricWord(wordText, startMs, endMs))
            }
        }

        if (words.isEmpty() && text.isNotBlank()) {
            words.add(LyricWord(text, lineStartMs, lineStartMs + 1000))
        }

        return words
    }

    private fun estimateWordDuration(text: String): Long {
        return (text.length * 150L).coerceIn(200L, 2000L)
    }

    private fun parseTime(groups: List<String>): Long {
        val minutes = groups[1].toLongOrNull() ?: 0L
        val seconds = groups[2].toLongOrNull() ?: 0L
        val millisStr = groups.getOrNull(3).orEmpty()
        val millis = when (millisStr.length) {
            1 -> (millisStr.toLongOrNull() ?: 0L) * 100
            2 -> (millisStr.toLongOrNull() ?: 0L) * 10
            3 -> millisStr.toLongOrNull() ?: 0L
            else -> 0L
        }
        return minutes * 60_000 + seconds * 1000 + millis
    }

    fun findLrcFile(songPath: String): String? {
        val baseName = songPath.substringBeforeLast('.')
        val candidates = listOf("$baseName.lrc", "${baseName}.LRC")
        for (candidate in candidates) {
            val file = File(candidate)
            if (file.exists()) return readTextWithFallback(file)
        }

        val parentDir = File(songPath).parentFile ?: return null
        val songName = File(songPath).nameWithoutExtension
        parentDir.listFiles()?.find {
            it.extension.equals("lrc", ignoreCase = true) &&
                it.nameWithoutExtension.contains(songName, ignoreCase = true)
        }?.let { return readTextWithFallback(it) }

        return null
    }

    private fun readTextWithFallback(file: File): String {
        val bytes = file.readBytes()
        val charsets = listOf("UTF-8", "GB18030", "UTF-16LE", "UTF-16BE")
        for (charsetName in charsets) {
            val charset = Charset.forName(charsetName)
            try {
                val decoder = charset.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                return decoder.decode(ByteBuffer.wrap(bytes)).toString()
            } catch (_: CharacterCodingException) {
            }
        }
        return String(bytes, Charsets.UTF_8)
    }
}
