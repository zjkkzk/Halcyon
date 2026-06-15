package com.ella.music.data.model

data class LyricLine(
    val timeMs: Long,
    val text: String,
    val words: List<LyricWord> = emptyList(),
    val translation: String? = null,
    val pronunciation: String? = null,
    val pronunciationWords: List<LyricWord> = emptyList(),
    val agent: String? = null,
    val agentName: String? = null,
    val backgroundText: String? = null,
    val backgroundWords: List<LyricWord> = emptyList(),
    val backgroundTranslation: String? = null,
    val backgroundStartMs: Long? = null,
    val backgroundEndMs: Long? = null,
    val isTtml: Boolean = false,
    val endMs: Long? = null
)

data class LyricWord(
    val text: String,
    val startMs: Long,
    val endMs: Long
)

fun LyricLine.primaryEndMs(
    nextLineStartMs: Long? = null,
    fallbackDurationMs: Long = 4_000L
): Long {
    if (text.isBlank() && !backgroundText.isNullOrBlank()) {
        return (backgroundEndMs
            ?: backgroundWords.maxOfOrNull { it.endMs }
            ?: endMs
            ?: nextLineStartMs
            ?: (timeMs + fallbackDurationMs))
            .coerceAtLeast(timeMs + 1L)
    }

    val mainEnd = words.maxOfOrNull { it.endMs } ?: endMs
    val cappedEnd = when {
        nextLineStartMs == null -> mainEnd
        mainEnd == null -> nextLineStartMs
        mainEnd > nextLineStartMs -> nextLineStartMs
        else -> mainEnd
    }
    return (cappedEnd ?: timeMs + fallbackDurationMs).coerceAtLeast(timeMs + 1L)
}

fun List<LyricLine>.shiftedBy(offsetMs: Long): List<LyricLine> {
    if (offsetMs == 0L || isEmpty()) return this
    fun Long.shift() = (this + offsetMs).coerceAtLeast(0L)
    fun Long?.shiftNullable() = this?.let { (it + offsetMs).coerceAtLeast(0L) }
    fun LyricWord.shifted() = copy(startMs = startMs.shift(), endMs = endMs.shift())

    return map { line ->
        line.copy(
            timeMs = line.timeMs.shift(),
            words = line.words.map { it.shifted() },
            pronunciationWords = line.pronunciationWords.map { it.shifted() },
            backgroundWords = line.backgroundWords.map { it.shifted() },
            backgroundStartMs = line.backgroundStartMs.shiftNullable(),
            backgroundEndMs = line.backgroundEndMs.shiftNullable(),
            endMs = line.endMs.shiftNullable()
        )
    }
}
