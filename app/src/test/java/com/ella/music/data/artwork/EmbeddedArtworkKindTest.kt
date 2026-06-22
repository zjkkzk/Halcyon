package com.ella.music.data.artwork

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbeddedArtworkKindTest {

    @Test
    fun `sniffEmbeddedArtworkKind detects avif sequence brand`() {
        val payload = ftypPayload(majorBrand = "avis", compatibleBrands = listOf("mif1", "avif"))

        assertEquals(EmbeddedArtworkKind.AVIF_SEQUENCE, sniffEmbeddedArtworkKind(payload))
    }

    @Test
    fun `sniffEmbeddedArtworkKind detects avif still brand`() {
        val payload = ftypPayload(majorBrand = "avif", compatibleBrands = listOf("mif1"))

        assertEquals(EmbeddedArtworkKind.AVIF_STILL, sniffEmbeddedArtworkKind(payload))
    }

    @Test
    fun `avif sequence is blocked from static bitmap chain`() {
        assertEquals(
            StaticArtworkPolicy.BLOCK_DYNAMIC_ONLY,
            EmbeddedArtworkKind.AVIF_SEQUENCE.staticArtworkPolicy()
        )
        assertFalse(EmbeddedArtworkKind.AVIF_SEQUENCE.supportsStaticBitmapDecoding())
    }

    @Test
    fun `jpeg and png artwork policies remain on direct bytes path`() {
        assertEquals(EmbeddedArtworkKind.JPEG, sniffEmbeddedArtworkKind(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0x00)))
        assertEquals(StaticArtworkPolicy.DIRECT_BYTES, EmbeddedArtworkKind.JPEG.staticArtworkPolicy())

        val png = byteArrayOf(
            0x89.toByte(),
            0x50,
            0x4E,
            0x47,
            0x0D,
            0x0A,
            0x1A,
            0x0A
        )
        assertEquals(EmbeddedArtworkKind.PNG, sniffEmbeddedArtworkKind(png))
        assertEquals(StaticArtworkPolicy.DIRECT_BYTES, EmbeddedArtworkKind.PNG.staticArtworkPolicy())
        assertTrue(EmbeddedArtworkKind.PNG.supportsStaticBitmapDecoding())
    }

    private fun ftypPayload(
        majorBrand: String,
        compatibleBrands: List<String>
    ): ByteArray {
        val payload = mutableListOf<Byte>()
        val size = 16 + compatibleBrands.size * 4
        payload.add(((size ushr 24) and 0xFF).toByte())
        payload.add(((size ushr 16) and 0xFF).toByte())
        payload.add(((size ushr 8) and 0xFF).toByte())
        payload.add((size and 0xFF).toByte())
        payload.addAll("ftyp".encodeToByteArray().toList())
        payload.addAll(majorBrand.encodeToByteArray().toList())
        payload.addAll(byteArrayOf(0, 0, 0, 0).toList())
        compatibleBrands.forEach { brand ->
            payload.addAll(brand.encodeToByteArray().toList())
        }
        return payload.toByteArray()
    }
}
