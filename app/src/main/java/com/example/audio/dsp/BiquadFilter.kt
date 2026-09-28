package com.example.audio.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

enum class FilterType {
    LOW_PASS,
    HIGH_PASS,
    BAND_PASS,
    PEAKING,
    LOW_SHELF,
    HIGH_SHELF,
    NOTCH,
    ALL_PASS
}

class BiquadFilter(
    private var sampleRate: Int = 48000
) {
    // Coefficients
    private var b0 = 1.0f
    private var b1 = 0.0f
    private var b2 = 0.0f
    private var a1 = 0.0f
    private var a2 = 0.0f

    // Channel delay states (Left and Right)
    private var x1L = 0f; private var x2L = 0f; private var y1L = 0f; private var y2L = 0f
    private var x1R = 0f; private var x2R = 0f; private var y1R = 0f; private var y2R = 0f

    fun setSampleRate(sr: Int) {
        if (sr > 0) sampleRate = sr
    }

    fun configure(type: FilterType, frequencyHz: Float, q: Float = 0.707f, gainDb: Float = 0f) {
        val f0 = frequencyHz.coerceIn(10f, (sampleRate * 0.49f))
        val omega = (2.0 * PI * f0 / sampleRate).toDouble()
        val sn = sin(omega)
        val cs = cos(omega)
        val alpha = sn / (2.0 * q.coerceAtLeast(0.01f))
        val aGain = Math.pow(10.0, (gainDb / 40.0).toDouble()) // for shelving & peaking

        var a0 = 1.0
        var tb0 = 1.0
        var tb1 = 0.0
        var tb2 = 0.0
        var ta1 = 0.0
        var ta2 = 0.0

        when (type) {
            FilterType.LOW_PASS -> {
                tb0 = (1.0 - cs) / 2.0
                tb1 = 1.0 - cs
                tb2 = (1.0 - cs) / 2.0
                a0 = 1.0 + alpha
                ta1 = -2.0 * cs
                ta2 = 1.0 - alpha
            }
            FilterType.HIGH_PASS -> {
                tb0 = (1.0 + cs) / 2.0
                tb1 = -(1.0 + cs)
                tb2 = (1.0 + cs) / 2.0
                a0 = 1.0 + alpha
                ta1 = -2.0 * cs
                ta2 = 1.0 - alpha
            }
            FilterType.BAND_PASS -> {
                tb0 = alpha
                tb1 = 0.0
                tb2 = -alpha
                a0 = 1.0 + alpha
                ta1 = -2.0 * cs
                ta2 = 1.0 - alpha
            }
            FilterType.PEAKING -> {
                tb0 = 1.0 + alpha * aGain
                tb1 = -2.0 * cs
                tb2 = 1.0 - alpha * aGain
                a0 = 1.0 + alpha / aGain
                ta1 = -2.0 * cs
                ta2 = 1.0 - alpha / aGain
            }
            FilterType.LOW_SHELF -> {
                val sq = 2.0 * sqrt(aGain) * alpha
                tb0 = aGain * ((aGain + 1.0) - (aGain - 1.0) * cs + sq)
                tb1 = 2.0 * aGain * ((aGain - 1.0) - (aGain + 1.0) * cs)
                tb2 = aGain * ((aGain + 1.0) - (aGain - 1.0) * cs - sq)
                a0 = (aGain + 1.0) + (aGain - 1.0) * cs + sq
                ta1 = -2.0 * ((aGain - 1.0) + (aGain + 1.0) * cs)
                ta2 = (aGain + 1.0) + (aGain - 1.0) * cs - sq
            }
            FilterType.HIGH_SHELF -> {
                val sq = 2.0 * sqrt(aGain) * alpha
                tb0 = aGain * ((aGain + 1.0) + (aGain - 1.0) * cs + sq)
                tb1 = -2.0 * aGain * ((aGain - 1.0) + (aGain + 1.0) * cs)
                tb2 = aGain * ((aGain + 1.0) + (aGain - 1.0) * cs - sq)
                a0 = (aGain + 1.0) - (aGain - 1.0) * cs + sq
                ta1 = 2.0 * ((aGain - 1.0) - (aGain + 1.0) * cs)
                ta2 = (aGain + 1.0) - (aGain - 1.0) * cs - sq
            }
            FilterType.NOTCH -> {
                tb0 = 1.0
                tb1 = -2.0 * cs
                tb2 = 1.0
                a0 = 1.0 + alpha
                ta1 = -2.0 * cs
                ta2 = 1.0 - alpha
            }
            FilterType.ALL_PASS -> {
                tb0 = 1.0 - alpha
                tb1 = -2.0 * cs
                tb2 = 1.0 + alpha
                a0 = 1.0 + alpha
                ta1 = -2.0 * cs
                ta2 = 1.0 - alpha
            }
        }

        b0 = (tb0 / a0).toFloat()
        b1 = (tb1 / a0).toFloat()
        b2 = (tb2 / a0).toFloat()
        a1 = (ta1 / a0).toFloat()
        a2 = (ta2 / a0).toFloat()
    }

    fun processLeft(input: Float): Float {
        val out = b0 * input + b1 * x1L + b2 * x2L - a1 * y1L - a2 * y2L
        x2L = x1L
        x1L = input
        y2L = y1L
        y1L = out
        return out
    }

    fun processRight(input: Float): Float {
        val out = b0 * input + b1 * x1R + b2 * x2R - a1 * y1R - a2 * y2R
        x2R = x1R
        x1R = input
        y2R = y1R
        y1R = out
        return out
    }

    fun reset() {
        x1L = 0f; x2L = 0f; y1L = 0f; y2L = 0f
        x1R = 0f; x2R = 0f; y1R = 0f; y2R = 0f
    }
}
