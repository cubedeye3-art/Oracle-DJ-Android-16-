package com.example.audio.dsp

import com.example.audio.model.AudioBuffer
import kotlin.math.cos
import kotlin.math.sin

class TimeStretcher(
    private var sampleRate: Int = 48000
) {
    // Grain parameters for WSOLA / Overlap-Add
    private val grainSize = (0.035 * sampleRate).toInt().coerceAtLeast(256) // ~35ms grains
    private val hopSize = grainSize / 2
    private val hanningWindow = FloatArray(grainSize) { i ->
        (0.5 * (1.0 - cos(2.0 * Math.PI * i / (grainSize - 1)))).toFloat()
    }

    private val grainBufferL = FloatArray(grainSize)
    private val grainBufferR = FloatArray(grainSize)
    private val overlapL = FloatArray(hopSize)
    private val overlapR = FloatArray(hopSize)

    fun setSampleRate(sr: Int) {
        if (sr > 0) sampleRate = sr
    }

    /**
     * Reads output frames with pitch-preserved time-stretch (Key Lock) or vinyl pitch bend.
     *
     * @param buffer Source audio buffer
     * @param position Current floating playhead frame position (sub-sample precision)
     * @param rate Playback rate (1.0 = normal, 1.08 = +8%, -1.0 = reverse)
     * @param keyLock If true, pitch is preserved while tempo changes. If false, speed changes pitch.
     * @param outL Left destination float buffer
     * @param outR Right destination float buffer
     * @param frameCount Number of frames to render
     * @return New playhead frame position
     */
    fun processFrames(
        buffer: AudioBuffer,
        position: Double,
        rate: Double,
        keyLock: Boolean,
        outL: FloatArray,
        outR: FloatArray,
        frameCount: Int
    ): Double {
        val totalFrames = buffer.frameCount
        if (totalFrames <= 0) {
            outL.fill(0f, 0, frameCount)
            outR.fill(0f, 0, frameCount)
            return position
        }

        var currentPos = position

        if (!keyLock || Math.abs(rate - 1.0) < 0.001 || Math.abs(rate) > 3.0 || Math.abs(rate) < 0.05) {
            // Vinyl mode / direct Hermite resample (or near nominal rate)
            for (i in 0 until frameCount) {
                val baseIndex = currentPos.toInt()
                val frac = (currentPos - baseIndex).toFloat()

                if (baseIndex in 1 until totalFrames - 2) {
                    val l0 = buffer.leftChannel[baseIndex - 1]
                    val l1 = buffer.leftChannel[baseIndex]
                    val l2 = buffer.leftChannel[baseIndex + 1]
                    val l3 = buffer.leftChannel[baseIndex + 2]

                    val r0 = buffer.rightChannel[baseIndex - 1]
                    val r1 = buffer.rightChannel[baseIndex]
                    val r2 = buffer.rightChannel[baseIndex + 1]
                    val r3 = buffer.rightChannel[baseIndex + 2]

                    outL[i] = DspUtils.interpolateHermite(l0, l1, l2, l3, frac)
                    outR[i] = DspUtils.interpolateHermite(r0, r1, r2, r3, frac)
                } else if (baseIndex in 0 until totalFrames) {
                    outL[i] = buffer.leftChannel[baseIndex]
                    outR[i] = buffer.rightChannel[baseIndex]
                } else {
                    outL[i] = 0f
                    outR[i] = 0f
                }

                currentPos += rate
                if (currentPos >= totalFrames) {
                    currentPos = (currentPos % totalFrames)
                } else if (currentPos < 0) {
                    currentPos = (totalFrames + (currentPos % totalFrames)) % totalFrames
                }
            }
            return currentPos
        }

        // Key Lock enabled: Granular overlap-add to stretch/compress duration while maintaining pitch
        val grainAdvance = (hopSize * rate).toInt().coerceAtLeast(1)
        var outIndex = 0

        while (outIndex < frameCount) {
            val framesToFill = Math.min(hopSize, frameCount - outIndex)

            // Extract grain from currentPos
            val grainBase = currentPos.toInt()
            for (g in 0 until grainSize) {
                val idx = (grainBase + g) % totalFrames
                val safeIdx = if (idx < 0) idx + totalFrames else idx
                val w = hanningWindow[g]
                grainBufferL[g] = buffer.leftChannel[safeIdx] * w
                grainBufferR[g] = buffer.rightChannel[safeIdx] * w
            }

            // Overlap and add
            for (f in 0 until framesToFill) {
                outL[outIndex + f] = overlapL[f] + grainBufferL[f]
                outR[outIndex + f] = overlapR[f] + grainBufferR[f]
            }

            // Prepare next overlap buffer
            for (f in 0 until hopSize) {
                overlapL[f] = grainBufferL[f + hopSize]
                overlapR[f] = grainBufferR[f + hopSize]
            }

            currentPos += grainAdvance
            if (currentPos >= totalFrames) currentPos %= totalFrames
            else if (currentPos < 0) currentPos = totalFrames + (currentPos % totalFrames)

            outIndex += framesToFill
        }

        return currentPos
    }

    fun reset() {
        overlapL.fill(0f)
        overlapR.fill(0f)
    }
}
