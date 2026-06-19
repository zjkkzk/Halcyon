package io.github.proify.lyricon.lyric.view

import android.graphics.Typeface

interface UpdatableColor {
    fun updateColor(primary: IntArray, background: IntArray, highlight: IntArray)
}

enum class PlaceholderFormat {
    NAME,
    NAME_ARTIST
}

data class RichLyricLineConfig(
    val primary: TextConfig = TextConfig(textSize = 28f, typeface = Typeface.DEFAULT_BOLD),
    val secondary: TextConfig = TextConfig(textSize = 20f, typeface = Typeface.DEFAULT),
    val syllable: SyllableConfig = SyllableConfig(),
    val placeholderFormat: PlaceholderFormat = PlaceholderFormat.NAME_ARTIST,
    val enableAnim: Boolean = true
)

data class TextConfig(
    val textSize: Float = 0f,
    val textColor: IntArray = intArrayOf(),
    val typeface: Typeface = Typeface.DEFAULT
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TextConfig) return false
        return textSize == other.textSize &&
            textColor.contentEquals(other.textColor) &&
            typeface == other.typeface
    }

    override fun hashCode(): Int {
        var result = textSize.hashCode()
        result = 31 * result + textColor.contentHashCode()
        result = 31 * result + typeface.hashCode()
        return result
    }
}

data class SyllableConfig(
    val highlightColor: IntArray = intArrayOf(),
    val backgroundColor: IntArray = intArrayOf()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SyllableConfig) return false
        return highlightColor.contentEquals(other.highlightColor) &&
            backgroundColor.contentEquals(other.backgroundColor)
    }

    override fun hashCode(): Int {
        var result = highlightColor.contentHashCode()
        result = 31 * result + backgroundColor.contentHashCode()
        return result
    }
}
