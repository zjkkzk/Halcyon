package com.ella.music.data.artwork

enum class EmbeddedArtworkKind {
    JPEG,
    PNG,
    WEBP,
    HEIF,
    AVIF_STILL,
    AVIF_SEQUENCE,
    UNKNOWN
}

internal enum class StaticArtworkPolicy {
    DIRECT_BYTES,
    SAFE_STILL_IMAGE,
    BLOCK_DYNAMIC_ONLY
}

internal fun EmbeddedArtworkKind.mimeType(): String? =
    when (this) {
        EmbeddedArtworkKind.JPEG -> "image/jpeg"
        EmbeddedArtworkKind.PNG -> "image/png"
        EmbeddedArtworkKind.WEBP -> "image/webp"
        EmbeddedArtworkKind.HEIF -> "image/heif"
        EmbeddedArtworkKind.AVIF_STILL -> "image/avif"
        EmbeddedArtworkKind.AVIF_SEQUENCE -> "image/avif-sequence"
        EmbeddedArtworkKind.UNKNOWN -> null
    }

internal fun EmbeddedArtworkKind.supportsStaticBitmapDecoding(): Boolean =
    this != EmbeddedArtworkKind.AVIF_SEQUENCE

internal fun EmbeddedArtworkKind.staticArtworkPolicy(): StaticArtworkPolicy =
    when (this) {
        EmbeddedArtworkKind.JPEG,
        EmbeddedArtworkKind.PNG,
        EmbeddedArtworkKind.WEBP,
        EmbeddedArtworkKind.UNKNOWN -> StaticArtworkPolicy.DIRECT_BYTES

        EmbeddedArtworkKind.HEIF,
        EmbeddedArtworkKind.AVIF_STILL -> StaticArtworkPolicy.SAFE_STILL_IMAGE

        EmbeddedArtworkKind.AVIF_SEQUENCE -> StaticArtworkPolicy.BLOCK_DYNAMIC_ONLY
    }

internal fun sniffEmbeddedArtworkKind(data: ByteArray): EmbeddedArtworkKind {
    if (data.size >= 3 &&
        (data[0].toInt() and 0xFF) == 0xFF &&
        (data[1].toInt() and 0xFF) == 0xD8 &&
        (data[2].toInt() and 0xFF) == 0xFF
    ) {
        return EmbeddedArtworkKind.JPEG
    }
    if (
        data.size >= 8 &&
        (data[0].toInt() and 0xFF) == 0x89 &&
        data[1] == 0x50.toByte() &&
        data[2] == 0x4E.toByte() &&
        data[3] == 0x47.toByte() &&
        data[4] == 0x0D.toByte() &&
        data[5] == 0x0A.toByte() &&
        data[6] == 0x1A.toByte() &&
        data[7] == 0x0A.toByte()
    ) {
        return EmbeddedArtworkKind.PNG
    }
    if (
        data.size >= 12 &&
        data[0] == 'R'.code.toByte() &&
        data[1] == 'I'.code.toByte() &&
        data[2] == 'F'.code.toByte() &&
        data[3] == 'F'.code.toByte() &&
        data[8] == 'W'.code.toByte() &&
        data[9] == 'E'.code.toByte() &&
        data[10] == 'B'.code.toByte() &&
        data[11] == 'P'.code.toByte()
    ) {
        return EmbeddedArtworkKind.WEBP
    }

    val brands = parseIsobmffBrands(data) ?: return EmbeddedArtworkKind.UNKNOWN
    val allBrands = buildSet {
        add(brands.majorBrand)
        addAll(brands.compatibleBrands)
    }
    return when {
        "avis" in allBrands -> EmbeddedArtworkKind.AVIF_SEQUENCE
        "avif" in allBrands -> EmbeddedArtworkKind.AVIF_STILL
        allBrands.any { it in HEIF_BRANDS } -> EmbeddedArtworkKind.HEIF
        else -> EmbeddedArtworkKind.UNKNOWN
    }
}

private data class IsobmffBrands(
    val majorBrand: String,
    val compatibleBrands: Set<String>
)

private val HEIF_BRANDS = setOf("mif1", "msf1", "heic", "heix", "hevc", "hevx")

private fun parseIsobmffBrands(data: ByteArray): IsobmffBrands? {
    if (data.size < 16) return null
    if (
        data[4] != 'f'.code.toByte() ||
        data[5] != 't'.code.toByte() ||
        data[6] != 'y'.code.toByte() ||
        data[7] != 'p'.code.toByte()
    ) {
        return null
    }
    val majorBrand = data.readAscii(8, 4)
    if (majorBrand.isBlank()) return null
    val compatible = buildSet {
        var offset = 16
        while (offset + 4 <= data.size) {
            add(data.readAscii(offset, 4))
            offset += 4
        }
    }.filter { it.isNotBlank() }.toSet()
    return IsobmffBrands(majorBrand = majorBrand, compatibleBrands = compatible)
}

private fun ByteArray.readAscii(offset: Int, length: Int): String {
    if (offset < 0 || offset + length > size) return ""
    return buildString(length) {
        for (index in offset until offset + length) {
            append(this@readAscii[index].toInt().toChar())
        }
    }.trim()
}
