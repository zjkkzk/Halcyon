package com.ella.music.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * Single-channel biquad IIR filter.
 *
 * Ported from the BiQuad implementation in RawS-Music's dsp_engine.cpp.
 * Uses double precision for coefficient calculation and float for runtime state.
 */
class BiQuadFilter {

    private var b0 = 1.0f
    private var b1 = 0.0f
    private var b2 = 0.0f
    private var a1 = 0.0f
    private var a2 = 0.0f

    // State per channel: [channel][x1, x2, y1, y2]
    private val state = Array(2) { FloatArray(4) }

    fun reset() {
        b0 = 1.0f
        b1 = 0.0f
        b2 = 0.0f
        a1 = 0.0f
        a2 = 0.0f
        state.forEach { it.fill(0.0f) }
    }

    /**
     * Configure as a peaking EQ using the standard RBJ formulas.
     *
     * @param sampleRate sample rate in Hz
     * @param frequency center frequency in Hz
     * @param q quality factor
     * @param gainDb gain in dB
     */
    fun setPeakEQ(sampleRate: Float, frequency: Float, q: Float, gainDb: Float) {
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = (2.0 * PI * frequency / sampleRate).coerceIn(0.00000001, 3.0013)
        val sinW0 = sin(w0)
        val cosW0 = cos(w0)
        val alpha = sinW0 / (2.0 * q.coerceAtLeast(0.00000001f).toDouble())

        val b0d = 1.0 + alpha * a
        val b1d = -2.0 * cosW0
        val b2d = 1.0 - alpha * a
        val a0d = 1.0 + alpha / a
        val a1d = -2.0 * cosW0
        val a2d = 1.0 - alpha / a

        setCoeffs(b0d, b1d, b2d, a0d, a1d, a2d)
    }

    private fun setCoeffs(b0d: Double, b1d: Double, b2d: Double, a0d: Double, a1d: Double, a2d: Double) {
        b0 = (b0d / a0d).toFloat()
        b1 = (b1d / a0d).toFloat()
        b2 = (b2d / a0d).toFloat()
        a1 = (a1d / a0d).toFloat()
        a2 = (a2d / a0d).toFloat()
    }

    /**
     * Process one sample for the given channel.
     *
     * @param input input sample in [-1.0, 1.0]
     * @param channel channel index (0 or 1)
     */
    fun processSample(input: Float, channel: Int): Float {
        val s = state[channel]
        val output = b0 * input + b1 * s[0] + b2 * s[1] - a1 * s[2] - a2 * s[3]
        s[1] = s[0]
        s[0] = input
        s[3] = s[2]
        s[2] = output
        return output
    }
}
