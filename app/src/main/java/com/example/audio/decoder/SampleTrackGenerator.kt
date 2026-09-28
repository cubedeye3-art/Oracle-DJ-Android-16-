package com.example.audio.decoder

import com.example.audio.model.AudioBuffer
import com.example.audio.model.AudioMetadata
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

object SampleTrackGenerator {

    fun generateDeckATrack(): WavDecoder.DecodeResult {
        val sampleRate = 48000
        val bpm = 128.0
        val bars = 32
        val beatsPerBar = 4
        val totalBeats = bars * beatsPerBar
        val secondsPerBeat = 60.0 / bpm
        val totalSeconds = totalBeats * secondsPerBeat
        val totalFrames = (totalSeconds * sampleRate).toInt()

        val left = FloatArray(totalFrames)
        val right = FloatArray(totalFrames)

        val beatFrames = (secondsPerBeat * sampleRate).toInt()

        for (beat in 0 until totalBeats) {
            val startFrame = (beat * secondsPerBeat * sampleRate).toInt()

            // 1. Kick Drum on every beat (4-on-the-floor)
            val kickFrames = (0.35 * sampleRate).toInt()
            for (f in 0 until kickFrames) {
                val frame = startFrame + f
                if (frame >= totalFrames) break
                val t = f.toDouble() / sampleRate
                // Pitch sweep from 150 Hz down to 45 Hz
                val freq = 45.0 + 105.0 * exp(-t * 22.0)
                val amp = exp(-t * 8.5)
                val sample = (sin(2.0 * PI * freq * t) * amp * 0.75).toFloat()
                left[frame] += sample
                right[frame] += sample
            }

            // 2. Offbeat Hi-Hat (on every half-beat)
            val offbeatStart = startFrame + beatFrames / 2
            val hatFrames = (0.12 * sampleRate).toInt()
            for (f in 0 until hatFrames) {
                val frame = offbeatStart + f
                if (frame >= totalFrames) break
                val t = f.toDouble() / sampleRate
                val noise = ((Math.random() * 2.0 - 1.0) * 0.28).toFloat()
                val env = exp(-t * 28.0).toFloat()
                left[frame] += noise * env * 0.9f
                right[frame] += noise * env * 1.1f
            }

            // 3. 16th Note Rolling Bassline (Key: A minor = 55 Hz / 110 Hz)
            for (sub in 0 until 4) {
                if (sub == 0 && beat % 4 == 0) continue // leave room for downbeat kick transient
                val bassStart = startFrame + (sub * beatFrames) / 4
                val bassFrames = (beatFrames / 4).coerceAtMost((0.18 * sampleRate).toInt())
                val noteFreq = when ((beat / 4) % 4) {
                    0 -> 55.0  // A1
                    1 -> 49.0  // G1
                    2 -> 43.65 // F1
                    else -> 49.0 // G1
                }
                for (f in 0 until bassFrames) {
                    val frame = bassStart + f
                    if (frame >= totalFrames) break
                    val t = f.toDouble() / sampleRate
                    val osc1 = sin(2.0 * PI * noteFreq * t)
                    val osc2 = sin(2.0 * PI * (noteFreq * 2.0) * t) * 0.5
                    val subEnv = exp(-t * 12.0)
                    val sample = ((osc1 + osc2) * subEnv * 0.42).toFloat()
                    left[frame] += sample
                    right[frame] += sample
                }
            }

            // 4. Synth Chord Stabs (Bars 8..24)
            if (beat >= 32 && (beat % 2 == 1)) {
                val chordStart = startFrame + (beatFrames * 0.25).toInt()
                val chordFrames = (0.28 * sampleRate).toInt()
                val chordFreqs = when ((beat / 8) % 4) {
                    0 -> doubleArrayOf(220.0, 261.63, 329.63) // Am (A, C, E)
                    1 -> doubleArrayOf(196.0, 246.94, 293.66) // G (G, B, D)
                    2 -> doubleArrayOf(174.61, 220.0, 261.63) // F (F, A, C)
                    else -> doubleArrayOf(164.81, 207.65, 246.94) // E (E, G#, B)
                }
                for (f in 0 until chordFrames) {
                    val frame = chordStart + f
                    if (frame >= totalFrames) break
                    val t = f.toDouble() / sampleRate
                    var chordSample = 0.0
                    for (cf in chordFreqs) {
                        chordSample += sin(2.0 * PI * cf * t) * 0.12
                    }
                    val env = exp(-t * 9.0).toFloat()
                    left[frame] += (chordSample * env * 0.85).toFloat()
                    right[frame] += (chordSample * env * 1.15).toFloat()
                }
            }
        }

        // Normalize peak to -1.0 dBFS
        var maxSample = 0.001f
        for (i in 0 until totalFrames) {
            val aL = kotlin.math.abs(left[i])
            val aR = kotlin.math.abs(right[i])
            if (aL > maxSample) maxSample = aL
            if (aR > maxSample) maxSample = aR
        }
        val targetPeak = 0.891f // -1 dBFS
        val scale = targetPeak / maxSample
        for (i in 0 until totalFrames) {
            left[i] *= scale
            right[i] *= scale
        }

        val metadata = AudioMetadata(
            title = "Oracle Cyber Techno",
            artist = "Oracle DJ Studio",
            sampleRate = sampleRate,
            bitDepth = 24,
            channels = 2,
            durationSeconds = totalSeconds,
            fileSizeBytes = totalFrames * 2 * 3L,
            peakDb = -1.0f,
            rmsDb = -10.5f,
            formatName = "WAV 24-bit 48kHz",
            filePath = "asset://deck_a_techno.wav"
        )

        val buffer = AudioBuffer(
            leftChannel = left,
            rightChannel = right,
            sampleRate = sampleRate,
            originalBitDepth = 24,
            originalSampleRate = sampleRate,
            channels = 2
        )

        return WavDecoder.DecodeResult(buffer, metadata)
    }

    fun generateDeckBTrack(): WavDecoder.DecodeResult {
        val sampleRate = 48000
        val bpm = 124.0
        val bars = 32
        val beatsPerBar = 4
        val totalBeats = bars * beatsPerBar
        val secondsPerBeat = 60.0 / bpm
        val totalSeconds = totalBeats * secondsPerBeat
        val totalFrames = (totalSeconds * sampleRate).toInt()

        val left = FloatArray(totalFrames)
        val right = FloatArray(totalFrames)

        val beatFrames = (secondsPerBeat * sampleRate).toInt()

        for (beat in 0 until totalBeats) {
            val startFrame = (beat * secondsPerBeat * sampleRate).toInt()

            // 1. Deep House Kick
            val kickFrames = (0.32 * sampleRate).toInt()
            for (f in 0 until kickFrames) {
                val frame = startFrame + f
                if (frame >= totalFrames) break
                val t = f.toDouble() / sampleRate
                val freq = 48.0 + 80.0 * exp(-t * 26.0)
                val amp = exp(-t * 7.0)
                val sample = (sin(2.0 * PI * freq * t) * amp * 0.72).toFloat()
                left[frame] += sample
                right[frame] += sample
            }

            // 2. Clap / Snare on 2 and 4
            if (beat % 2 == 1) {
                val clapFrames = (0.2 * sampleRate).toInt()
                for (f in 0 until clapFrames) {
                    val frame = startFrame + f
                    if (frame >= totalFrames) break
                    val t = f.toDouble() / sampleRate
                    val noise = ((Math.random() * 2.0 - 1.0) * 0.35).toFloat()
                    val tone = (sin(2.0 * PI * 220.0 * t) * 0.15).toFloat()
                    val env = exp(-t * 18.0).toFloat()
                    left[frame] += (noise + tone) * env
                    right[frame] += (noise + tone) * env
                }
            }

            // 3. Shaker 16th Pattern
            for (sub in 0 until 4) {
                val shakerStart = startFrame + (sub * beatFrames) / 4
                val shakerFrames = (0.05 * sampleRate).toInt()
                val shakerAmp = if (sub % 2 == 1) 0.18f else 0.08f
                for (f in 0 until shakerFrames) {
                    val frame = shakerStart + f
                    if (frame >= totalFrames) break
                    val t = f.toDouble() / sampleRate
                    val noise = ((Math.random() * 2.0 - 1.0) * shakerAmp).toFloat()
                    val env = exp(-t * 60.0).toFloat()
                    left[frame] += noise * env
                    right[frame] += noise * env
                }
            }

            // 4. Deep House Chord Progression (Fmaj7 - Dm7 - Bbmaj7 - C7)
            val chordFreqs = when ((beat / 4) % 4) {
                0 -> doubleArrayOf(174.61, 220.0, 261.63, 329.63) // Fmaj7
                1 -> doubleArrayOf(146.83, 174.61, 220.0, 261.63) // Dm7
                2 -> doubleArrayOf(116.54, 146.83, 174.61, 220.0)  // Bbmaj7
                else -> doubleArrayOf(130.81, 164.81, 196.0, 233.08) // C7
            }
            val chordFrames = (beatFrames * 0.85).toInt()
            for (f in 0 until chordFrames) {
                val frame = startFrame + f
                if (frame >= totalFrames) break
                val t = f.toDouble() / sampleRate
                var chordSample = 0.0
                for (cf in chordFreqs) {
                    chordSample += sin(2.0 * PI * cf * t) * 0.09
                }
                val env = exp(-t * 3.5).toFloat()
                left[frame] += (chordSample * env * 1.1f).toFloat()
                right[frame] += (chordSample * env * 0.9f).toFloat()
            }
        }

        // Normalize peak to -1.0 dBFS
        var maxSample = 0.001f
        for (i in 0 until totalFrames) {
            val aL = kotlin.math.abs(left[i])
            val aR = kotlin.math.abs(right[i])
            if (aL > maxSample) maxSample = aL
            if (aR > maxSample) maxSample = aR
        }
        val targetPeak = 0.891f
        val scale = targetPeak / maxSample
        for (i in 0 until totalFrames) {
            left[i] *= scale
            right[i] *= scale
        }

        val metadata = AudioMetadata(
            title = "Neon Horizon House",
            artist = "Oracle DJ Studio",
            sampleRate = sampleRate,
            bitDepth = 24,
            channels = 2,
            durationSeconds = totalSeconds,
            fileSizeBytes = totalFrames * 2 * 3L,
            peakDb = -1.0f,
            rmsDb = -11.2f,
            formatName = "WAV 24-bit 48kHz",
            filePath = "asset://deck_b_house.wav"
        )

        val buffer = AudioBuffer(
            leftChannel = left,
            rightChannel = right,
            sampleRate = sampleRate,
            originalBitDepth = 24,
            originalSampleRate = sampleRate,
            channels = 2
        )

        return WavDecoder.DecodeResult(buffer, metadata)
    }
}
