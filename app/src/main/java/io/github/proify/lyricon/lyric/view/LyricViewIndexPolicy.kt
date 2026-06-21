package io.github.proify.lyricon.lyric.view

internal data class LyricViewLineWindow(
    val begin: Long,
    val end: Long
)

private const val PREVIEW_OFFSET_MIN_MS = 480L
private const val PREVIEW_OFFSET_MAX_MS = 750L
private const val PREVIEW_OFFSET_GAP_MIN_MS = 200L
private const val PREVIEW_OFFSET_GAP_MAX_MS = 750L
private const val POSITION_JITTER_TOLERANCE_MS = 32L

internal fun previewLyricViewIndexAt(
    effectivePositionMs: Long,
    lines: List<LyricViewLineWindow>
): Int {
    if (lines.isEmpty()) return -1
    for (index in lines.indices.reversed()) {
        val line = lines[index]
        if (effectivePositionMs >= line.begin && effectivePositionMs < line.end) {
            return index
        }
    }
    for (index in lines.indices.reversed()) {
        if (lines[index].begin <= effectivePositionMs) {
            return index
        }
    }
    return -1
}

internal fun computeLyricViewPreviewOffsetMs(
    currentIndex: Int,
    lines: List<LyricViewLineWindow>
): Long {
    if (currentIndex !in lines.indices || currentIndex + 1 >= lines.size) {
        return PREVIEW_OFFSET_MIN_MS
    }
    val gap = lines[currentIndex + 1].begin - lines[currentIndex].end
    val clampedGap = gap.coerceIn(PREVIEW_OFFSET_GAP_MIN_MS, PREVIEW_OFFSET_GAP_MAX_MS)
    val fraction =
        (clampedGap - PREVIEW_OFFSET_GAP_MIN_MS).toFloat() /
            (PREVIEW_OFFSET_GAP_MAX_MS - PREVIEW_OFFSET_GAP_MIN_MS)
    return (
        PREVIEW_OFFSET_MIN_MS +
            (PREVIEW_OFFSET_MAX_MS - PREVIEW_OFFSET_MIN_MS) * fraction
        ).toLong()
}

internal fun resolveLyricViewIndex(
    positionMs: Long,
    previousPositionMs: Long,
    currentIndex: Int,
    currentPreviewOffsetMs: Long,
    lines: List<LyricViewLineWindow>
): Int {
    if (lines.isEmpty()) return -1

    val monotonicPlayback = positionMs + POSITION_JITTER_TOLERANCE_MS >= previousPositionMs
    if (monotonicPlayback && currentIndex in lines.indices) {
        var candidate = currentIndex
        while (candidate + 1 < lines.size) {
            val nextBegin = lines[candidate + 1].begin
            val previewOffset = computeLyricViewPreviewOffsetMs(candidate, lines)
            if (positionMs + previewOffset < nextBegin) break
            candidate++
        }
        return candidate
    }

    return previewLyricViewIndexAt(positionMs + currentPreviewOffsetMs, lines)
}
