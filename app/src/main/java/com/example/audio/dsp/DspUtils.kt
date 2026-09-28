package com.example.audio.dsp

import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.tanh

object DspUtils {

    fun dbToGain(db: Float): Float {
        return 10.0f.pow(db / 20.0f)
    }

    fun gainToDb(gain: Float): Float {
        val g = max(1e-5f, gain)
        return (20.0 * log10(g.toDouble())).toFloat()
    }

    /**
     * DC Offset Blocker: High-pass filter with cutoff at ~10 Hz to prevent DC accumulation.
     */
    class DcBlocker(private val r: Float = 0.995f) {
        private var xPrev = 0f
        private var yPrev = 0f

        fun process(x: Float): Float {
            val y = x - xPrev + r * yPrev
            xPrev = x
            yPrev = y
            return y
        }

        fun reset() {
            xPrev = 0f
            yPrev = 0f
        }
    }

    /**
     * Soft-knee analog-style saturation.
     */
    fun softSaturate(input: Float, drive: Float): Float {
        val boosted = input * (1.0f + drive * 2.0f)
        return if (boosted > 1.5f) {
            1.0f
        } else if (boosted < -1.5f) {
            -1.0f
        } else {
            tanh(boosted.toDouble()).toFloat()
        }
    }

    /**
     * Cubic Hermite interpolation for smooth, anti-aliased scrub and pitch changes.
     */
    fun interpolateHermite(y0: Float, y1: Float, y2: Float, y3: Float, frac: Float): Float {
        val c0 = y1
        val c1 = 0.5f * (y2 - y0)
        val c2 = y0 - 2.5f * y1 + 2.0f * y2 - 0.5f * y3
        val c3 = 0.5f * (y3 - y0) + 1.5f * (y1 - y2)
        return ((c3 * frac + c2) * frac + c1) * frac + c0
    }

    /**
     * Linear interpolation.
     */
    fun interpolateLinear(y0: Float, y1: Float, frac: Float): Float {
        return y0 + frac * (y1 - y0)
    }

    /**
     * True peak lookahead peak limiter helper.
     */
    fun hardLimit(sample: Float, ceiling: Float = 0.99f): Float {
        return min(ceiling, max(-ceiling, sample))
    }
}
