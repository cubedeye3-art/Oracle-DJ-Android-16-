package com.example.audio.analysis

import com.example.audio.model.AudioBuffer
import kotlin.math.abs
import kotlin.math.max

object BpmDetector {

    data class BpmResult(
        val bpm: Double,
        val beatIntervalSeconds: Double,
        val firstBeatOffsetSeconds: Double,
        val confidence: Float
    )

    fun detect(buffer: AudioBuffer): BpmResult {
        val totalFrames = buffer.frameCount
        val sampleRate = buffer.sampleRate
        if (totalFrames <= sampleRate * 2) {
            return BpmResult(124.0, 60.0 / 124.0, 0.0, 0.5f)
        }

        // Downsample audio to an envelope flux signal at ~200 Hz hop rate
        val hopSize = sampleRate / 200 // ~5ms
        val envelopeLength = totalFrames / hopSize
        val envelope = FloatArray(envelopeLength)

        var prevEnergy = 0.0f
        var maxFlux = 0.001f

        for (i in 0 until envelopeLength) {
            val start = i * hopSize
            var sum = 0.0f
            val count = Math.min(hopSize, totalFrames - start)
            for (j in 0 until count) {
                val s = abs(buffer.leftChannel[start + j])
                sum += s
            }
            val energy = sum / count
            // Half-wave rectified spectral energy flux
            val flux = max(0.0f, energy - prevEnergy)
            prevEnergy = energy
            envelope[i] = flux
            if (flux > maxFlux) maxFlux = flux
        }

        // Normalize flux
        for (i in 0 until envelopeLength) {
            envelope[i] /= maxFlux
        }

        // Autocorrelation over BPM range 70 to 180 BPM
        // At 200 Hz: 70 BPM -> lag = (60 / 70) * 200 = 171
        // 180 BPM -> lag = (60 / 180) * 200 = 66
        val minLag = (60.0 / 185.0 * 200).toInt()
        val maxLag = (60.0 / 68.0 * 200).toInt().coerceAtMost(envelopeLength / 2)

        var bestLag = 100
        var bestCorrelation = 0.0
        val maxCompare = Math.min(envelopeLength - maxLag, 2000)

        for (lag in minLag..maxLag) {
            var corr = 0.0
            for (i in 0 until maxCompare) {
                corr += (envelope[i] * envelope[i + lag])
            }
            if (corr > bestCorrelation) {
                bestCorrelation = corr
                bestLag = lag
            }
        }

        var detectedBpm = (60.0 * 200.0) / bestLag

        // Normalize into standard DJ tempo range (80 - 165 BPM)
        while (detectedBpm < 80.0) detectedBpm *= 2.0
        while (detectedBpm > 165.0) detectedBpm /= 2.0

        val beatIntervalSec = 60.0 / detectedBpm
        val beatHopFrames = (beatIntervalSec * 200).toInt()

        // Find first downbeat offset by searching for maximum flux in the first 4 beats
        var bestOffsetHop = 0
        var maxOffsetEnergy = 0.0f
        val searchHops = Math.min(beatHopFrames * 2, envelopeLength)

        for (h in 0 until searchHops) {
            var energyAcrossBeats = 0.0f
            for (b in 0 until 8) {
                val idx = h + b * beatHopFrames
                if (idx < envelopeLength) {
                    energyAcrossBeats += envelope[idx]
                }
            }
            if (energyAcrossBeats > maxOffsetEnergy) {
                maxOffsetEnergy = energyAcrossBeats
                bestOffsetHop = h
            }
        }

        val firstBeatOffsetSec = (bestOffsetHop.toDouble() / 200.0).coerceAtLeast(0.0)

        return BpmResult(
            bpm = Math.round(detectedBpm * 10.0) / 10.0,
            beatIntervalSeconds = beatIntervalSec,
            firstBeatOffsetSeconds = firstBeatOffsetSec,
            confidence = 0.92f
        )
    }
}
