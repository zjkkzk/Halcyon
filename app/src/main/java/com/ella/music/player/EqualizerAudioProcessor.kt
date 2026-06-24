package com.ella.music.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import com.ella.music.dsp.TenBandEqualizer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicReference

/**
 * Immutable snapshot of the custom equalizer settings.
 *
 * Settings are stored as dB values to match the DSP core. The UI stores millibels,
 * so the owner of this processor must convert before calling [setSettings].
 */
data class EqualizerSettings(
    val enabled: Boolean = false,
    val bandGainsDb: FloatArray = FloatArray(TenBandEqualizer.BAND_COUNT) { 0f }
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EqualizerSettings) return false
        return enabled == other.enabled && bandGainsDb.contentEquals(other.bandGainsDb)
    }

    override fun hashCode(): Int {
        var result = enabled.hashCode()
        result = 31 * result + bandGainsDb.contentHashCode()
        return result
    }
}

/**
 * ExoPlayer [AudioProcessor] that applies a custom 10-band parametric EQ.
 *
 * This runs entirely in software and does not depend on the system [android.media.audiofx.Equalizer].
 * The DSP core is a Kotlin port of the BiQuad/ParametricEQ implementation from RawS-Music.
 */
@UnstableApi
class EqualizerAudioProcessor : AudioProcessor {

    private var inputAudioFormat: AudioProcessor.AudioFormat = AudioProcessor.AudioFormat.NOT_SET
    private var outputAudioFormat: AudioProcessor.AudioFormat = AudioProcessor.AudioFormat.NOT_SET

    private val settingsRef = AtomicReference(EqualizerSettings())
    private var equalizer: TenBandEqualizer? = null

    private var outputBuffer: ByteBuffer = EMPTY_BUFFER
    private var inputEnded = false

    private var tempFloatBuffer: FloatArray = FloatArray(0)

    private var reusableOutputBuffer: ByteBuffer? = null

    fun setSettings(settings: EqualizerSettings) {
        settingsRef.set(settings)
    }

    override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT
            || inputAudioFormat.channelCount !in 1..2
        ) {
            this.inputAudioFormat = AudioProcessor.AudioFormat.NOT_SET
            this.outputAudioFormat = AudioProcessor.AudioFormat.NOT_SET
            equalizer = null
            return AudioProcessor.AudioFormat.NOT_SET
        }

        this.inputAudioFormat = inputAudioFormat
        this.outputAudioFormat = inputAudioFormat

        equalizer = TenBandEqualizer(
            inputAudioFormat.sampleRate,
            inputAudioFormat.channelCount
        )
        applySettings()
        return outputAudioFormat
    }

    override fun isActive(): Boolean {
        // Always active so enabling/disabling the EQ does not require rebuilding the audio sink.
        // The DSP core bypasses itself when disabled.
        return inputAudioFormat != AudioProcessor.AudioFormat.NOT_SET
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val position = inputBuffer.position()
        val limit = inputBuffer.limit()
        val remaining = limit - position
        if (remaining == 0) {
            return
        }

        val eq = equalizer ?: return
        applySettings()

        val channels = inputAudioFormat.channelCount
        val frames = remaining / (channels * BYTES_PER_SAMPLE)
        val requiredFloats = frames * channels
        if (tempFloatBuffer.size < requiredFloats) {
            tempFloatBuffer = FloatArray(requiredFloats)
        }

        // 16-bit PCM -> float [-1.0, 1.0]
        for (i in 0 until requiredFloats) {
            val sample = inputBuffer.getShort(position + i * BYTES_PER_SAMPLE).toInt()
            tempFloatBuffer[i] = sample / 32768f
        }

        eq.process(tempFloatBuffer, frames)

        // float -> 16-bit PCM, matching RawS-Music's conversion sign handling.
        outputBuffer = replaceOutputBuffer(remaining)
        outputBuffer.order(ByteOrder.nativeOrder())
        for (i in 0 until requiredFloats) {
            val clamped = tempFloatBuffer[i].coerceIn(-1f, 1f)
            val intSample = if (clamped < 0) {
                (clamped * 32768f).toInt()
            } else {
                (clamped * 32767f).toInt()
            }
            outputBuffer.putShort(intSample.toShort())
        }
        outputBuffer.flip()

        inputBuffer.position(limit)
    }

    override fun queueEndOfStream() {
        inputEnded = true
    }

    override fun getOutput(): ByteBuffer {
        val output = outputBuffer
        outputBuffer = EMPTY_BUFFER
        return output
    }

    override fun isEnded(): Boolean {
        return inputEnded && outputBuffer === EMPTY_BUFFER
    }

    override fun flush() {
        outputBuffer = EMPTY_BUFFER
        inputEnded = false
        equalizer?.reset()
        applySettings(force = true)
    }

    override fun reset() {
        flush()
        inputAudioFormat = AudioProcessor.AudioFormat.NOT_SET
        outputAudioFormat = AudioProcessor.AudioFormat.NOT_SET
        equalizer = null
        reusableOutputBuffer = null
    }

    private fun applySettings(force: Boolean = false) {
        val eq = equalizer ?: return
        val settings = settingsRef.get()
        if (!force && settings == lastAppliedSettings) return

        eq.setEnabled(settings.enabled)
        settings.bandGainsDb.forEachIndexed { index, gainDb ->
            eq.setBandGain(index, gainDb)
        }
        lastAppliedSettings = settings
    }

    private var lastAppliedSettings: EqualizerSettings? = null

    private fun replaceOutputBuffer(count: Int): ByteBuffer {
        val existing = reusableOutputBuffer
        return if (existing != null && existing.capacity() >= count) {
            existing.clear()
            existing.limit(count)
            existing
        } else {
            ByteBuffer.allocateDirect(count).order(ByteOrder.nativeOrder()).also {
                reusableOutputBuffer = it
            }
        }
    }

    companion object {
        private val EMPTY_BUFFER: ByteBuffer = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())
        private const val BYTES_PER_SAMPLE = 2
    }
}
