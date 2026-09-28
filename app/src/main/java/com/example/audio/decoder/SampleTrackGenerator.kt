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

    fun generateDnbTrack(): WavDecoder.DecodeResult {
        val sampleRate = 48000
        val bpm = 174.0
        val bars = 32
        val beatsPerBar = 4
        val totalBeats = bars * beatsPerBar
        val secondsPerBeat = 60.0 / bpm
        val totalSeconds = totalBeats * secondsPerBeat
        val totalFrames = (totalSeconds * sampleRate).toInt()

        val left = FloatArray(totalFrames)
        val right = FloatArray(totalFrames)
        val beatFrames = (secondsPerBeat * sampleRate).toInt()

        // 174 BPM Amen-style Breakbeat & Reese Bass (Key: 4A / Fm)
        for (beat in 0 until totalBeats) {
            val startFrame = (beat * secondsPerBeat * sampleRate).toInt()
            val barPos = beat % 4

            // Kick on beat 0 and beat 2.5
            if (barPos == 0 || barPos == 2) {
                val kickFrames = (0.22 * sampleRate).toInt()
                for (f in 0 until kickFrames) {
                    val frame = startFrame + f
                    if (frame >= totalFrames) break
                    val t = f.toDouble() / sampleRate
                    val freq = 50.0 + 120.0 * exp(-t * 26.0)
                    val amp = exp(-t * 9.0)
                    val sample = (sin(2.0 * PI * freq * t) * amp * 0.85).toFloat()
                    left[frame] += sample
                    right[frame] += sample
                }
            }

            // Snare on beat 1 and beat 3 (classic DnB backbeat)
            if (barPos == 1 || barPos == 3) {
                val snareFrames = (0.18 * sampleRate).toInt()
                for (f in 0 until snareFrames) {
                    val frame = startFrame + f
                    if (frame >= totalFrames) break
                    val t = f.toDouble() / sampleRate
                    val body = sin(2.0 * PI * 200.0 * exp(-t * 18.0) * t) * exp(-t * 14.0) * 0.5
                    val snap = (Math.random() * 2.0 - 1.0) * exp(-t * 22.0) * 0.6
                    val sample = (body + snap).toFloat()
                    left[frame] += sample
                    right[frame] += sample
                }
            }

            // Fast 16th-note rolling ghost snares and hats
            for (sub in 0 until 4) {
                val hatStart = startFrame + (sub * beatFrames) / 4
                val hatFrames = (0.06 * sampleRate).toInt()
                for (f in 0 until hatFrames) {
                    val frame = hatStart + f
                    if (frame >= totalFrames) break
                    val t = f.toDouble() / sampleRate
                    val noise = ((Math.random() * 2.0 - 1.0) * 0.18).toFloat()
                    val env = exp(-t * 40.0).toFloat()
                    left[frame] += noise * env
                    right[frame] += noise * env
                }
            }

            // Deep Reese Sub-bass (F minor: F1 = 43.65 Hz with detuned saw)
            for (f in 0 until beatFrames) {
                val frame = startFrame + f
                if (frame >= totalFrames) break
                val t = f.toDouble() / sampleRate
                val osc1 = sin(2.0 * PI * 43.65 * t)
                val osc2 = sin(2.0 * PI * 44.5 * t) // slow chorused beat
                val sub = ((osc1 + osc2) * 0.35).toFloat()
                left[frame] += sub
                right[frame] += sub
            }
        }

        normalizeBuffer(left, right, totalFrames)

        val metadata = AudioMetadata(
            title = "Quantum Drum & Bass",
            artist = "Oracle DJ Studio",
            sampleRate = sampleRate,
            bitDepth = 24,
            channels = 2,
            durationSeconds = totalSeconds,
            fileSizeBytes = totalFrames * 2 * 3L,
            peakDb = -1.0f,
            rmsDb = -10.5f,
            formatName = "WAV 24-bit 48kHz",
            filePath = "asset://deck_dnb.wav"
        )

        return WavDecoder.DecodeResult(
            AudioBuffer(left, right, sampleRate, 24, sampleRate, 2),
            metadata
        )
    }

    fun generateHipHopTrack(): WavDecoder.DecodeResult {
        val sampleRate = 48000
        val bpm = 92.0
        val bars = 32
        val beatsPerBar = 4
        val totalBeats = bars * beatsPerBar
        val secondsPerBeat = 60.0 / bpm
        val totalSeconds = totalBeats * secondsPerBeat
        val totalFrames = (totalSeconds * sampleRate).toInt()

        val left = FloatArray(totalFrames)
        val right = FloatArray(totalFrames)
        val beatFrames = (secondsPerBeat * sampleRate).toInt()

        // 92 BPM Boom Bap Lo-Fi Groove (Key: 6A / G#m)
        for (beat in 0 until totalBeats) {
            val startFrame = (beat * secondsPerBeat * sampleRate).toInt()
            val barPos = beat % 4

            // Heavy 808-style Kick
            if (barPos == 0 || (barPos == 2 && beat % 2 == 1)) {
                val kickFrames = (0.45 * sampleRate).toInt()
                for (f in 0 until kickFrames) {
                    val frame = startFrame + f
                    if (frame >= totalFrames) break
                    val t = f.toDouble() / sampleRate
                    val freq = 42.0 + 90.0 * exp(-t * 16.0)
                    val amp = exp(-t * 6.5)
                    val sample = (sin(2.0 * PI * freq * t) * amp * 0.85).toFloat()
                    left[frame] += sample
                    right[frame] += sample
                }
            }

            // Rimshot / Snare on 2 and 4
            if (barPos == 1 || barPos == 3) {
                val snareFrames = (0.25 * sampleRate).toInt()
                for (f in 0 until snareFrames) {
                    val frame = startFrame + f
                    if (frame >= totalFrames) break
                    val t = f.toDouble() / sampleRate
                    val tone = sin(2.0 * PI * 180.0 * t) * exp(-t * 12.0) * 0.4
                    val snap = (Math.random() * 2.0 - 1.0) * exp(-t * 18.0) * 0.55
                    val sample = (tone + snap).toFloat()
                    left[frame] += sample
                    right[frame] += sample
                }
            }

            // Swing vinyl hi-hat
            for (sub in 0 until 2) {
                val hatStart = startFrame + (sub * beatFrames) / 2 + (if (sub == 1) (0.04 * sampleRate).toInt() else 0)
                val hatFrames = (0.08 * sampleRate).toInt()
                for (f in 0 until hatFrames) {
                    val frame = hatStart + f
                    if (frame >= totalFrames) break
                    val t = f.toDouble() / sampleRate
                    val noise = ((Math.random() * 2.0 - 1.0) * 0.2).toFloat()
                    val env = exp(-t * 24.0).toFloat()
                    left[frame] += noise * env
                    right[frame] += noise * env
                }
            }

            // Warm Rhodes Piano chord stab on bar start
            if (barPos == 0) {
                val chordFrames = (1.5 * sampleRate).toInt().coerceAtMost(totalFrames - startFrame)
                for (f in 0 until chordFrames) {
                    val frame = startFrame + f
                    val t = f.toDouble() / sampleRate
                    val env = exp(-t * 2.2).toFloat()
                    // G#m9: G#3 (207.65 Hz), B3 (246.94 Hz), D#4 (311.13 Hz), F#4 (369.99 Hz)
                    val c1 = sin(2.0 * PI * 207.65 * t)
                    val c2 = sin(2.0 * PI * 246.94 * t)
                    val c3 = sin(2.0 * PI * 311.13 * t)
                    val c4 = sin(2.0 * PI * 369.99 * t)
                    val chord = ((c1 + c2 + c3 + c4) * 0.12 * env).toFloat()
                    left[frame] += chord * 0.9f
                    right[frame] += chord * 1.1f
                }
            }
        }

        normalizeBuffer(left, right, totalFrames)

        val metadata = AudioMetadata(
            title = "Midnight Boom Bap",
            artist = "Oracle DJ Studio",
            sampleRate = sampleRate,
            bitDepth = 24,
            channels = 2,
            durationSeconds = totalSeconds,
            fileSizeBytes = totalFrames * 2 * 3L,
            peakDb = -1.0f,
            rmsDb = -11.0f,
            formatName = "WAV 24-bit 48kHz",
            filePath = "asset://deck_hiphop.wav"
        )

        return WavDecoder.DecodeResult(
            AudioBuffer(left, right, sampleRate, 24, sampleRate, 2),
            metadata
        )
    }

    fun generateTranceTrack(): WavDecoder.DecodeResult {
        val sampleRate = 48000
        val bpm = 138.0
        val bars = 32
        val beatsPerBar = 4
        val totalBeats = bars * beatsPerBar
        val secondsPerBeat = 60.0 / bpm
        val totalSeconds = totalBeats * secondsPerBeat
        val totalFrames = (totalSeconds * sampleRate).toInt()

        val left = FloatArray(totalFrames)
        val right = FloatArray(totalFrames)
        val beatFrames = (secondsPerBeat * sampleRate).toInt()

        // 138 BPM Uplifting Trance Anthem (Key: 9A / Em)
        for (beat in 0 until totalBeats) {
            val startFrame = (beat * secondsPerBeat * sampleRate).toInt()

            // Punchy Punchy Trance Kick on each downbeat
            val kickFrames = (0.28 * sampleRate).toInt()
            for (f in 0 until kickFrames) {
                val frame = startFrame + f
                if (frame >= totalFrames) break
                val t = f.toDouble() / sampleRate
                val freq = 52.0 + 130.0 * exp(-t * 24.0)
                val amp = exp(-t * 7.5)
                val sample = (sin(2.0 * PI * freq * t) * amp * 0.8).toFloat()
                left[frame] += sample
                right[frame] += sample
            }

            // Sizzling offbeat open hi-hat
            val hatStart = startFrame + beatFrames / 2
            val hatFrames = (0.16 * sampleRate).toInt()
            for (f in 0 until hatFrames) {
                val frame = hatStart + f
                if (frame >= totalFrames) break
                val t = f.toDouble() / sampleRate
                val noise = ((Math.random() * 2.0 - 1.0) * 0.28).toFloat()
                val env = exp(-t * 20.0).toFloat()
                left[frame] += noise * env * 0.85f
                right[frame] += noise * env * 1.15f
            }

            // Rolling Trance Bassline (3 16th-notes following kick)
            for (sub in 1 until 4) {
                val bassStart = startFrame + (sub * beatFrames) / 4
                val bassFrames = (0.14 * sampleRate).toInt().coerceAtMost(beatFrames / 4)
                for (f in 0 until bassFrames) {
                    val frame = bassStart + f
                    if (frame >= totalFrames) break
                    val t = f.toDouble() / sampleRate
                    // E1 = 41.2 Hz, E2 = 82.4 Hz
                    val saw = sin(2.0 * PI * 82.4 * t) + 0.4 * sin(2.0 * PI * 164.8 * t)
                    val env = exp(-t * 18.0)
                    val sample = (saw * env * 0.4).toFloat()
                    left[frame] += sample
                    right[frame] += sample
                }
            }

            // Supersaw Arpeggio Lead (Em: E4 329.63Hz, G4 392.00Hz, B4 493.88Hz, D5 587.33Hz)
            val notes = doubleArrayOf(329.63, 392.00, 493.88, 587.33)
            for (sub in 0 until 4) {
                val leadStart = startFrame + (sub * beatFrames) / 4
                val leadFrames = (0.12 * sampleRate).toInt()
                val noteFreq = notes[(beat * 4 + sub) % notes.size]
                for (f in 0 until leadFrames) {
                    val frame = leadStart + f
                    if (frame >= totalFrames) break
                    val t = f.toDouble() / sampleRate
                    // Detuned saws for massive stereo spread
                    val sL = sin(2.0 * PI * noteFreq * t) + 0.3 * sin(2.0 * PI * (noteFreq * 1.01) * t)
                    val sR = sin(2.0 * PI * noteFreq * t) + 0.3 * sin(2.0 * PI * (noteFreq * 0.99) * t)
                    val env = exp(-t * 14.0).toFloat()
                    left[frame] += (sL * env * 0.22).toFloat()
                    right[frame] += (sR * env * 0.22).toFloat()
                }
            }
        }

        normalizeBuffer(left, right, totalFrames)

        val metadata = AudioMetadata(
            title = "Starlight Vocal Trance",
            artist = "Oracle DJ Studio",
            sampleRate = sampleRate,
            bitDepth = 24,
            channels = 2,
            durationSeconds = totalSeconds,
            fileSizeBytes = totalFrames * 2 * 3L,
            peakDb = -1.0f,
            rmsDb = -10.8f,
            formatName = "WAV 24-bit 48kHz",
            filePath = "asset://deck_trance.wav"
        )

        return WavDecoder.DecodeResult(
            AudioBuffer(left, right, sampleRate, 24, sampleRate, 2),
            metadata
        )
    }

    private fun normalizeBuffer(left: FloatArray, right: FloatArray, totalFrames: Int) {
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
    }
}
