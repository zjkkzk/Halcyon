package com.ella.music.data.artwork

import java.io.ByteArrayInputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class Mp4EmbeddedArtworkExtractorTest {

    @Test
    fun `extract returns covr data payload from nested mp4 atoms`() {
        val payload = "ftypavis-test-payload".encodeToByteArray()
        val fileBytes = box("free", byteArrayOf(1, 2, 3)) +
            box(
                "moov",
                box(
                    "udta",
                    fullMetaBox(
                        box(
                            "ilst",
                            box(
                                "covr",
                                dataBox(payload)
                            )
                        )
                    )
                )
            )

        val extracted = Mp4EmbeddedArtworkExtractor.extract(ByteArrayInputStream(fileBytes))

        assertArrayEquals(payload, extracted)
    }

    @Test
    fun `extract supports extended size boxes`() {
        val payload = "extended-size-payload".encodeToByteArray()
        val fileBytes = extendedBox(
            "moov",
            box(
                "udta",
                fullMetaBox(
                    box(
                        "ilst",
                        box(
                            "covr",
                            dataBox(payload)
                        )
                    )
                )
            )
        )

        val extracted = Mp4EmbeddedArtworkExtractor.extract(ByteArrayInputStream(fileBytes))

        assertArrayEquals(payload, extracted)
    }

    @Test
    fun `extracted payload still sniffs as avif sequence`() {
        val payload = ftypPayload(majorBrand = "avis", compatibleBrands = listOf("mif1", "avif"))
        val fileBytes = box(
            "moov",
            box(
                "udta",
                fullMetaBox(
                    box(
                        "ilst",
                        box(
                            "covr",
                            dataBox(payload)
                        )
                    )
                )
            )
        )

        val extracted = Mp4EmbeddedArtworkExtractor.extract(ByteArrayInputStream(fileBytes))

        assertEquals(EmbeddedArtworkKind.AVIF_SEQUENCE, sniffEmbeddedArtworkKind(requireNotNull(extracted)))
    }

    private fun box(type: String, payload: ByteArray): ByteArray {
        val size = payload.size + 8
        return int32(size) + type.encodeToByteArray() + payload
    }

    private fun extendedBox(type: String, payload: ByteArray): ByteArray {
        val size = payload.size.toLong() + 16L
        return int32(1) + type.encodeToByteArray() + int64(size) + payload
    }

    private fun fullMetaBox(childPayload: ByteArray): ByteArray =
        box("meta", byteArrayOf(0, 0, 0, 0) + childPayload)

    private fun dataBox(payload: ByteArray): ByteArray =
        box("data", byteArrayOf(0, 0, 0, 1, 0, 0, 0, 0) + payload)

    private fun ftypPayload(
        majorBrand: String,
        compatibleBrands: List<String>
    ): ByteArray {
        val size = 16 + compatibleBrands.size * 4
        val bytes = mutableListOf<Byte>()
        bytes += int32(size).toList()
        bytes += "ftyp".encodeToByteArray().toList()
        bytes += majorBrand.encodeToByteArray().toList()
        bytes += byteArrayOf(0, 0, 0, 0).toList()
        compatibleBrands.forEach { brand -> bytes += brand.encodeToByteArray().toList() }
        return bytes.toByteArray()
    }

    private fun int32(value: Int): ByteArray = byteArrayOf(
        ((value ushr 24) and 0xFF).toByte(),
        ((value ushr 16) and 0xFF).toByte(),
        ((value ushr 8) and 0xFF).toByte(),
        (value and 0xFF).toByte()
    )

    private fun int64(value: Long): ByteArray = byteArrayOf(
        ((value ushr 56) and 0xFF).toByte(),
        ((value ushr 48) and 0xFF).toByte(),
        ((value ushr 40) and 0xFF).toByte(),
        ((value ushr 32) and 0xFF).toByte(),
        ((value ushr 24) and 0xFF).toByte(),
        ((value ushr 16) and 0xFF).toByte(),
        ((value ushr 8) and 0xFF).toByte(),
        (value and 0xFF).toByte()
    )
}
