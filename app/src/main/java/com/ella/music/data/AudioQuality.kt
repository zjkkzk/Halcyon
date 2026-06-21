package com.ella.music.data

import com.ella.music.data.model.AudioInfo
import java.util.Locale

data class AudioQualitySummary(
    val compactLabel: String,
    val detailLabel: String,
    val listTag: String?,
    val analyticsLabel: String,
    val showMobius: Boolean
)

/**
 * Compact Dolby mark built from the right/left half black-circle glyphs (◗◖), mimicking the
 * facing "double-D" Dolby logo. Used as the list tag and as the player-badge prefix for
 * Dolby (AC3 / E-AC-3) streams.
 */
const val DOLBY_MARK = "◗◖"

fun audioQualitySummary(info: AudioInfo): AudioQualitySummary {
    val normalizedFormat = normalizedAudioFormat(info.format)
    val bitDepth = normalizedBitDepth(info)
    val isDolby = normalizedFormat in setOf("AC3", "EC3", "EAC3")
    val isSurround = isDolby || info.channels >= 6
    val isMq = bitDepth >= 24 && info.sampleRate >= 192_000
    val isHiRes = bitDepth >= 24 && info.sampleRate >= 48_000
    val isAppleLossless = normalizedFormat == "ALAC"
    val isLossless = isAppleLossless || normalizedFormat in setOf("FLAC", "WAV", "APE")
    val isSq = isLossless && info.sampleRate >= 44_100 && bitDepth >= 16
    val isKnownLossy = normalizedFormat in setOf("MP3", "AAC", "M4A", "OGG", "OPUS")
    // Player badge: "◖◗ Dolby Atmos" for Dolby, plain "Surround" otherwise.
    val surroundLabel = if (isDolby) "$DOLBY_MARK Dolby Atmos" else "Surround"
    // List tag: just the compact Dolby mark, or "SUR" for generic multichannel.
    val surroundTag = if (isDolby) DOLBY_MARK else "SUR"
    // Analytics keeps the codec name so format grouping/ordering stays intact.
    val surroundAnalytics = normalizedFormat.takeIf { isDolby } ?: "Surround"
    val compact = when {
        isSurround -> surroundLabel
        isMq -> "MQ"
        isAppleLossless -> "Apple Lossless"
        isHiRes -> "Hi-Res"
        isSq -> "Lossless"
        info.bitRate >= 319_000 -> "HQ"
        info.bitRate > 0 || isKnownLossy -> "LQ"
        else -> normalizedFormat.ifBlank { "Audio" }
    }
    val tag = when {
        isSurround -> surroundTag
        isMq -> "MQ"
        isHiRes -> "HR"
        isSq -> "SQ"
        info.bitRate >= 319_000 -> "HQ"
        info.bitRate > 0 || isKnownLossy -> "LQ"
        else -> null
    }
    val analytics = when {
        isSurround -> surroundAnalytics
        isMq -> "MQ"
        isHiRes -> "Hi-Res"
        isSq -> "无损"
        info.bitRate >= 319_000 -> "HQ"
        info.bitRate > 0 || isKnownLossy -> "LQ"
        else -> "未知"
    }
    return AudioQualitySummary(
        compactLabel = compact,
        detailLabel = detailedAudioInfo(info, bitDepth),
        listTag = tag,
        analyticsLabel = analytics,
        showMobius = isAppleLossless || isSq || isHiRes || isMq
    )
}

fun detailedAudioInfo(info: AudioInfo): String = detailedAudioInfo(info, normalizedBitDepth(info))

fun normalizedBitDepth(info: AudioInfo): Int {
    val format = normalizedAudioFormat(info.format)
    val likelyHiResAlac = format == "ALAC" &&
        info.sampleRate >= 48_000 &&
        info.bitRate >= 1_000_000
    if (info.bitDepth > 0) {
        return if (info.bitDepth < 24 && likelyHiResAlac) 24 else info.bitDepth
    }
    if (format in setOf("FLAC", "ALAC", "WAV", "APE")) {
        if (info.sampleRate >= 88_200 || info.bitRate >= 1_600_000) return 24
        val channels = info.channels.coerceAtLeast(2)
        val pcmRate = info.sampleRate.toLong() * channels
        if (pcmRate > 0 && info.bitRate > 0) {
            val bitsPerSample = info.bitRate.toLong() / pcmRate
            if (bitsPerSample >= 12) return 24
        }
        return 16
    }
    return 0
}

fun normalizedAudioFormat(raw: String): String {
    val value = raw.uppercase()
    return when {
        "EAC3" in value || "E-AC-3" in value || "EC-3" in value || "AUDIO/EAC3" in value -> "EC3"
        "AC3" in value || "AC-3" in value || "AUDIO/AC3" in value -> "AC3"
        "ALAC" in value -> "ALAC"
        "FLAC" in value -> "FLAC"
        "AAC" in value -> "AAC"
        "M4A" in value || "MP4" in value -> "M4A"
        "MP3" in value || "MPEG" in value -> "MP3"
        "WAV" in value || "PCM" in value -> "WAV"
        "OPUS" in value -> "OPUS"
        "OGG" in value -> "OGG"
        else -> value.ifBlank { "AUDIO" }
    }
}

private fun detailedAudioInfo(info: AudioInfo, bitDepth: Int): String {
    val parts = mutableListOf<String>()
    parts += normalizedAudioFormat(info.format).lowercase()
    if (info.sampleRate > 0) parts += if (info.sampleRate % 1000 == 0) {
        "${info.sampleRate / 1000}kHz"
    } else {
        "%.1fkHz".format(info.sampleRate / 1000f)
    }
    if (bitDepth > 0) parts += "${bitDepth}bits"
    if (info.channels > 0) parts += "${info.channels}ch"
    info.replayGainDb?.let { gain ->
        parts += String.format(Locale.US, "%+.2f dB", gain)
    }
    return parts.joinToString(" / ")
}
