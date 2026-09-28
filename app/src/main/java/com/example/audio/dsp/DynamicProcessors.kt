package com.example.audio.dsp

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Feed-forward dynamic range compressor.
 */
class AudioCompressor(
    private var sampleRate: Int = 48000
) {
    var thresholdDb: Float = -12.0f
    var ratio: Float = 3.0f
    var attackMs: Float = 20.0f
        set(value) { field = value; updateCoeffs() }
    var releaseMs: Float = 120.0f
        set(value) { field = value; updateCoeffs() }
    var makeupGainDb: Float = 0.0f

    private var attackCoeff = 0.0f
    private var releaseCoeff = 0.0f
    private var envelope = 0.0f

    init {
        updateCoeffs()
    }

    fun setSampleRate(sr: Int) {
        if (sr > 0) {
            sampleRate = sr
            updateCoeffs()
        }
    }

    private fun updateCoeffs() {
        attackCoeff = exp(-1.0f / (attackMs * 0.001f * sampleRate))
        releaseCoeff = exp(-1.0f / (releaseMs * 0.001f * sampleRate))
    }

    fun process(inputL: Float, inputR: Float): Pair<Float, Float> {
        val peak = max(abs(inputL), abs(inputR))
        val peakDb = DspUtils.gainToDb(peak)

        val targetEnv = if (peakDb > thresholdDb) {
            val overDb = peakDb - thresholdDb
            val gainReductionDb = overDb * (1.0f - 1.0f / ratio)
            DspUtils.dbToGain(-gainReductionDb)
        } else {
            1.0f
        }

        envelope = if (targetEnv < envelope) {
            attackCoeff * envelope + (1.0f - attackCoeff) * targetEnv
        } else {
            releaseCoeff * envelope + (1.0f - releaseCoeff) * targetEnv
        }

        val makeup = DspUtils.dbToGain(makeupGainDb)
        val finalGain = envelope * makeup
        return Pair(inputL * finalGain, inputR * finalGain)
    }

    fun reset() {
        envelope = 1.0f
    }
}

/**
 * Lookahead Brickwall Peak Limiter.
 */
class LookaheadLimiter(
    private var sampleRate: Int = 48000,
    lookaheadMs: Float = 3.0f
) {
    var thresholdDb: Float = -0.5f
    var ceilingDb: Float = -0.1f
    var releaseMs: Float = 60.0f
        set(value) { field = value; updateRelease() }

    private var bufferSize = ((lookaheadMs * 0.001f) * sampleRate).toInt().coerceAtLeast(16)
    private var delayBufferL = FloatArray(bufferSize)
    private var delayBufferR = FloatArray(bufferSize)
    private var bufferIdx = 0

    private var releaseCoeff = 0.0f
    private var envelope = 1.0f

    init {
        updateRelease()
    }

    fun setSampleRate(sr: Int) {
        if (sr > 0) {
            sampleRate = sr
            bufferSize = ((3.0f * 0.001f) * sampleRate).toInt().coerceAtLeast(16)
            delayBufferL = FloatArray(bufferSize)
            delayBufferR = FloatArray(bufferSize)
            bufferIdx = 0
            updateRelease()
        }
    }

    private fun updateRelease() {
        releaseCoeff = exp(-1.0f / (releaseMs * 0.001f * sampleRate))
    }

    fun process(inputL: Float, inputR: Float): Pair<Float, Float> {
        val peak = max(abs(inputL), abs(inputR))
        val thresholdGain = DspUtils.dbToGain(thresholdDb)

        val targetGain = if (peak > thresholdGain) {
            thresholdGain / peak
        } else {
            1.0f
        }

        envelope = if (targetGain < envelope) {
            targetGain // instantaneous attack
        } else {
            releaseCoeff * envelope + (1.0f - releaseCoeff) * targetGain
        }

        // Delay buffer
        val delayedL = delayBufferL[bufferIdx]
        val delayedR = delayBufferR[bufferIdx]

        delayBufferL[bufferIdx] = inputL
        delayBufferR[bufferIdx] = inputR
        bufferIdx = (bufferIdx + 1) % bufferSize

        val ceiling = DspUtils.dbToGain(ceilingDb)
        val outL = (delayedL * envelope).coerceIn(-ceiling, ceiling)
        val outR = (delayedR * envelope).coerceIn(-ceiling, ceiling)

        return Pair(outL, outR)
    }

    fun reset() {
        delayBufferL.fill(0f)
        delayBufferR.fill(0f)
        bufferIdx = 0
        envelope = 1.0f
    }
}

/**
 * Mid/Side Stereo Imager with mono bass protection.
 */
class StereoProcessor(sampleRate: Int = 48000) {
    var width: Float = 1.15f // 0.0 = Mono, 1.0 = Normal, 2.0 = Wide

    private val monoBassFilter = BiquadFilter(sampleRate)

    init {
        // High-pass on the Side signal below 90 Hz keeps sub bass punchy and dead-center
        monoBassFilter.configure(FilterType.HIGH_PASS, 90f, 0.707f)
    }

    fun setSampleRate(sr: Int) {
        monoBassFilter.setSampleRate(sr)
        monoBassFilter.configure(FilterType.HIGH_PASS, 90f, 0.707f)
    }

    fun process(left: Float, right: Float): Pair<Float, Float> {
        val mid = (left + right) * 0.5f
        var side = (left - right) * 0.5f

        // High-pass the side channel to ensure 0-90Hz stays 100% mono
        side = monoBassFilter.processLeft(side) * width

        val outL = mid + side
        val outR = mid - side
        return Pair(outL, outR)
    }

    fun reset() {
        monoBassFilter.reset()
    }
}

/**
 * Transient Shaper: Separates fast transients from sustain.
 */
class TransientShaper(sampleRate: Int = 48000) {
    var attack: Float = 0.0f  // -1.0 .. +1.0
    var sustain: Float = 0.0f // -1.0 .. +1.0

    private val fastCoeff = exp(-1.0f / (0.002f * sampleRate))
    private val slowCoeff = exp(-1.0f / (0.040f * sampleRate))
    private var fastEnv = 0.0f
    private var slowEnv = 0.0f

    fun process(left: Float, right: Float): Pair<Float, Float> {
        val peak = max(abs(left), abs(right))
        fastEnv = fastCoeff * fastEnv + (1.0f - fastCoeff) * peak
        slowEnv = slowCoeff * slowEnv + (1.0f - slowCoeff) * peak

        val transDiff = fastEnv - slowEnv
        val transGain = 1.0f + attack * 1.5f * (transDiff / (fastEnv + 0.001f)).coerceIn(0f, 2f)
        val sustainGain = 1.0f + sustain * (slowEnv / (fastEnv + 0.001f)).coerceIn(0f, 1.5f)

        val totalGain = (transGain * sustainGain).coerceIn(0.2f, 3.0f)
        return Pair(left * totalGain, right * totalGain)
    }

    fun reset() {
        fastEnv = 0f
        slowEnv = 0f
    }
}
