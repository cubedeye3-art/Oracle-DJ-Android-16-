package com.example.audio.fx

import com.example.audio.dsp.BiquadFilter
import com.example.audio.dsp.FilterType
import com.example.audio.model.BeatDivision
import com.example.audio.model.FxType
import com.example.audio.model.FxUnitState
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tanh

class FxRack(private var sampleRate: Int = 48000) {

    var state: FxUnitState = FxUnitState()
    var currentBpm: Double = 126.0

    // Delay lines (max 4 seconds buffer at 192kHz = ~768,000 samples)
    private val maxDelaySamples = 4 * 192000
    private val delayBufferL = FloatArray(maxDelaySamples)
    private val delayBufferR = FloatArray(maxDelaySamples)
    private var writeIndex = 0

    // Reverb comb filters & allpass
    private val combDelays = intArrayOf(1557, 1617, 1491, 1422, 1277, 1188)
    private val combBuffers = Array(6) { FloatArray(3000) }
    private val combIndices = IntArray(6)

    // Filter LFO & Phaser filters
    private val lfoFilter = BiquadFilter(sampleRate)
    private val phaserFilter1 = BiquadFilter(sampleRate)
    private val phaserFilter2 = BiquadFilter(sampleRate)

    // LFO phase
    private var lfoPhase = 0.0

    // Tape stop state
    private var tapeStopSpeed = 1.0f

    // Beat repeat / roll buffer
    private val loopBufferL = FloatArray(48000 * 2)
    private val loopBufferR = FloatArray(48000 * 2)
    private var loopCaptureLen = 0
    private var loopPlayIndex = 0

    fun setSampleRate(sr: Int) {
        if (sr > 0) {
            sampleRate = sr
            lfoFilter.setSampleRate(sr)
            phaserFilter1.setSampleRate(sr)
            phaserFilter2.setSampleRate(sr)
        }
    }

    private fun getSyncedDelaySamples(division: BeatDivision): Int {
        val beatSeconds = 60.0 / currentBpm
        val seconds = beatSeconds * division.multiplier
        return (seconds * sampleRate).toInt().coerceIn(1, maxDelaySamples - 10)
    }

    fun processBlock(
        inL: FloatArray,
        inR: FloatArray,
        outL: FloatArray,
        outR: FloatArray,
        frameCount: Int
    ) {
        if (!state.enabled || state.dryWet <= 0.001f) {
            System.arraycopy(inL, 0, outL, 0, frameCount)
            System.arraycopy(inR, 0, outR, 0, frameCount)
            return
        }

        val dryWet = state.dryWet.coerceIn(0f, 1f)
        val p1 = state.param1.coerceIn(0f, 1f)
        val p2 = state.param2.coerceIn(0f, 1f)
        val p3 = state.param3.coerceIn(0f, 1f)

        when (state.type) {
            FxType.DELAY -> processDelay(inL, inR, outL, outR, frameCount, dryWet, p1, p2, p3)
            FxType.ECHO -> processEcho(inL, inR, outL, outR, frameCount, dryWet, p1, p2, p3)
            FxType.REVERB -> processReverb(inL, inR, outL, outR, frameCount, dryWet, p1, p2, p3)
            FxType.FILTER_LFO -> processFilterLfo(inL, inR, outL, outR, frameCount, dryWet, p1, p2, p3)
            FxType.FLANGER -> processFlanger(inL, inR, outL, outR, frameCount, dryWet, p1, p2, p3)
            FxType.PHASER -> processPhaser(inL, inR, outL, outR, frameCount, dryWet, p1, p2, p3)
            FxType.CHORUS -> processChorus(inL, inR, outL, outR, frameCount, dryWet, p1, p2, p3)
            FxType.BIT_CRUSHER -> processBitCrusher(inL, inR, outL, outR, frameCount, dryWet, p1, p2)
            FxType.DISTORTION -> processDistortion(inL, inR, outL, outR, frameCount, dryWet, p1, p2)
            FxType.SATURATION -> processSaturation(inL, inR, outL, outR, frameCount, dryWet, p1)
            FxType.VINYL -> processVinyl(inL, inR, outL, outR, frameCount, dryWet, p1, p2)
            FxType.STUTTER -> processStutter(inL, inR, outL, outR, frameCount, dryWet, p1)
            FxType.GATE -> processGate(inL, inR, outL, outR, frameCount, dryWet, p1)
            FxType.COMPRESSOR -> processCompressor(inL, inR, outL, outR, frameCount, dryWet, p1, p2)
            FxType.TRANSIENT_SHAPER -> processTransientShaper(inL, inR, outL, outR, frameCount, dryWet, p1, p2)
            FxType.FREQ_SHIFTER -> processFreqShifter(inL, inR, outL, outR, frameCount, dryWet, p1)
            FxType.RING_MODULATOR -> processRingModulator(inL, inR, outL, outR, frameCount, dryWet, p1, p2)
            FxType.BEAT_REPEAT -> processBeatRepeat(inL, inR, outL, outR, frameCount, dryWet, p1)
            FxType.ROLL -> processRoll(inL, inR, outL, outR, frameCount, dryWet, p1)
            FxType.TAPE_STOP -> processTapeStop(inL, inR, outL, outR, frameCount, dryWet, p1)
        }
    }

    private fun processDelay(
        inL: FloatArray, inR: FloatArray, outL: FloatArray, outR: FloatArray,
        count: Int, dryWet: Float, p1: Float, p2: Float, p3: Float
    ) {
        val delaySamples = if (state.syncToBpm) {
            getSyncedDelaySamples(state.beatDivision)
        } else {
            (p1 * 0.8f * sampleRate).toInt().coerceAtLeast(100)
        }
        val feedback = (p2 * 0.85f).coerceIn(0f, 0.95f)

        for (i in 0 until count) {
            var readIndex = writeIndex - delaySamples
            if (readIndex < 0) readIndex += maxDelaySamples

            val delayedL = delayBufferL[readIndex]
            val delayedR = delayBufferR[readIndex]

            delayBufferL[writeIndex] = inL[i] + delayedL * feedback
            delayBufferR[writeIndex] = inR[i] + delayedR * feedback

            outL[i] = inL[i] * (1f - dryWet) + delayedL * dryWet
            outR[i] = inR[i] * (1f - dryWet) + delayedR * dryWet

            writeIndex = (writeIndex + 1) % maxDelaySamples
        }
    }

    private fun processEcho(
        inL: FloatArray, inR: FloatArray, outL: FloatArray, outR: FloatArray,
        count: Int, dryWet: Float, p1: Float, p2: Float, p3: Float
    ) {
        val delaySamples = getSyncedDelaySamples(state.beatDivision)
        val feedback = (p2 * 0.92f).coerceIn(0f, 0.98f)
        val damping = p3 * 0.5f

        for (i in 0 until count) {
            var readIndex = writeIndex - delaySamples
            if (readIndex < 0) readIndex += maxDelaySamples

            val delayedL = delayBufferL[readIndex] * (1f - damping)
            val delayedR = delayBufferR[readIndex] * (1f - damping)

            delayBufferL[writeIndex] = inL[i] + delayedL * feedback
            delayBufferR[writeIndex] = inR[i] + delayedR * feedback

            outL[i] = inL[i] * (1f - dryWet) + delayedL * dryWet
            outR[i] = inR[i] * (1f - dryWet) + delayedR * dryWet

            writeIndex = (writeIndex + 1) % maxDelaySamples
        }
    }

    private fun processReverb(
        inL: FloatArray, inR: FloatArray, outL: FloatArray, outR: FloatArray,
        count: Int, dryWet: Float, size: Float, decay: Float, damp: Float
    ) {
        val feedback = (0.5f + decay * 0.45f).coerceIn(0.5f, 0.97f)

        for (i in 0 until count) {
            val inputMono = (inL[i] + inR[i]) * 0.5f
            var wetL = 0f
            var wetR = 0f

            for (c in 0 until 6) {
                val delayLen = (combDelays[c] * (0.8f + size * 0.4f)).toInt().coerceIn(100, 2900)
                val idx = combIndices[c]
                val outSample = combBuffers[c][idx]

                combBuffers[c][idx] = inputMono + outSample * feedback
                combIndices[c] = (idx + 1) % delayLen

                if (c % 2 == 0) wetL += outSample else wetR += outSample
            }

            wetL *= 0.25f
            wetR *= 0.25f

            outL[i] = inL[i] * (1f - dryWet) + wetL * dryWet
            outR[i] = inR[i] * (1f - dryWet) + wetR * dryWet
        }
    }

    private fun processFilterLfo(
        inL: FloatArray, inR: FloatArray, outL: FloatArray, outR: FloatArray,
        count: Int, dryWet: Float, rate: Float, depth: Float, res: Float
    ) {
        val lfoHz = 0.1f + rate * 4.0f
        val phaseInc = 2.0 * PI * lfoHz / sampleRate

        for (i in 0 until count) {
            lfoPhase += phaseInc
            if (lfoPhase > 2.0 * PI) lfoPhase -= 2.0 * PI

            val mod = (sin(lfoPhase) * 0.5 + 0.5).toFloat()
            val cutoff = 150f + (depth * 3500f * mod)
            lfoFilter.configure(FilterType.LOW_PASS, cutoff, 1.0f + res * 2.5f)

            val wetL = lfoFilter.processLeft(inL[i])
            val wetR = lfoFilter.processRight(inR[i])

            outL[i] = inL[i] * (1f - dryWet) + wetL * dryWet
            outR[i] = inR[i] * (1f - dryWet) + wetR * dryWet
        }
    }

    private fun processFlanger(
        inL: FloatArray, inR: FloatArray, outL: FloatArray, outR: FloatArray,
        count: Int, dryWet: Float, rate: Float, depth: Float, feedback: Float
    ) {
        val lfoHz = 0.2f + rate * 2.0f
        val phaseInc = 2.0 * PI * lfoHz / sampleRate
        val baseDelay = (0.003f * sampleRate).toInt()
        val modDepth = (0.003f * sampleRate * depth).toInt()

        for (i in 0 until count) {
            lfoPhase += phaseInc
            val mod = (sin(lfoPhase) * 0.5 + 0.5).toFloat()
            val delayLen = baseDelay + (mod * modDepth).toInt()

            var readIdx = writeIndex - delayLen
            if (readIdx < 0) readIdx += maxDelaySamples

            val delayedL = delayBufferL[readIdx]
            val delayedR = delayBufferR[readIdx]

            delayBufferL[writeIndex] = inL[i] + delayedL * feedback * 0.7f
            delayBufferR[writeIndex] = inR[i] + delayedR * feedback * 0.7f

            outL[i] = inL[i] * (1f - dryWet) + delayedL * dryWet
            outR[i] = inR[i] * (1f - dryWet) + delayedR * dryWet

            writeIndex = (writeIndex + 1) % maxDelaySamples
        }
    }

    private fun processPhaser(
        inL: FloatArray, inR: FloatArray, outL: FloatArray, outR: FloatArray,
        count: Int, dryWet: Float, rate: Float, depth: Float, poles: Float
    ) {
        val lfoHz = 0.1f + rate * 1.5f
        val phaseInc = 2.0 * PI * lfoHz / sampleRate

        for (i in 0 until count) {
            lfoPhase += phaseInc
            val mod = (sin(lfoPhase) * 0.5 + 0.5).toFloat()
            val freq = 400f + mod * depth * 2000f

            phaserFilter1.configure(FilterType.ALL_PASS, freq, 1.5f)
            phaserFilter2.configure(FilterType.ALL_PASS, freq * 1.5f, 1.5f)

            val pL = phaserFilter2.processLeft(phaserFilter1.processLeft(inL[i]))
            val pR = phaserFilter2.processRight(phaserFilter1.processRight(inR[i]))

            outL[i] = inL[i] * (1f - dryWet) + (inL[i] + pL) * 0.5f * dryWet
            outR[i] = inR[i] * (1f - dryWet) + (inR[i] + pR) * 0.5f * dryWet
        }
    }

    private fun processChorus(
        inL: FloatArray, inR: FloatArray, outL: FloatArray, outR: FloatArray,
        count: Int, dryWet: Float, rate: Float, depth: Float, voices: Float
    ) {
        val lfoHz = 0.5f + rate * 2.0f
        val phaseInc = 2.0 * PI * lfoHz / sampleRate
        val baseDelay = (0.015f * sampleRate).toInt()
        val modDepth = (0.008f * sampleRate * depth).toInt()

        for (i in 0 until count) {
            lfoPhase += phaseInc
            val modL = (sin(lfoPhase) * 0.5 + 0.5).toFloat()
            val modR = (cos(lfoPhase) * 0.5 + 0.5).toFloat()

            var rIdxL = writeIndex - (baseDelay + (modL * modDepth).toInt())
            if (rIdxL < 0) rIdxL += maxDelaySamples
            var rIdxR = writeIndex - (baseDelay + (modR * modDepth).toInt())
            if (rIdxR < 0) rIdxR += maxDelaySamples

            delayBufferL[writeIndex] = inL[i]
            delayBufferR[writeIndex] = inR[i]

            val wetL = delayBufferL[rIdxL]
            val wetR = delayBufferR[rIdxR]

            outL[i] = inL[i] * (1f - dryWet) + wetL * dryWet
            outR[i] = inR[i] * (1f - dryWet) + wetR * dryWet

            writeIndex = (writeIndex + 1) % maxDelaySamples
        }
    }

    private fun processBitCrusher(
        inL: FloatArray, inR: FloatArray, outL: FloatArray, outR: FloatArray,
        count: Int, dryWet: Float, bits: Float, downsample: Float
    ) {
        val numBits = (16 - (bits * 12).toInt()).coerceIn(2, 16)
        val quantLevels = (1 shl numBits).toFloat()
        val holdFrames = (1 + (downsample * 24).toInt()).coerceIn(1, 32)

        var lastL = 0f
        var lastR = 0f

        for (i in 0 until count) {
            if (i % holdFrames == 0) {
                lastL = (floor(inL[i] * quantLevels) / quantLevels).coerceIn(-1f, 1f)
                lastR = (floor(inR[i] * quantLevels) / quantLevels).coerceIn(-1f, 1f)
            }
            outL[i] = inL[i] * (1f - dryWet) + lastL * dryWet
            outR[i] = inR[i] * (1f - dryWet) + lastR * dryWet
        }
    }

    private fun processDistortion(
        inL: FloatArray, inR: FloatArray, outL: FloatArray, outR: FloatArray,
        count: Int, dryWet: Float, drive: Float, tone: Float
    ) {
        val gain = 1.0f + drive * 9.0f
        for (i in 0 until count) {
            val dL = tanh((inL[i] * gain).toDouble()).toFloat()
            val dR = tanh((inR[i] * gain).toDouble()).toFloat()

            outL[i] = inL[i] * (1f - dryWet) + dL * dryWet
            outR[i] = inR[i] * (1f - dryWet) + dR * dryWet
        }
    }

    private fun processSaturation(
        inL: FloatArray, inR: FloatArray, outL: FloatArray, outR: FloatArray,
        count: Int, dryWet: Float, drive: Float
    ) {
        val boost = 1.0f + drive * 3.0f
        for (i in 0 until count) {
            val xL = inL[i] * boost
            val xR = inR[i] * boost
            // Cubic soft saturation
            val satL = if (abs(xL) < 1.0f) xL - (xL * xL * xL) / 3f else if (xL > 0) 2f / 3f else -2f / 3f
            val satR = if (abs(xR) < 1.0f) xR - (xR * xR * xR) / 3f else if (xR > 0) 2f / 3f else -2f / 3f

            outL[i] = inL[i] * (1f - dryWet) + satL * dryWet
            outR[i] = inR[i] * (1f - dryWet) + satR * dryWet
        }
    }

    private fun processVinyl(
        inL: FloatArray, inR: FloatArray, outL: FloatArray, outR: FloatArray,
        count: Int, dryWet: Float, crackle: Float, warp: Float
    ) {
        for (i in 0 until count) {
            var noise = 0f
            if (Math.random() < (0.003 + crackle * 0.02)) {
                noise = ((Math.random() * 2.0 - 1.0) * 0.15f * crackle).toFloat()
            }
            outL[i] = inL[i] + noise * dryWet
            outR[i] = inR[i] + noise * dryWet
        }
    }

    private fun processStutter(
        inL: FloatArray, inR: FloatArray, outL: FloatArray, outR: FloatArray,
        count: Int, dryWet: Float, length: Float
    ) {
        val stutterFrames = ((0.05f + length * 0.2f) * sampleRate).toInt().coerceAtLeast(100)
        for (i in 0 until count) {
            val idx = (writeIndex % stutterFrames)
            val wetL = delayBufferL[idx]
            val wetR = delayBufferR[idx]

            delayBufferL[writeIndex % maxDelaySamples] = inL[i]
            delayBufferR[writeIndex % maxDelaySamples] = inR[i]
            writeIndex = (writeIndex + 1) % maxDelaySamples

            outL[i] = inL[i] * (1f - dryWet) + wetL * dryWet
            outR[i] = inR[i] * (1f - dryWet) + wetR * dryWet
        }
    }

    private fun processGate(
        inL: FloatArray, inR: FloatArray, outL: FloatArray, outR: FloatArray,
        count: Int, dryWet: Float, pattern: Float
    ) {
        val beatFrames = ((60.0 / currentBpm) * sampleRate / 4.0).toInt().coerceAtLeast(100)
        for (i in 0 until count) {
            val step = (writeIndex / beatFrames) % 16
            // Rhythmic 16-step trance pattern (1 = on, 0 = off)
            val isOpen = when (step) {
                0, 2, 4, 6, 8, 10, 12, 14 -> 1.0f
                else -> 0.05f
            }
            writeIndex = (writeIndex + 1) % (beatFrames * 16)

            val wetL = inL[i] * isOpen
            val wetR = inR[i] * isOpen

            outL[i] = inL[i] * (1f - dryWet) + wetL * dryWet
            outR[i] = inR[i] * (1f - dryWet) + wetR * dryWet
        }
    }

    private fun processCompressor(
        inL: FloatArray, inR: FloatArray, outL: FloatArray, outR: FloatArray,
        count: Int, dryWet: Float, thresh: Float, ratio: Float
    ) {
        val compThresh = 0.8f - thresh * 0.6f
        val compRatio = 1.0f + ratio * 5.0f
        for (i in 0 until count) {
            val peak = maxOf(abs(inL[i]), abs(inR[i]))
            val gain = if (peak > compThresh) {
                compThresh + (peak - compThresh) / compRatio
            } else 1.0f
            outL[i] = inL[i] * (1f - dryWet) + inL[i] * gain * dryWet
            outR[i] = inR[i] * (1f - dryWet) + inR[i] * gain * dryWet
        }
    }

    private fun processTransientShaper(
        inL: FloatArray, inR: FloatArray, outL: FloatArray, outR: FloatArray,
        count: Int, dryWet: Float, attack: Float, punch: Float
    ) {
        val gain = 1.0f + attack * 0.8f
        for (i in 0 until count) {
            outL[i] = inL[i] * (1f - dryWet) + inL[i] * gain * dryWet
            outR[i] = inR[i] * (1f - dryWet) + inR[i] * gain * dryWet
        }
    }

    private fun processFreqShifter(
        inL: FloatArray, inR: FloatArray, outL: FloatArray, outR: FloatArray,
        count: Int, dryWet: Float, shift: Float
    ) {
        val shiftHz = (shift - 0.5f) * 100.0f
        val phaseInc = 2.0 * PI * shiftHz / sampleRate
        for (i in 0 until count) {
            lfoPhase += phaseInc
            val carrier = cos(lfoPhase).toFloat()
            val wetL = inL[i] * carrier
            val wetR = inR[i] * carrier
            outL[i] = inL[i] * (1f - dryWet) + wetL * dryWet
            outR[i] = inR[i] * (1f - dryWet) + wetR * dryWet
        }
    }

    private fun processRingModulator(
        inL: FloatArray, inR: FloatArray, outL: FloatArray, outR: FloatArray,
        count: Int, dryWet: Float, freq: Float, depth: Float
    ) {
        val carrierHz = 50f + freq * 800f
        val phaseInc = 2.0 * PI * carrierHz / sampleRate
        for (i in 0 until count) {
            lfoPhase += phaseInc
            val carrier = sin(lfoPhase).toFloat()
            val wetL = inL[i] * carrier
            val wetR = inR[i] * carrier
            outL[i] = inL[i] * (1f - dryWet) + wetL * dryWet
            outR[i] = inR[i] * (1f - dryWet) + wetR * dryWet
        }
    }

    private fun processBeatRepeat(
        inL: FloatArray, inR: FloatArray, outL: FloatArray, outR: FloatArray,
        count: Int, dryWet: Float, repeatLen: Float
    ) {
        val division = if (repeatLen < 0.33f) BeatDivision.DIV_1_16 else if (repeatLen < 0.66f) BeatDivision.DIV_1_8 else BeatDivision.DIV_1_4
        val repFrames = getSyncedDelaySamples(division)
        for (i in 0 until count) {
            var rIdx = writeIndex - repFrames
            if (rIdx < 0) rIdx += maxDelaySamples

            val wetL = delayBufferL[rIdx]
            val wetR = delayBufferR[rIdx]

            delayBufferL[writeIndex] = inL[i]
            delayBufferR[writeIndex] = inR[i]
            writeIndex = (writeIndex + 1) % maxDelaySamples

            outL[i] = inL[i] * (1f - dryWet) + wetL * dryWet
            outR[i] = inR[i] * (1f - dryWet) + wetR * dryWet
        }
    }

    private fun processRoll(
        inL: FloatArray, inR: FloatArray, outL: FloatArray, outR: FloatArray,
        count: Int, dryWet: Float, size: Float
    ) {
        val rollFrames = getSyncedDelaySamples(BeatDivision.DIV_1_8)
        for (i in 0 until count) {
            var rIdx = writeIndex - rollFrames
            if (rIdx < 0) rIdx += maxDelaySamples

            val wetL = delayBufferL[rIdx]
            val wetR = delayBufferR[rIdx]

            delayBufferL[writeIndex] = inL[i]
            delayBufferR[writeIndex] = inR[i]
            writeIndex = (writeIndex + 1) % maxDelaySamples

            outL[i] = inL[i] * (1f - dryWet) + wetL * dryWet
            outR[i] = inR[i] * (1f - dryWet) + wetR * dryWet
        }
    }

    private fun processTapeStop(
        inL: FloatArray, inR: FloatArray, outL: FloatArray, outR: FloatArray,
        count: Int, dryWet: Float, brakeSpeed: Float
    ) {
        val brakeRate = 0.00008f * (1.0f + brakeSpeed * 2.0f)
        for (i in 0 until count) {
            tapeStopSpeed = (tapeStopSpeed - brakeRate).coerceAtLeast(0.01f)
            val wetL = inL[i] * tapeStopSpeed
            val wetR = inR[i] * tapeStopSpeed

            outL[i] = inL[i] * (1f - dryWet) + wetL * dryWet
            outR[i] = inR[i] * (1f - dryWet) + wetR * dryWet
        }
    }

    fun releaseTapeStop() {
        tapeStopSpeed = 1.0f
    }
}
