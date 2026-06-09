package com.ella.music.data.parser

import android.os.ParcelFileDescriptor
import android.text.Html
import com.ella.music.data.model.LyricLine
import com.ella.music.data.model.LyricWord
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource

object LrcParser {

    private val timePattern = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,6}))?]""")
    private val retroLinePattern = Regex("""((?:\[.*?])+)(.*)""")
    private val wordTimePattern = Regex("""<(\d{1,3}):(\d{1,2})(?:[.:](\d{1,6}))?>""")
    private val metaDataPattern = Regex("""\[(ti|ar|al|by|offset|re|ve):\s*(.*)]""", RegexOption.IGNORE_CASE)
    private val ttmlParagraphPattern = Regex("""<p\b([^>]*)>(.*?)</p>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val ttmlSpanPattern = Regex("""<span\b([^>]*)>(.*?)</span>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val ttmlTranslationPattern = Regex("""<span\b(?=[^>]*ttm:role="x-translation")[^>]*>(.*?)</span>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val ttmlBackgroundPattern = Regex("""<span\b(?=[^>]*ttm:role="x-bg")[^>]*>.*?</span>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val xmlTagPattern = Regex("""<[^>]+>""")
    private val beginAttributePattern = Regex("\\bbegin=\"([^\"]+)\"", RegexOption.IGNORE_CASE)
    private val endAttributePattern = Regex("\\bend=\"([^\"]+)\"", RegexOption.IGNORE_CASE)

    data class LrcResult(
        val lyrics: List<LyricLine>,
        val title: String? = null,
        val artist: String? = null,
        val album: String? = null,
        val offset: Long = 0L
    )

    fun parse(lrcContent: String): LrcResult = EllaLyricsParser.parse(lrcContent)

    private fun parseTtml(content: String): LrcResult? {
        if (!Regex("""<tt(?:\s|>)""", RegexOption.IGNORE_CASE).containsMatchIn(content)) return null
        parseTtmlDom(content)?.let { return it }

        val lyrics = ttmlParagraphPattern.findAll(content)
            .mapNotNull { paragraph ->
                val attributes = paragraph.groupValues[1]
                val body = paragraph.groupValues[2]
                val begin = beginAttributePattern.find(attributes)?.groupValues?.getOrNull(1)?.parseTtmlTime()
                    ?: return@mapNotNull null
                val end = endAttributePattern.find(attributes)?.groupValues?.getOrNull(1)?.parseTtmlTime()
                val agent = Regex("\\b(?:ttm:)?agent=\"([^\"]+)\"", RegexOption.IGNORE_CASE)
                    .find(attributes)
                    ?.groupValues
                    ?.getOrNull(1)

                val translation = ttmlTranslationPattern
                    .find(body)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.stripXmlText()
                    ?.takeIf { it.isNotBlank() && !it.isMusicSymbolOnly() }

                val mainBody = body
                    .replace(ttmlTranslationPattern, "")
                    .replace(ttmlBackgroundPattern, "")
                val background = ttmlBackgroundPattern
                    .find(body)
                    ?.value
                    ?.parseTtmlBackground()
                val text = mainBody.stripXmlText()
                if (text.isMusicSymbolOnly()) return@mapNotNull null

                val words = ttmlSpanPattern.findAll(mainBody)
                    .mapNotNull { span ->
                        val spanAttributes = span.groupValues[1]
                        if (spanAttributes.contains("ttm:role=", ignoreCase = true)) return@mapNotNull null
                        val wordBegin = beginAttributePattern.find(spanAttributes)?.groupValues?.getOrNull(1)?.parseTtmlTime()
                            ?: return@mapNotNull null
                        val wordEnd = endAttributePattern.find(spanAttributes)?.groupValues?.getOrNull(1)?.parseTtmlTime()
                            ?: end
                            ?: (wordBegin + estimateWordDuration(span.groupValues[2].stripXmlText()))
                        val wordText = span.groupValues[2].stripXmlText()
                        if (wordText.isBlank()) null else LyricWord(wordText, wordBegin, wordEnd)
                    }
                    .toList()

                LyricLine(
                    timeMs = begin,
                    text = text,
                    words = words.toTtmlDisplayWords(text),
                    translation = translation,
                    agent = agent,
                    backgroundText = background?.text,
                    backgroundWords = background?.let { it.words.toTtmlDisplayWords(it.text) }.orEmpty(),
                    backgroundTranslation = background?.translation,
                    backgroundStartMs = background?.startMs,
                    backgroundEndMs = background?.endMs,
                    isTtml = true,
                    endMs = end
                )
            }
            .sortedBy { it.timeMs }
            .toList()

        return LrcResult(lyrics = lyrics)
    }

    private fun parseTtmlDom(content: String): LrcResult? {
        return try {
            val document = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                isIgnoringComments = true
                isCoalescing = true
            }.newDocumentBuilder().parse(InputSource(StringReader(content)))

            val pronunciations = parseTtmlPronunciations(document)

            val paragraphs = document.getElementsByTagNameNS("*", "p")
            val lyrics = (0 until paragraphs.length).mapNotNull { index ->
                val paragraph = paragraphs.item(index) as? Element ?: return@mapNotNull null
                val lyricKey = paragraph.attr("key")
                val pronunciation = pronunciations[lyricKey]

                val begin = paragraph.attr("begin").parseTtmlTime() ?: return@mapNotNull null
                val end = paragraph.attr("end").parseTtmlTime()
                val words = mutableListOf<LyricWord>()
                val translations = mutableListOf<String>()
                val backgrounds = mutableListOf<TtmlBackground>()
                val agent = paragraph.attr("agent")
                    .ifBlank { null }
                val text = collectTtmlText(paragraph, words, translations, backgrounds, end)
                    .replace(Regex("""[ \t\r\n]+"""), " ")
                    .trim()
                if (text.isMusicSymbolOnly()) return@mapNotNull null

                LyricLine(
                    timeMs = begin,
                    text = text,
                    words = words.toTtmlDisplayWords(text),
                    translation = translations.firstOrNull { it.isNotBlank() && !it.isMusicSymbolOnly() },
                    pronunciation = pronunciation?.text,
                    pronunciationWords = pronunciation?.words?.toTtmlDisplayWords(pronunciation.text).orEmpty(),
                    agent = agent,
                    backgroundText = backgrounds.firstOrNull { it.text.isNotBlank() && !it.text.isMusicSymbolOnly() }?.text,
                    backgroundWords = backgrounds.firstOrNull { it.words.isNotEmpty() }?.let { it.words.toTtmlDisplayWords(it.text) }.orEmpty(),
                    backgroundTranslation = backgrounds.firstOrNull { !it.translation.isNullOrBlank() }?.translation,
                    backgroundStartMs = backgrounds.firstOrNull { it.text.isNotBlank() && !it.text.isMusicSymbolOnly() }?.startMs
                        ?: backgrounds.firstOrNull { it.words.isNotEmpty() }?.startMs,
                    backgroundEndMs = backgrounds.firstOrNull { it.text.isNotBlank() && !it.text.isMusicSymbolOnly() }?.endMs
                        ?: backgrounds.firstOrNull { it.words.isNotEmpty() }?.endMs,
                    isTtml = true,
                    endMs = end
                )
            }.sortedBy { it.timeMs }

            LrcResult(lyrics = lyrics).takeIf { lyrics.isNotEmpty() }
        } catch (_: Exception) {
            null
        }
    }

    private fun parseTtmlPronunciations(document: org.w3c.dom.Document): Map<String, TtmlPronunciation> {
        val result = mutableMapOf<String, TtmlPronunciation>()
        val texts = document.getElementsByTagNameNS("*", "text")

        for (index in 0 until texts.length) {
            val textElement = texts.item(index) as? Element ?: continue
            val key = textElement.attr("for").ifBlank { continue }

            val words = mutableListOf<LyricWord>()
            val builder = StringBuilder()
            val spans = textElement.getElementsByTagNameNS("*", "span")

            for (spanIndex in 0 until spans.length) {
                val span = spans.item(spanIndex) as? Element ?: continue
                val begin = span.attr("begin").parseTtmlTime() ?: continue
                val spanText = span.textContent.cleanTtmlText()
                if (spanText.isBlank()) continue

                val end = span.attr("end").parseTtmlTime()
                    ?: (begin + estimateWordDuration(spanText))

                builder.append(spanText)
                words += LyricWord(
                    text = spanText,
                    startMs = begin,
                    endMs = end
                )
            }

            val text = builder.toString().cleanTtmlText()
            if (text.isNotBlank() && !text.isMusicSymbolOnly()) {
                result[key] = TtmlPronunciation(
                    text = text,
                    words = words
                )
            }
        }

        return result
    }

    private fun collectTtmlText(
        node: Node,
        words: MutableList<LyricWord>,
        translations: MutableList<String>,
        backgrounds: MutableList<TtmlBackground>,
        lineEndMs: Long?
    ): String {
        val builder = StringBuilder()
        val children = node.childNodes
        for (index in 0 until children.length) {
            val child = children.item(index)
            when (child.nodeType) {
                Node.TEXT_NODE -> builder.append(child.nodeValue.orEmpty().withoutFormattingWhitespace())
                Node.ELEMENT_NODE -> {
                    val element = child as? Element ?: continue
                    val role = element.attr("role")
                    when (role) {
                        "x-translation" -> translations += element.textContent.cleanTtmlText()
                        "x-bg" -> backgrounds += collectTtmlBackground(element, lineEndMs)
                        else -> {
                            val text = collectTtmlText(element, words, translations, backgrounds, lineEndMs)
                            val wordBegin = element.attr("begin").parseTtmlTime()
                            if (wordBegin != null && text.isNotBlank()) {
                                val wordEnd = element.attr("end").parseTtmlTime()
                                    ?: lineEndMs
                                    ?: (wordBegin + estimateWordDuration(text))
                                words += LyricWord(text, wordBegin, wordEnd)
                            }
                            builder.append(text)
                        }
                    }
                }
            }
        }
        return builder.toString()
    }

    private data class TtmlBackground(
        val text: String,
        val words: List<LyricWord> = emptyList(),
        val translation: String? = null,
        val startMs: Long? = null,
        val endMs: Long? = null
    )

    private data class TtmlPronunciation(
        val text: String,
        val words: List<LyricWord> = emptyList()
    )

    private fun collectTtmlBackground(element: Element, lineEndMs: Long?): TtmlBackground {
        val translations = mutableListOf<String>()
        val words = mutableListOf<LyricWord>()
        val children = element.childNodes
        for (index in 0 until children.length) {
            val child = children.item(index)
            when (child.nodeType) {
                Node.TEXT_NODE -> Unit
                Node.ELEMENT_NODE -> {
                    val childElement = child as? Element ?: continue
                    val role = childElement.attr("role")
                    if (role == "x-translation") {
                        translations += childElement.textContent.cleanTtmlText()
                    } else {
                        val childBackground = collectTtmlBackground(childElement, lineEndMs)
                        childBackground.translation?.let { translations += it }
                        words += childBackground.words

                        val wordBegin = childElement.attr("begin").parseTtmlTime()
                        if (wordBegin != null && childBackground.text.isNotBlank() && childBackground.words.isEmpty()) {
                            val wordEnd = childElement.attr("end").parseTtmlTime()
                                ?: lineEndMs
                                ?: (wordBegin + estimateWordDuration(childBackground.text))
                            words += LyricWord(childBackground.text, wordBegin, wordEnd)
                        }
                    }
                }
            }
        }
        val text = element.collectVisibleTtmlText()
            .normalizeTtmlText()
            .removeTtmlBackgroundParentheses()
        val ownBegin = element.attr("begin").parseTtmlTime()
        if (ownBegin != null && words.isEmpty() && text.isNotBlank()) {
            val ownEnd = element.attr("end").parseTtmlTime()
                ?: lineEndMs
                ?: (ownBegin + estimateWordDuration(text))
            words += LyricWord(text, ownBegin, ownEnd)
        }
        val cleanedWords = words
            .map { word -> word.copy(text = word.text.removeTtmlBackgroundParentheses()) }
            .filter { it.text.isNotBlank() }
        return TtmlBackground(
            text = text,
            words = cleanedWords.toTtmlDisplayWords(text),
            translation = translations.firstOrNull { it.isNotBlank() && !it.isMusicSymbolOnly() },
            startMs = ownBegin ?: cleanedWords.minOfOrNull { it.startMs },
            endMs = element.attr("end").parseTtmlTime() ?: cleanedWords.maxOfOrNull { it.endMs } ?: lineEndMs
        )
    }

    private fun String.parseTtmlBackground(): TtmlBackground {
        val translation = ttmlTranslationPattern
            .find(this)
            ?.groupValues
            ?.getOrNull(1)
            ?.stripXmlText()
            ?.takeIf { it.isNotBlank() && !it.isMusicSymbolOnly() }
        val text = replace(ttmlTranslationPattern, "")
            .stripXmlText()
            .removeTtmlBackgroundParentheses()
            .takeIf { it.isNotBlank() && !it.isMusicSymbolOnly() }
            .orEmpty()
        val words = ttmlSpanPattern.findAll(this)
            .mapNotNull { span ->
                val attributes = span.groupValues[1]
                if (attributes.contains("ttm:role=", ignoreCase = true)) return@mapNotNull null
                val begin = beginAttributePattern.find(attributes)?.groupValues?.getOrNull(1)?.parseTtmlTime()
                    ?: return@mapNotNull null
                val wordText = span.groupValues[2]
                    .stripXmlText()
                    .removeTtmlBackgroundParentheses()
                if (wordText.isBlank()) return@mapNotNull null
                val end = endAttributePattern.find(attributes)?.groupValues?.getOrNull(1)?.parseTtmlTime()
                    ?: (begin + estimateWordDuration(wordText))
                LyricWord(wordText, begin, end)
            }
            .toList()
        return TtmlBackground(
            text = text,
            words = words.toTtmlDisplayWords(text),
            translation = translation,
            startMs = beginAttributePattern.find(this)?.groupValues?.getOrNull(1)?.parseTtmlTime()
                ?: words.minOfOrNull { it.startMs },
            endMs = endAttributePattern.find(this)?.groupValues?.getOrNull(1)?.parseTtmlTime()
                ?: words.maxOfOrNull { it.endMs }
        )
    }

    private fun List<LyricWord>.toTtmlDisplayWords(lineText: String): List<LyricWord> {
        if (isEmpty() || lineText.isBlank()) return this
        if (lineText.hasCjk()) return withTextSpacing(lineText)

        val tokens = Regex("""\S+\s*""").findAll(lineText).toList()
        if (tokens.isEmpty()) return withTextSpacing(lineText)

        val result = mutableListOf<LyricWord>()
        var wordIndex = 0
        for (token in tokens) {
            if (wordIndex >= size) break

            val displayText = token.value
            val target = displayText.trim()
            if (target.isBlank()) continue

            val startIndex = wordIndex
            val startMs = this[startIndex].startMs
            var endMs = this[startIndex].endMs
            val builder = StringBuilder()

            while (wordIndex < size && builder.length < target.length) {
                val piece = this[wordIndex].text.trim()
                builder.append(piece)
                endMs = this[wordIndex].endMs
                wordIndex++
            }

            if (builder.toString() == target) {
                result += LyricWord(displayText, startMs, endMs)
            } else {
                return withTextSpacing(lineText)
            }
        }

        return if (result.isNotEmpty()) result else withTextSpacing(lineText)
    }

    private fun parseInlineTimedLine(line: String): LyricLine? {
        val matches = timePattern.findAll(line).toList()
        if (matches.size < 2) return null

        val timedSegments = matches.mapIndexedNotNull { index, match ->
            val start = match.range.last + 1
            val end = matches.getOrNull(index + 1)?.range?.first ?: line.length
            val text = line.substring(start, end)
            if (text.isBlank()) null else TimedSegment(parseTime(match.groupValues), text, index)
        }
        if (timedSegments.isEmpty()) return null

        val first = timedSegments.first()
        val wordSegments = if (first.text.hasCjk() && timedSegments.getOrNull(1)?.timeMs == first.timeMs) {
            timedSegments.drop(1)
        } else {
            timedSegments
        }.filter { !it.text.isMusicSymbolOnly() }

        if (wordSegments.isEmpty()) return null

        val hasWordTiming = wordSegments.size > 1
        if (!hasWordTiming) {
            val lineText = wordSegments.joinToString("") { it.text }.trim()
            val endMs = matches
                .lastOrNull()
                ?.groupValues
                ?.let(::parseTime)
                ?.takeIf { it > first.timeMs }
            return LyricLine(first.timeMs, lineText.ifBlank { first.text.trim() }, endMs = endMs)
        }

        val words = wordSegments.mapIndexed { index, segment ->
            val next = wordSegments.getOrNull(index + 1)
            val nextRawTime = matches
                .getOrNull(segment.matchIndex + 1)
                ?.groupValues
                ?.let(::parseTime)
                ?.takeIf { it > segment.timeMs }
            LyricWord(
                text = segment.text,
                startMs = segment.timeMs,
                endMs = next?.timeMs ?: nextRawTime ?: (segment.timeMs + estimateWordDuration(segment.text))
            )
        }
        val text = words.joinLyricText()
        if (text.isMusicSymbolOnly()) return null

        val translation = first.text
            .takeIf { first.text.hasCjk() && timedSegments.getOrNull(1)?.timeMs == first.timeMs }
            ?.trim()
            ?.takeIf { it.isNotBlank() && !it.isMusicSymbolOnly() }

        return LyricLine(wordSegments.first().timeMs, text, words, translation = translation)
    }

    private data class TimedSegment(
        val timeMs: Long,
        val text: String,
        val matchIndex: Int
    )

    private fun mergeSameTimestampLines(lines: List<LyricLine>): List<LyricLine> {
        return lines
            .sortedBy { it.timeMs }
            .groupBy { it.timeMs }
            .values
            .map { sameTimeLines ->
                if (sameTimeLines.size == 1) return@map sameTimeLines.first()

                val primary = sameTimeLines.firstOrNull { !it.text.isMusicSymbolOnly() }
                    ?: sameTimeLines.first()
                val pronunciation = if (sameTimeLines.size >= 3 && primary.text.hasCjk()) {
                    sameTimeLines
                        .asSequence()
                        .filter { it !== primary }
                        .firstOrNull { it.text.isPronunciationLine() }
                } else {
                    null
                }
                val translation = sameTimeLines
                    .asSequence()
                    .filter { it !== primary }
                    .filter { it !== pronunciation }
                    .map { it.text.trim() }
                    .firstOrNull { it.isNotBlank() && !it.isMusicSymbolOnly() && it != primary.text.trim() }

                primary.copy(
                    pronunciation = primary.pronunciation ?: pronunciation?.text?.trim(),
                    pronunciationWords = primary.pronunciationWords.ifEmpty { pronunciation?.words.orEmpty() },
                    translation = primary.translation ?: translation,
                    endMs = primary.endMs ?: sameTimeLines.mapNotNull { it.endMs }.maxOrNull()
                )
            }
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

    private fun List<LyricWord>.joinLyricText(): String {
        if (isEmpty()) return ""
        val raw = joinToString("") { it.text }
            .replace(Regex("""[ \t\r\n]+"""), " ")
            .trim()
        if (raw.isBlank() || raw.hasCjk() || raw.contains(' ')) return raw

        val tokens = map { it.text.trim() }.filter { it.isNotBlank() }
        return tokens.joinToString(" ")
    }

    private fun List<LyricWord>.withTextSpacing(lineText: String): List<LyricWord> {
        if (isEmpty() || lineText.isBlank()) return this

        val result = mutableListOf<LyricWord>()
        var cursor = 0

        forEachIndexed { index, word ->
            val start = lineText.indexOf(word.text, startIndex = cursor)
            if (start < 0) {
                result += word
                return@forEachIndexed
            }

            val end = start + word.text.length
            val nextText = getOrNull(index + 1)?.text
            val nextStart = if (nextText != null) lineText.indexOf(nextText, startIndex = end) else -1
            val suffix = if (nextStart > end) lineText.substring(end, nextStart) else ""
            result += word.copy(text = word.text + suffix)
            cursor = end + suffix.length
        }

        return result
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

    private fun String.parseTtmlTime(): Long? {
        val value = trim()
        if (value.isBlank()) return null

        if (value.endsWith("ms", ignoreCase = true)) {
            return value.dropLast(2).toDoubleOrNull()?.toLong()
        }
        if (value.endsWith("s", ignoreCase = true)) {
            return ((value.dropLast(1).toDoubleOrNull() ?: return null) * 1000).toLong()
        }

        val parts = value.split(":")
        return when (parts.size) {
            1 -> ((parts[0].toDoubleOrNull() ?: return null) * 1000).toLong()
            2 -> {
                val minutes = parts[0].toLongOrNull() ?: return null
                val seconds = parts[1].toDoubleOrNull() ?: return null
                (minutes * 60_000 + seconds * 1000).toLong()
            }
            3 -> {
                val hours = parts[0].toLongOrNull() ?: return null
                val minutes = parts[1].toLongOrNull() ?: return null
                val seconds = parts[2].toDoubleOrNull() ?: return null
                (hours * 3_600_000 + minutes * 60_000 + seconds * 1000).toLong()
            }
            else -> null
        }
    }

    private fun String.stripXmlText(): String {
        val withoutTags = replace(xmlTagPattern, "")
        return Html.fromHtml(withoutTags, Html.FROM_HTML_MODE_LEGACY)
            .toString()
            .cleanTtmlText()
    }

    private fun Element.attr(localName: String): String {
        val direct = getAttribute(localName).ifBlank { getAttribute("ttm:$localName") }
        if (direct.isNotBlank()) return direct

        val attributes = attributes ?: return ""
        for (index in 0 until attributes.length) {
            val item = attributes.item(index)
            if (item.localName == localName || item.nodeName.substringAfter(':') == localName) {
                return item.nodeValue.orEmpty()
            }
        }
        return ""
    }

    private fun String.cleanTtmlText(): String =
        replace(Regex("""[ \t\r\n]+"""), " ").trim()

    private fun String.normalizeTtmlText(): String =
        replace(Regex("""[ \t\r\n]+"""), " ")

    private fun String.removeTtmlBackgroundParentheses(): String =
        replace(Regex("""[()（）]"""), "")
            .replace(Regex("""[ \t\r\n]+"""), " ")
            .trim()

    private fun Element.collectVisibleTtmlText(): String {
        val builder = StringBuilder()
        val children = childNodes
        for (index in 0 until children.length) {
            val child = children.item(index)
            when (child.nodeType) {
                Node.TEXT_NODE -> builder.append(child.nodeValue.orEmpty().withoutFormattingWhitespace())
                Node.ELEMENT_NODE -> {
                    val element = child as? Element ?: continue
                    if (element.attr("role") != "x-translation") {
                        builder.append(element.collectVisibleTtmlText())
                    }
                }
            }
        }
        return builder.toString()
    }

    private fun String.completeOpenParentheticalAdlib(): String {
        val trimmed = trim()
        if (trimmed.length !in 2..24) return this
        if (!trimmed.startsWith("(") || trimmed.endsWith(")")) return this
        if (trimmed.count { it == '(' } != 1 || trimmed.any { it == ')' }) return this
        return this + ")"
    }

    private fun String.withoutFormattingWhitespace(): String {
        if (isBlank() && any { it == '\n' || it == '\r' || it == '\t' }) return ""
        return this
    }

    private fun String.hasCjk(): Boolean =
        any { char ->
            Character.UnicodeBlock.of(char) in setOf(
                Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS,
                Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A,
                Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B,
                Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS,
                Character.UnicodeBlock.HIRAGANA,
                Character.UnicodeBlock.KATAKANA,
                Character.UnicodeBlock.HANGUL_SYLLABLES
            )
        }

    private fun String.isPronunciationLine(): Boolean {
        val cleaned = replace(Regex("""[ \t\r\n]+"""), " ").trim()
        if (cleaned.isBlank() || cleaned.hasCjk() || cleaned.isMusicSymbolOnly()) return false
        val letters = cleaned.count { it.isLetter() }
        return letters >= 2 && cleaned.all { it.isLetter() || it.isWhitespace() || it in "-'`." }
    }

    private fun String.isMusicSymbolOnly(): Boolean {
        val content = trim()
        if (content.isBlank()) return true
        return content.all { char ->
            char.isWhitespace() ||
                char in setOf('♪', '♫', '♬', '♩', '♭', '♯', '♮') ||
                Character.UnicodeBlock.of(char) == Character.UnicodeBlock.MUSICAL_SYMBOLS
        }
    }

    private val lyricExtensions = listOf("lrc", "ttml", "elrc", "spl")

    fun findLrcFile(songPath: String): String? {
        val baseName = songPath.substringBeforeLast('.')
        // Exact name match: try each extension in playback preference order.
        for (ext in lyricExtensions) {
            readViaFd("$baseName.$ext")?.let { return it }
        }

        val parentDir = File(songPath).parentFile
        if (parentDir != null) {
            val songName = File(songPath).nameWithoutExtension
            try {
                // Fuzzy match: find any lyric file whose name contains the song name
                parentDir.listFiles()?.filter {
                    it.extension.equals("lrc", ignoreCase = true) ||
                        it.extension.equals("ttml", ignoreCase = true) ||
                        it.extension.equals("elrc", ignoreCase = true) ||
                        it.extension.equals("spl", ignoreCase = true)
                }?.sortedWith(
                    compareBy<File> { lyricExtensions.indexOf(it.extension.lowercase()) }
                        .thenBy { it.name }
                )?.find {
                    it.nameWithoutExtension.contains(songName, ignoreCase = true)
                }?.let { readViaFd(it.absolutePath)?.let { text -> return text } }
            } catch (_: Exception) {}
        }

        return null
    }

    private fun readViaFd(path: String): String? {
        return try {
            val file = File(path)
            if (!file.exists()) return null
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                FileInputStream(pfd.fileDescriptor).use { fis ->
                    val bytes = fis.readBytes()
                    if (bytes.isEmpty()) return null
                    readTextWithFallback(bytes)
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun readTextWithFallback(bytes: ByteArray): String {
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
