package com.ella.music.data.parser

import com.ella.music.data.model.LyricLine
import com.ella.music.data.model.LyricWord
import com.mocharealm.accompanist.lyrics.core.model.ISyncedLine
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeLine
import com.mocharealm.accompanist.lyrics.core.model.synced.SyncedLine
import com.mocharealm.accompanist.lyrics.core.parser.AutoParser

internal object AccompanistLyricsParser {
    private val parser = AutoParser()

    fun parse(content: String): LrcParser.LrcResult? {
        if (!parser.canParse(content)) return null
        val syncedLyrics = runCatching { parser.parse(content) }.getOrNull() ?: return null
        val isTtmlFormat = content.contains("<tt", ignoreCase = true) &&
            content.contains("</tt", ignoreCase = true)
        val lines = syncedLyrics.lines
            .mapNotNull { toLyricLine(it, isTtmlFormat) }
            .filterNot { line ->
                val text = line.text.ifBlank { line.backgroundText.orEmpty() }
                text.isBlank() || EllaLyricsParser.isIgnorableRawLyricLine(text)
            }
            .sortedBy { it.timeMs }
            .mergeSameTimestampCompanions()
        if (lines.isEmpty()) return null
        return LrcParser.LrcResult(
            lyrics = lines,
            title = syncedLyrics.title.takeIf { it.isNotBlank() },
            artist = syncedLyrics.artists
                ?.joinToString("/") { it.name }
                ?.takeIf { it.isNotBlank() }
        )
    }

    private fun toLyricLine(line: ISyncedLine, isTtmlFormat: Boolean): LyricLine? {
        return when (line) {
            is KaraokeLine.MainKaraokeLine -> line.toMainLyricLine(isTtmlFormat)
            is KaraokeLine.AccompanimentKaraokeLine -> line.toBackgroundLyricLine(isTtmlFormat)
            is SyncedLine -> line.toPlainLyricLine()
            else -> null
        }
    }

    private fun KaraokeLine.MainKaraokeLine.toMainLyricLine(isTtmlFormat: Boolean): LyricLine? {
        val mainParts = syllables.toTimedTextParts(isTtmlFormat).withoutElrcAgentPrefix()
        val textParts = mainParts.parts
        val text = textParts.toDisplayText().trimMeaningful()
        if (text.isBlank() || EllaLyricsParser.isPlaceholderOnlyLine(text)) return null
        val background = accompanimentLines?.firstOrNull()
        val backgroundParts = background?.syllables.orEmpty().toTimedTextParts(isTtmlFormat).withoutElrcAgentPrefix().parts
        val backgroundText = backgroundParts.toDisplayText().trimMeaningful().takeUsefulSecondaryText()
        return LyricLine(
            timeMs = start.toLong().coerceAtLeast(0L),
            text = text,
            words = textParts.toLyricWords(),
            translation = translation.takeUsefulSecondaryText(),
            pronunciation = phonetic.takeUsefulSecondaryText(),
            agent = mainParts.agent ?: alignment.toEllaAgent(),
            backgroundText = backgroundText,
            backgroundWords = if (backgroundText == null) emptyList() else backgroundParts.toLyricWords(),
            backgroundTranslation = background?.translation.takeUsefulSecondaryText(),
            backgroundStartMs = background?.start?.toLong()?.coerceAtLeast(0L),
            backgroundEndMs = background?.end?.toSafeEndMs(),
            isTtml = isTtmlFormat,
            endMs = end.toSafeEndMs()
        )
    }

    private fun KaraokeLine.AccompanimentKaraokeLine.toBackgroundLyricLine(isTtmlFormat: Boolean): LyricLine? {
        val parsedParts = syllables.toTimedTextParts(isTtmlFormat).withoutElrcAgentPrefix()
        val textParts = parsedParts.parts
        val text = textParts.toDisplayText().trimMeaningful()
        if (text.isBlank() || EllaLyricsParser.isPlaceholderOnlyLine(text)) return null
        return LyricLine(
            timeMs = start.toLong().coerceAtLeast(0L),
            text = "",
            backgroundText = text,
            backgroundWords = textParts.toLyricWords(),
            backgroundTranslation = translation.takeUsefulSecondaryText(),
            backgroundStartMs = start.toLong().coerceAtLeast(0L),
            backgroundEndMs = end.toSafeEndMs(),
            agent = parsedParts.agent ?: alignment.toEllaAgent(),
            isTtml = isTtmlFormat,
            endMs = end.toSafeEndMs()
        )
    }

    private fun SyncedLine.toPlainLyricLine(): LyricLine? {
        val parsedContent = content.withoutElrcAgentPrefix()
        val text = parsedContent.text.trimMeaningful()
        if (text.isBlank() || EllaLyricsParser.isPlaceholderOnlyLine(text)) return null
        return LyricLine(
            timeMs = start.toLong().coerceAtLeast(0L),
            text = text,
            translation = translation.takeUsefulSecondaryText(),
            agent = parsedContent.agent,
            endMs = end.toSafeEndMs()
        )
    }

    private data class TimedTextPart(
        val text: String,
        val startMs: Long,
        val endMs: Long
    )

    private data class AgentTimedTextParts(
        val agent: String?,
        val parts: List<TimedTextPart>
    )

    private data class AgentText(
        val agent: String?,
        val text: String
    )

    private fun List<com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeSyllable>.toTimedTextParts(
        preserveLatinWordSpaces: Boolean
    ): List<TimedTextPart> =
        mapIndexedNotNull { index, syllable ->
            val startMs = syllable.start.toLong().coerceAtLeast(0L)
            val endMs = syllable.end.toLong().coerceAtLeast(startMs + 1L)
            val rawText = syllable.content
                .normalizeTimedTokenText(preserveLatinWordSpaces, trimEnd = index == lastIndex)
                .takeIf { it.isNotBlank() } ?: return@mapIndexedNotNull null
            val previous = getOrNull(index - 1)
            val text = if (
                preserveLatinWordSpaces &&
                previous != null &&
                shouldInsertLatinWordSpace(
                    previous.content,
                    syllable.content,
                    previous.content.normalizeTimedTokenText(preserveLatinWordSpaces, trimEnd = false),
                    rawText
                )
            ) {
                " $rawText"
            } else {
                rawText
            }
            TimedTextPart(text = text, startMs = startMs, endMs = endMs)
        }

    private fun List<TimedTextPart>.toDisplayText(): String =
        joinToString(separator = "") { it.text }

    private fun List<TimedTextPart>.withoutElrcAgentPrefix(): AgentTimedTextParts {
        if (isEmpty()) return AgentTimedTextParts(agent = null, parts = this)
        val first = first()
        val stripped = first.text.withoutElrcAgentPrefix()
        if (stripped.agent == null) return AgentTimedTextParts(agent = null, parts = this)
        val updated = buildList {
            val firstText = stripped.text
            if (firstText.isNotBlank()) {
                add(first.copy(text = firstText))
            }
            addAll(drop(1))
        }
        return AgentTimedTextParts(agent = stripped.agent, parts = updated)
    }

    private fun List<TimedTextPart>.toLyricWords(): List<LyricWord> =
        mapNotNull { part ->
            part.text.takeIf { it.isNotBlank() }?.let { text ->
                LyricWord(text = text, startMs = part.startMs, endMs = part.endMs)
            }
        }

    private fun shouldInsertLatinWordSpace(
        previousRaw: String,
        currentRaw: String,
        previousNormalized: String,
        currentNormalized: String
    ): Boolean {
        val hasExplicitWhitespaceBoundary =
            previousRaw.lastOrNull()?.isWhitespace() == true || currentRaw.firstOrNull()?.isWhitespace() == true
        if (!hasExplicitWhitespaceBoundary) return false
        val prev = previousNormalized.lastOrNull { !it.isWhitespace() } ?: return false
        val next = currentNormalized.firstOrNull { !it.isWhitespace() } ?: return false
        if (prev.isCjkWordChar() || next.isCjkWordChar()) return false
        return true
    }

    private fun Char.isCjkWordChar(): Boolean {
        val block = Character.UnicodeBlock.of(this)
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
            block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A ||
            block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B ||
            block == Character.UnicodeBlock.HIRAGANA ||
            block == Character.UnicodeBlock.KATAKANA ||
            block == Character.UnicodeBlock.HANGUL_SYLLABLES
    }

    private fun String.normalizeTimedTokenText(preserveLatinWordSpaces: Boolean, trimEnd: Boolean): String =
        if (preserveLatinWordSpaces) {
            replace(Regex("""\s+"""), " ").trim()
        } else if (trimEnd) {
            trimEnd()
        } else {
            this
        }

    private fun Int.toSafeEndMs(): Long? =
        takeIf { it in 0 until Int.MAX_VALUE }?.toLong()

    private fun String.trimMeaningful(): String =
        trim().replace(Regex("""[ \t\r\n]+"""), " ")

    private fun String.withoutElrcAgentPrefix(): AgentText {
        val match = Regex("""^\s*(v[12])\s*[:：]\s*""", RegexOption.IGNORE_CASE).find(this)
            ?: return AgentText(agent = null, text = this)
        return AgentText(
            agent = match.groupValues[1].lowercase(),
            text = removeRange(match.range)
        )
    }

    private fun String?.takeUsefulSecondaryText(): String? =
        this
            ?.trimMeaningful()
            ?.takeIf { it.isNotBlank() && !EllaLyricsParser.isPlaceholderOnlyLine(it) && !EllaLyricsParser.isIgnorableRawLyricLine(it) }

    private fun com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeAlignment.toEllaAgent(): String? =
        when (name.lowercase()) {
            "start" -> "v1"
            "end" -> "v2"
            else -> null
        }

    private fun List<LyricLine>.mergeSameTimestampCompanions(): List<LyricLine> {
        return groupBy { it.timeMs }
            .values
            .flatMap { group ->
                if (group.size == 1) return@flatMap group
                val primary = group.firstOrNull { it.words.isNotEmpty() && it.text.isUsefulMainText() }
                    ?: group.firstOrNull { it.text.isUsefulMainText() }
                    ?: group.first()
                val primaryText = primary.text.trimMeaningful()
                val primaryTranslationAsPronunciation = primary.translation
                    ?.takeIf { primaryText.hasCjk() && it.isPronunciationFor(primaryText) }
                    ?.trimMeaningful()
                val companions = group.filter { it !== primary }
                val pronunciation = companions
                    .firstOrNull { primaryText.hasCjk() && it.text.isPronunciationFor(primaryText) }
                    ?.text
                    ?.trimMeaningful()
                val translation = companions
                    .asSequence()
                    .filter { it.text.isUsefulMainText() }
                    .map { it.text.trimMeaningful() }
                    .filter { it != primaryText && it != pronunciation }
                    .distinct()
                    .joinToString("\n")
                    .takeIf { it.isNotBlank() }
                listOf(
                    primary.copy(
                        translation = primary.translation
                            ?.takeUnless { it == primaryTranslationAsPronunciation }
                            .mergeText(translation),
                        pronunciation = primary.pronunciation ?: pronunciation ?: primaryTranslationAsPronunciation,
                        endMs = group.mapNotNull { it.endMs }.maxOrNull() ?: primary.endMs
                    )
                )
            }
            .sortedBy { it.timeMs }
    }

    private fun String?.mergeText(extra: String?): String? =
        listOfNotNull(this?.takeIf { it.isNotBlank() }, extra?.takeIf { it.isNotBlank() })
            .distinct()
            .joinToString("\n")
            .takeIf { it.isNotBlank() }

    private fun String.isUsefulMainText(): Boolean =
        trimMeaningful().isNotBlank() && !EllaLyricsParser.isPlaceholderOnlyLine(this)

    private fun String.hasCjk(): Boolean =
        any { char ->
            val block = Character.UnicodeBlock.of(char)
            block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
                block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A ||
                block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B ||
                block == Character.UnicodeBlock.HIRAGANA ||
                block == Character.UnicodeBlock.KATAKANA ||
                block == Character.UnicodeBlock.HANGUL_SYLLABLES
        }

    private fun String.isPronunciationFor(primaryText: String): Boolean {
        val text = trimMeaningful()
        if (text.isBlank() || text.hasCjk()) return false
        return primaryText.hasCjk() && text.any { it.isLetter() }
    }
}
