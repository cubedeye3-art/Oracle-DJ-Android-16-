package com.example.audio.analysis

import com.example.audio.model.AudioBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

object KeyDetector {

    data class KeyResult(
        val keyName: String,
        val camelotCode: String,
        val isMinor: Boolean
    )

    // Musical note names
    private val NOTE_NAMES = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

    // Krumhansl-Kessler key profiles
    private val MAJOR_PROFILE = doubleArrayOf(6.35, 2.23, 3.48, 2.33, 4.38, 4.09, 2.52, 5.19, 2.39, 3.66, 2.29, 2.88)
    private val MINOR_PROFILE = doubleArrayOf(6.33, 2.68, 3.52, 5.38, 2.60, 3.53, 2.54, 4.75, 3.98, 2.69, 3.34, 3.17)

    // Camelot mappings
    // Major (B)
    private val CAMELOT_MAJOR = mapOf(
        "B" to "1B", "F#" to "2B", "Db" to "3B", "C#" to "3B", "Ab" to "4B", "G#" to "4B",
        "Eb" to "5B", "D#" to "5B", "Bb" to "6B", "A#" to "6B", "F" to "7B", "C" to "8B",
        "G" to "9B", "D" to "10B", "A" to "11B", "E" to "12B"
    )

    // Minor (A)
    private val CAMELOT_MINOR = mapOf(
        "Abm" to "1A", "G#m" to "1A", "Ebm" to "2A", "D#m" to "2A", "Bbm" to "3A", "A#m" to "3A",
        "Fm" to "4A", "Cm" to "5A", "Gm" to "6A", "Dm" to "7A", "Am" to "8A",
        "Em" to "9A", "Bm" to "10A", "F#m" to "11A", "C#m" to "12A", "Dbm" to "12A"
    )

    fun detect(buffer: AudioBuffer): KeyResult {
        val totalFrames = buffer.frameCount
        val sampleRate = buffer.sampleRate
        if (totalFrames <= 0) {
            return KeyResult("Am", "8A", true)
        }

        // Aggregate 12 chroma bins across octaves (130Hz - 1000Hz)
        val chroma = DoubleArray(12)
        val step = (totalFrames / 1500).coerceAtLeast(1)
        val testFrequencies = doubleArrayOf(
            130.81, 138.59, 146.83, 155.56, 164.81, 174.61, 185.00, 196.00, 207.65, 220.00, 233.08, 246.94
        )

        val windowSize = 256
        for (fIndex in 0 until 12) {
            val freq = testFrequencies[fIndex]
            val omega = 2.0 * PI * freq / sampleRate
            var binEnergy = 0.0

            var i = 0
            while (i + windowSize < totalFrames) {
                var real = 0.0
                var imag = 0.0
                for (w in 0 until windowSize) {
                    val s = buffer.leftChannel[i + w]
                    real += s * cos(omega * w)
                    imag += s * sin(omega * w)
                }
                binEnergy += (real * real + imag * imag)
                i += (step * windowSize)
            }
            chroma[fIndex] = binEnergy
        }

        // Normalize chroma
        var chromaSum = 0.0
        for (c in chroma) chromaSum += c
        if (chromaSum > 0) {
            for (k in 0 until 12) chroma[k] /= chromaSum
        }

        // Correlate with 12 major and 12 minor keys
        var bestCorrelation = -Double.MAX_VALUE
        var bestKeyName = "Am"
        var isMinorBest = true

        for (root in 0 until 12) {
            // Major
            var corrMajor = 0.0
            for (stepNote in 0 until 12) {
                val chromaIdx = (root + stepNote) % 12
                corrMajor += chroma[chromaIdx] * MAJOR_PROFILE[stepNote]
            }
            if (corrMajor > bestCorrelation) {
                bestCorrelation = corrMajor
                bestKeyName = NOTE_NAMES[root]
                isMinorBest = false
            }

            // Minor
            var corrMinor = 0.0
            for (stepNote in 0 until 12) {
                val chromaIdx = (root + stepNote) % 12
                corrMinor += chroma[chromaIdx] * MINOR_PROFILE[stepNote]
            }
            if (corrMinor > bestCorrelation) {
                bestCorrelation = corrMinor
                bestKeyName = NOTE_NAMES[root] + "m"
                isMinorBest = true
            }
        }

        val camelot = if (isMinorBest) {
            CAMELOT_MINOR[bestKeyName] ?: "8A"
        } else {
            CAMELOT_MAJOR[bestKeyName] ?: "8B"
        }

        return KeyResult(bestKeyName, camelot, isMinorBest)
    }
}
