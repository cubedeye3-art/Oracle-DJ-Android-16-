package com.example.audio.analysis

import com.example.audio.model.AudioBuffer
import com.example.util.DjLogger
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

object BpmDetector {

    const val ANALYSIS_VERSION = 2
    private const val TAG = "BpmDetector"
    private const val HOP_RATE_HZ = 200 // 5ms per envelope hop
    private const val MIN_BPM = 60.0
    private const val MAX_BPM = 220.0

    data class BpmResult(
        val bpm: Double,
        val beatIntervalSeconds: Double,
        val firstBeatOffsetSeconds: Double,
        val confidence: Float,
        val analysisVersion: Int = ANALYSIS_VERSION
    )

    private data class Candidate(
        val bpm: Double,
        val score: Double,
        val confidence: Float
    )

    fun detect(buffer: AudioBuffer): BpmResult {
        val totalFrames = buffer.frameCount
        val sampleRate = buffer.sampleRate
        val durationSec = if (sampleRate > 0) totalFrames.toDouble() / sampleRate else 0.0

        DjLogger.d(TAG, "INPUT: sampleRate=$sampleRate, frames=$totalFrames, duration=${durationSec}s")

        // 1. Validation: require valid sample rate and at least 2.0s of audio
        if (sampleRate <= 0 || totalFrames < sampleRate * 2) {
            DjLogger.w(TAG, "Analysis failed: Insufficient audio frames ($totalFrames) for sampleRate ($sampleRate)")
            return BpmResult(
                bpm = 0.0,
                beatIntervalSeconds = 0.0,
                firstBeatOffsetSeconds = 0.0,
                confidence = 0.0f,
                analysisVersion = ANALYSIS_VERSION
            )
        }

        // 2. Convert to mono & compute DC offset
        val mono = FloatArray(totalFrames)
        var sumSamples = 0.0
        val isStereo = buffer.channels > 1 && buffer.rightChannel.size == totalFrames

        for (i in 0 until totalFrames) {
            val sample = if (isStereo) {
                (buffer.leftChannel[i] + buffer.rightChannel[i]) * 0.5f
            } else {
                buffer.leftChannel[i]
            }
            mono[i] = sample
            sumSamples += sample
        }

        val dcOffset = (sumSamples / totalFrames).toFloat()

        // 3. Remove DC and find peak amplitude for normalization
        var maxAmp = 0.0f
        for (i in 0 until totalFrames) {
            val s = mono[i] - dcOffset
            mono[i] = s
            val absVal = abs(s)
            if (absVal > maxAmp) maxAmp = absVal
        }

        if (maxAmp < 0.001f) {
            DjLogger.w(TAG, "Analysis failed: Audio signal is effectively silent (peak=$maxAmp)")
            return BpmResult(
                bpm = 0.0,
                beatIntervalSeconds = 0.0,
                firstBeatOffsetSeconds = 0.0,
                confidence = 0.0f,
                analysisVersion = ANALYSIS_VERSION
            )
        }

        val invPeak = 1.0f / maxAmp
        for (i in 0 until totalFrames) {
            mono[i] *= invPeak
        }

        // 4. Multi-stage onset envelope extraction at 200 Hz
        val hopSize = (sampleRate / HOP_RATE_HZ).coerceAtLeast(1)
        val envelopeLength = totalFrames / hopSize
        if (envelopeLength < HOP_RATE_HZ * 2) {
            DjLogger.w(TAG, "Analysis failed: Envelope length ($envelopeLength) too short")
            return BpmResult(
                bpm = 0.0,
                beatIntervalSeconds = 0.0,
                firstBeatOffsetSeconds = 0.0,
                confidence = 0.0f,
                analysisVersion = ANALYSIS_VERSION
            )
        }

        val envelope = FloatArray(envelopeLength)
        var prevEnergy = 0.0f
        var maxFlux = 0.0001f
        var sumEnvelope = 0.0

        for (i in 0 until envelopeLength) {
            val start = i * hopSize
            val count = (hopSize).coerceAtMost(totalFrames - start)
            var sumSquare = 0.0f
            for (j in 0 until count) {
                val s = mono[start + j]
                sumSquare += s * s
            }
            val rmsEnergy = sqrt(sumSquare / count)
            // Half-wave rectified onset spectral-energy flux
            val flux = max(0.0f, rmsEnergy - prevEnergy)
            prevEnergy = rmsEnergy * 0.95f // exponential decay leak
            envelope[i] = flux
            sumEnvelope += flux
            if (flux > maxFlux) maxFlux = flux
        }

        // Normalize onset envelope and compute stats
        for (i in 0 until envelopeLength) {
            envelope[i] /= maxFlux
        }

        val envMean = (sumEnvelope / (envelopeLength * maxFlux)).toFloat()
        var envVarSum = 0.0
        for (i in 0 until envelopeLength) {
            val diff = envelope[i] - envMean
            envVarSum += diff * diff
        }
        val envStdDev = sqrt(envVarSum / envelopeLength).toFloat()

        DjLogger.d(TAG, "ONSET: envelope length=$envelopeLength, mean=$envMean, standard deviation=$envStdDev")

        // If std deviation is negligible, there is no rhythmic onset variation
        if (envStdDev < 0.005f) {
            DjLogger.w(TAG, "Analysis failed: Flat onset envelope without rhythmic impulses (stdDev=$envStdDev)")
            return BpmResult(
                bpm = 0.0,
                beatIntervalSeconds = 0.0,
                firstBeatOffsetSeconds = 0.0,
                confidence = 0.0f,
                analysisVersion = ANALYSIS_VERSION
            )
        }

        // 5. Multi-window evaluation across song sections
        // We evaluate independent windows to prevent trusting a single breakdown/intro
        val windowSeconds = 12.0 // 12 seconds per analysis window (~2400 hops)
        val windowHops = (windowSeconds * HOP_RATE_HZ).toInt().coerceAtMost(envelopeLength)
        val windowPositions = if (envelopeLength > windowHops * 2) {
            listOf(
                (envelopeLength * 0.15).toInt().coerceAtMost(envelopeLength - windowHops),
                (envelopeLength * 0.35).toInt().coerceAtMost(envelopeLength - windowHops),
                (envelopeLength * 0.55).toInt().coerceAtMost(envelopeLength - windowHops),
                (envelopeLength * 0.75).toInt().coerceAtMost(envelopeLength - windowHops)
            ).distinct()
        } else {
            listOf(0)
        }

        // Minimum and maximum lag corresponding to MIN_BPM..MAX_BPM (60..220 BPM)
        val minLag = (60.0 / MAX_BPM * HOP_RATE_HZ).toInt().coerceAtLeast(1)
        val maxLag = (60.0 / MIN_BPM * HOP_RATE_HZ).toInt().coerceAtMost(windowHops / 2)

        val windowCandidates = mutableListOf<Candidate>()

        for (winStart in windowPositions) {
            val winCandidates = analyzeWindowCandidates(
                envelope = envelope,
                startHop = winStart,
                length = windowHops,
                minLag = minLag,
                maxLag = maxLag
            )
            for (c in winCandidates) {
                DjLogger.d(TAG, "CANDIDATES: candidate BPM=${c.bpm}, score=${c.score}, confidence=${c.confidence}")
            }
            windowCandidates.addAll(winCandidates)
        }

        if (windowCandidates.isEmpty()) {
            DjLogger.w(TAG, "Analysis failed: No periodicity candidates found")
            return BpmResult(
                bpm = 0.0,
                beatIntervalSeconds = 0.0,
                firstBeatOffsetSeconds = 0.0,
                confidence = 0.0f,
                analysisVersion = ANALYSIS_VERSION
            )
        }

        // 6. Aggregate consensus across candidates
        val consensus = resolveCandidateConsensus(windowCandidates)
        if (consensus == null || consensus.confidence < 0.25f || consensus.bpm <= 0.0) {
            DjLogger.w(TAG, "Analysis failed: Low consensus confidence (${consensus?.confidence ?: 0f})")
            return BpmResult(
                bpm = 0.0,
                beatIntervalSeconds = 0.0,
                firstBeatOffsetSeconds = 0.0,
                confidence = 0.0f,
                analysisVersion = ANALYSIS_VERSION
            )
        }

        val resolvedBpm = (consensus.bpm * 10.0).roundToInt() / 10.0
        val beatIntervalSec = 60.0 / resolvedBpm
        val beatHopInterval = (beatIntervalSec * HOP_RATE_HZ).toInt().coerceAtLeast(1)

        // 7. Find first downbeat offset using phase correlation
        var bestOffsetHop = 0
        var maxOffsetEnergy = -1.0f
        val maxSearchHops = (beatHopInterval * 2).coerceAtMost(envelopeLength / 2)

        for (offset in 0 until maxSearchHops) {
            var pulseSum = 0.0f
            var count = 0
            var h = offset
            while (h < envelopeLength && count < 16) {
                pulseSum += envelope[h]
                h += beatHopInterval
                count++
            }
            if (count > 0) {
                val avgEnergy = pulseSum / count
                if (avgEnergy > maxOffsetEnergy) {
                    maxOffsetEnergy = avgEnergy
                    bestOffsetHop = offset
                }
            }
        }

        val firstBeatOffsetSec = (bestOffsetHop.toDouble() / HOP_RATE_HZ).coerceAtLeast(0.0)

        DjLogger.d(
            TAG,
            "FINAL: BPM=$resolvedBpm, confidence=${consensus.confidence}, first beat offset=$firstBeatOffsetSec, analysis version=$ANALYSIS_VERSION"
        )

        return BpmResult(
            bpm = resolvedBpm,
            beatIntervalSeconds = beatIntervalSec,
            firstBeatOffsetSeconds = firstBeatOffsetSec,
            confidence = consensus.confidence,
            analysisVersion = ANALYSIS_VERSION
        )
    }

    private fun analyzeWindowCandidates(
        envelope: FloatArray,
        startHop: Int,
        length: Int,
        minLag: Int,
        maxLag: Int
    ): List<Candidate> {
        val candidates = mutableListOf<Candidate>()
        val compareLength = (length - maxLag).coerceAtLeast(100)
        val correlation = DoubleArray(maxLag + 1)
        var maxCorr = 0.0

        for (lag in minLag..maxLag) {
            var dot = 0.0
            for (i in 0 until compareLength) {
                val idx = startHop + i
                dot += (envelope[idx] * envelope[idx + lag])
            }
            correlation[lag] = dot
            if (dot > maxCorr) maxCorr = dot
        }

        if (maxCorr <= 0.0001) return emptyList()

        // Normalize autocorrelation curve
        for (lag in minLag..maxLag) {
            correlation[lag] /= maxCorr
        }

        // Find local peaks
        for (lag in (minLag + 1) until maxLag) {
            val curr = correlation[lag]
            val prev = correlation[lag - 1]
            val next = correlation[lag + 1]
            if (curr > prev && curr > next && curr > 0.35) {
                val candBpm = (60.0 * HOP_RATE_HZ) / lag

                // Evaluate harmonic support: check if lag * 2 or lag / 2 also resonates
                var harmonicBoost = 1.0
                val doubleLag = lag * 2
                if (doubleLag <= maxLag && correlation[doubleLag] > 0.3) {
                    harmonicBoost += (correlation[doubleLag] * 0.4)
                }
                val halfLag = lag / 2
                if (halfLag >= minLag && correlation[halfLag] > 0.3) {
                    harmonicBoost += (correlation[halfLag] * 0.3)
                }

                val score = curr * harmonicBoost
                val confidence = (curr.toFloat() * 0.85f).coerceIn(0.1f, 1.0f)

                candidates.add(Candidate(candBpm, score, confidence))
            }
        }

        return candidates.sortedByDescending { it.score }.take(4)
    }

    private fun resolveCandidateConsensus(candidates: List<Candidate>): Candidate? {
        if (candidates.isEmpty()) return null

        // Group candidates into tempo clusters within +/- 2.5% tolerance
        data class Cluster(
            var sumWeightedBpm: Double = 0.0,
            var totalWeight: Double = 0.0,
            var count: Int = 0,
            var maxConfidence: Float = 0.0f
        )

        val clusters = mutableListOf<Cluster>()

        for (c in candidates) {
            var matchedCluster: Cluster? = null
            for (cl in clusters) {
                val clusterBpm = cl.sumWeightedBpm / cl.totalWeight
                val diffPercent = abs(c.bpm - clusterBpm) / clusterBpm
                if (diffPercent < 0.03) {
                    matchedCluster = cl
                    break
                }
            }

            if (matchedCluster == null) {
                val newCl = Cluster(
                    sumWeightedBpm = c.bpm * c.score,
                    totalWeight = c.score,
                    count = 1,
                    maxConfidence = c.confidence
                )
                clusters.add(newCl)
            } else {
                matchedCluster.sumWeightedBpm += c.bpm * c.score
                matchedCluster.totalWeight += c.score
                matchedCluster.count += 1
                if (c.confidence > matchedCluster.maxConfidence) {
                    matchedCluster.maxConfidence = c.confidence
                }
            }
        }

        // Rank clusters by total weighted score and agreement count
        val ranked = clusters.sortedByDescending { it.totalWeight * (1.0 + it.count * 0.25) }
        val primary = ranked.firstOrNull() ?: return null
        val primaryBpm = primary.sumWeightedBpm / primary.totalWeight

        // Check if there is an octave harmonic cluster (e.g., 90 vs 180, or 85 vs 170)
        // If the faster candidate is supported across multiple windows (>= 2 votes and good weight),
        // keep the fast tempo (e.g. 180 BPM for drum & bass / hardcore / fast techno)!
        val fastOctaveCluster = clusters.firstOrNull { cl ->
            val b = cl.sumWeightedBpm / cl.totalWeight
            abs(b - primaryBpm * 2.0) / (primaryBpm * 2.0) < 0.03
        }

        val finalBpm: Double
        val finalConfidence: Float

        if (fastOctaveCluster != null && fastOctaveCluster.count >= 2 && fastOctaveCluster.totalWeight > primary.totalWeight * 0.7) {
            finalBpm = fastOctaveCluster.sumWeightedBpm / fastOctaveCluster.totalWeight
            finalConfidence = fastOctaveCluster.maxConfidence.coerceAtLeast(primary.maxConfidence)
        } else {
            finalBpm = primaryBpm
            finalConfidence = primary.maxConfidence
        }

        return Candidate(finalBpm, primary.totalWeight, finalConfidence)
    }
}
