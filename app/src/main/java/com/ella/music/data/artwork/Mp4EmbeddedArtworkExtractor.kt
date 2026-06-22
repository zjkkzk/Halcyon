package com.ella.music.data.artwork

import java.io.BufferedInputStream
import java.io.EOFException
import java.io.InputStream

internal object Mp4EmbeddedArtworkExtractor {
    private val artworkPath = arrayOf("moov", "udta", "meta", "ilst", "covr", "data")

    fun extract(input: InputStream): ByteArray? {
        val stream = if (input is BufferedInputStream) input else BufferedInputStream(input)
        while (true) {
            val header = readBoxHeader(stream) ?: return null
            if (header.type == artworkPath.first()) {
                return findArtworkData(
                    input = stream,
                    containerSize = header.payloadSize,
                    pathIndex = 1
                )
            }
            skipFully(stream, header.payloadSize)
        }
    }

    private fun findArtworkData(
        input: InputStream,
        containerSize: Long,
        pathIndex: Int
    ): ByteArray? {
        var remaining = containerSize
        while (remaining >= BOX_HEADER_SIZE) {
            val header = readBoxHeader(input) ?: return null
            if (header.size > remaining) {
                skipQuietly(input, remaining - header.headerSize)
                return null
            }
            remaining -= header.headerSize
            val payloadSize = header.payloadSize
            val expectedType = artworkPath.getOrNull(pathIndex)
            val result = if (header.type == expectedType) {
                when (header.type) {
                    "meta" -> {
                        if (payloadSize < META_FULL_BOX_EXTRA_BYTES) {
                            skipFully(input, payloadSize)
                            null
                        } else {
                            skipFully(input, META_FULL_BOX_EXTRA_BYTES)
                            findArtworkData(
                                input = input,
                                containerSize = payloadSize - META_FULL_BOX_EXTRA_BYTES,
                                pathIndex = pathIndex + 1
                            )
                        }
                    }

                    "data" -> readDataPayload(input, payloadSize)
                    else -> findArtworkData(
                        input = input,
                        containerSize = payloadSize,
                        pathIndex = pathIndex + 1
                    )
                }
            } else {
                skipFully(input, payloadSize)
                null
            }
            remaining -= payloadSize
            if (result != null) return result
        }
        if (remaining > 0) skipQuietly(input, remaining)
        return null
    }

    private fun readDataPayload(input: InputStream, payloadSize: Long): ByteArray? {
        if (payloadSize < DATA_BOX_METADATA_BYTES) {
            skipFully(input, payloadSize)
            return null
        }
        skipFully(input, DATA_BOX_METADATA_BYTES)
        val dataSize = (payloadSize - DATA_BOX_METADATA_BYTES).toIntSafe() ?: return null
        return readFully(input, dataSize)
    }

    private fun readBoxHeader(input: InputStream): BoxHeader? {
        val basicHeader = ByteArray(BOX_HEADER_SIZE)
        val bytesRead = input.read(basicHeader)
        if (bytesRead < 0) return null
        if (bytesRead != BOX_HEADER_SIZE) throw EOFException("Incomplete MP4 box header")

        val size32 = basicHeader.readUInt32(0)
        val type = basicHeader.readAscii(4, 4)
        if (type.length != 4) return null

        return if (size32 == 1L) {
            val extendedSizeBytes = readFully(input, EXTENDED_SIZE_BYTES)
            val extendedSize = extendedSizeBytes.readUInt64(0)
            if (extendedSize < EXTENDED_HEADER_SIZE.toLong()) return null
            BoxHeader(
                type = type,
                size = extendedSize,
                headerSize = EXTENDED_HEADER_SIZE.toLong()
            )
        } else {
            if (size32 < BOX_HEADER_SIZE.toLong()) return null
            BoxHeader(
                type = type,
                size = size32,
                headerSize = BOX_HEADER_SIZE.toLong()
            )
        }
    }

    private fun readFully(input: InputStream, length: Int): ByteArray {
        val data = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val read = input.read(data, offset, length - offset)
            if (read < 0) throw EOFException("Unexpected end of stream")
            offset += read
        }
        return data
    }

    private fun skipFully(input: InputStream, bytes: Long) {
        if (bytes <= 0L) return
        var remaining = bytes
        while (remaining > 0L) {
            val skipped = input.skip(remaining)
            if (skipped > 0L) {
                remaining -= skipped
                continue
            }
            if (input.read() < 0) throw EOFException("Unexpected end of stream while skipping")
            remaining--
        }
    }

    private fun skipQuietly(input: InputStream, bytes: Long) {
        runCatching { skipFully(input, bytes) }
    }

    private fun Long.toIntSafe(): Int? =
        takeIf { it in 0..Int.MAX_VALUE.toLong() }?.toInt()

    private fun ByteArray.readUInt32(offset: Int): Long =
        ((this[offset].toLong() and 0xFF) shl 24) or
            ((this[offset + 1].toLong() and 0xFF) shl 16) or
            ((this[offset + 2].toLong() and 0xFF) shl 8) or
            (this[offset + 3].toLong() and 0xFF)

    private fun ByteArray.readUInt64(offset: Int): Long =
        ((this[offset].toLong() and 0xFF) shl 56) or
            ((this[offset + 1].toLong() and 0xFF) shl 48) or
            ((this[offset + 2].toLong() and 0xFF) shl 40) or
            ((this[offset + 3].toLong() and 0xFF) shl 32) or
            ((this[offset + 4].toLong() and 0xFF) shl 24) or
            ((this[offset + 5].toLong() and 0xFF) shl 16) or
            ((this[offset + 6].toLong() and 0xFF) shl 8) or
            (this[offset + 7].toLong() and 0xFF)

    private fun ByteArray.readAscii(offset: Int, length: Int): String =
        buildString(length) {
            for (index in offset until offset + length) {
                append(this@readAscii[index].toInt().toChar())
            }
        }

    private data class BoxHeader(
        val type: String,
        val size: Long,
        val headerSize: Long
    ) {
        val payloadSize: Long get() = size - headerSize
    }

    private const val BOX_HEADER_SIZE = 8
    private const val EXTENDED_HEADER_SIZE = 16
    private const val EXTENDED_SIZE_BYTES = 8
    private const val META_FULL_BOX_EXTRA_BYTES = 4L
    private const val DATA_BOX_METADATA_BYTES = 8L
}
