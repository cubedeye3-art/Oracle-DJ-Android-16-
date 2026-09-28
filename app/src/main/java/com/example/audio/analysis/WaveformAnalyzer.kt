package com.example.audio.analysis

import com.example.audio.dsp.BiquadFilter
import com.example.audio.dsp.FilterType
import com.example.audio.model.AudioBuffer
import kotlin.math.abs
import kotlin.math.max

data class WaveformData(
    val overviewPeaks: FloatArray,          // Normalized 0.0 .. 1.0 (length ~600)
    val overviewColorR: FloatArray,         // Low band energy ratio
    val overviewColorG: FloatArray,         // Mid band energy ratio
    val overviewColorB: FloatArray,         // High band energy ratio
    val detailPeaks: FloatArray,            // High-res frames (~100 points per second)
    val pointsPerSecond: Float = 100f
)

object WaveformAnalyzer {

    fun analyze(buffer: AudioBuffer): WaveformData {
        val totalFrames = buffer.frameCount
        val sampleRate = buffer.sampleRate
        if (totalFrames <= 0) {
            return WaveformData(FloatArray(100), FloatArray(100), FloatArray(100), FloatArray(100), FloatArray(100))
        }

        // 1. Overview waveform: 600 points across the whole track
        val overviewCount = 600
        val overviewPeaks = FloatArray(overviewCount)
        val colR = FloatArray(overviewCount)
        val colG = FloatArray(overviewCount)
        val colB = FloatArray(overviewCount)

        val framesPerOverviewBucket = (totalFrames / overviewCount).coerceAtLeast(1)

        val lowFilter = BiquadFilter(sampleRate)
        val highFilter = BiquadFilter(sampleRate)
        lowFilter.configure(FilterType.LOW_PASS, 300f, 0.707f)
        highFilter.configure(FilterType.HIGH_PASS, 2500f, 0.707f)

        for (bucket in 0 until overviewCount) {
            val start = bucket * framesPerOverviewBucket
            val end = (start + framesPerOverviewBucket).coerceAtMost(totalFrames)

            var peak = 0.0f
            var sumLow = 0.0f
            var sumHigh = 0.0f
            var sumMid = 0.0f

            val step = max(1, (end - start) / 64) // sample 64 frames per bucket for speed
            var sampled = 0

            var f = start
            while (f < end) {
                val sample = abs(buffer.leftChannel[f])
                if (sample > peak) peak = sample

                val lowVal = abs(lowFilter.processLeft(buffer.leftChannel[f]))
                val highVal = abs(highFilter.processLeft(buffer.leftChannel[f]))
                val midVal = max(0f, sample - lowVal * 0.5f - highVal * 0.5f)

                sumLow += lowVal
                sumHigh += highVal
                sumMid += midVal
                sampled++
                f += step
            }

            overviewPeaks[bucket] = peak.coerceIn(0f, 1f)
            val totalFreq = (sumLow + sumMid + sumHigh).coerceAtLeast(0.001f)
            colR[bucket] = (sumLow / totalFreq).coerceIn(0f, 1f)
            colG[bucket] = (sumMid / totalFreq).coerceIn(0f, 1f)
            colB[bucket] = (sumHigh / totalFreq).coerceIn(0f, 1f)
        }

        // 2. Detail waveform: 100 points per second
        val pps = 100f
        val detailCount = ((totalFrames.toDouble() / sampleRate) * pps).toInt().coerceAtLeast(1)
        val detailPeaks = FloatArray(detailCount)
        val framesPerDetail = (sampleRate / pps).toInt().coerceAtLeast(1)

        for (d in 0 until detailCount) {
            val start = d * framesPerDetail
            val end = (start + framesPerDetail).coerceAtMost(totalFrames)
            var p = 0.0f
            for (f in start until end) {
                val s = abs(buffer.leftChannel[f])
                if (s > p) p = s
            }
            detailPeaks[d] = p.coerceIn(0f, 1f)
        }

        return WaveformData(
            overviewPeaks = overviewPeaks,
            overviewColorR = colR,
            overviewColorG = colG,
            overviewColorB = colB,
            detailPeaks = detailPeaks,
            pointsPerSecond = pps
        )
    }
}
