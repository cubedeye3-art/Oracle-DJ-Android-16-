package com.example.audio.engine

import com.example.audio.analysis.BpmDetector
import com.example.audio.analysis.KeyDetector
import com.example.audio.analysis.WaveformAnalyzer
import com.example.audio.analysis.WaveformData
import com.example.audio.dsp.ChannelEqFilter
import com.example.audio.dsp.DJFilter
import com.example.audio.dsp.DspUtils
import com.example.audio.dsp.TimeStretcher
import com.example.audio.fx.FxRack
import com.example.audio.model.AudioBuffer
import com.example.audio.model.AudioMetadata
import com.example.audio.model.DeckId
import com.example.audio.model.DeckUiState
import com.example.audio.model.HotCue
import com.example.audio.model.LoopState
import com.example.audio.model.PitchRange
import com.example.audio.model.PlayState
import kotlin.math.abs
import kotlin.math.max

class DeckAudioPlayer(
    val deckId: DeckId,
    private var sampleRate: Int = 48000
) {
    // Current loaded track
    @Volatile var buffer: AudioBuffer? = null
    @Volatile var metadata: AudioMetadata? = null
    @Volatile var waveformData: WaveformData? = null

    // Playback state
    @Volatile var playState: PlayState = PlayState.STOPPED
    @Volatile var currentFramePosition: Double = 0.0
    @Volatile var cueFramePosition: Double = 0.0

    // Slip mode virtual playhead
    @Volatile var slipMode: Boolean = false
    @Volatile var slipFramePosition: Double = 0.0

    // Scratch physics & jog wheel
    @Volatile var isScratching: Boolean = false
    @Volatile var scratchVelocityRate: Double = 0.0 // rate driven by touch
    @Volatile var jogAngleDegrees: Float = 0f
    @Volatile var pitchBendRate: Float = 0f

    // Pitch & Tempo
    @Volatile var tempoPercent: Float = 0.0f // -1.0 .. +1.0
    @Volatile var pitchRange: PitchRange = PitchRange.EIGHT
        private set
    @Volatile var keyLock: Boolean = true
    @Volatile var vinylMode: Boolean = true
    @Volatile var reverse: Boolean = false
    @Volatile var quantize: Boolean = true

    // BPM & Beat Grid
    @Volatile var originalBpm: Double = 0.0
    @Volatile var currentBpm: Double = 0.0
    @Volatile var beatIntervalFrames: Double = 0.0
    @Volatile var firstBeatOffsetFrames: Double = 0.0
    @Volatile var musicalKey: String = "--"
    @Volatile var camelotKey: String = "--"

    // Loops
    @Volatile var loopState: LoopState = LoopState()

    // 8 Hot Cues
    private val hotCues = MutableList(8) { index ->
        HotCue(
            id = index + 1,
            positionSeconds = -1.0,
            label = "CUE ${index + 1}",
            colorHex = when (index % 8) {
                0 -> 0xFF00E5FF // Cyan
                1 -> 0xFFFF9100 // Amber
                2 -> 0xFF00E676 // Lime
                3 -> 0xFFD500F9 // Magenta
                4 -> 0xFFFF1744 // Red
                5 -> 0xFFFFEA00 // Yellow
                6 -> 0xFF2979FF // Blue
                else -> 0xFF76FF03
            }
        )
    }

    // DSP components
    val eq = ChannelEqFilter(sampleRate)
    val filter = DJFilter(sampleRate)
    val fxRack = FxRack(sampleRate)
    private val timeStretcher = TimeStretcher(sampleRate)

    // Channel mixer parameters
    @Volatile var trimGain: Float = 1.0f
    @Volatile var channelFader: Float = 1.0f
    @Volatile var pan: Float = 0.0f
    @Volatile var cueMonitor: Boolean = false

    // VU meter levels
    @Volatile var vuLevelL: Float = 0f
    @Volatile var vuLevelR: Float = 0f

    fun setSampleRate(sr: Int) {
        if (sr > 0) {
            sampleRate = sr
            eq.setSampleRate(sr)
            filter.setSampleRate(sr)
            fxRack.setSampleRate(sr)
            timeStretcher.setSampleRate(sr)
            beatIntervalFrames = if (currentBpm > 0.0) (sampleRate * 60.0 / currentBpm) else 0.0
        }
    }

    fun loadTrack(
        newBuffer: AudioBuffer,
        newMetadata: AudioMetadata,
        cachedBpm: Double? = null,
        cachedKey: String? = null,
        cachedFirstBeatOffsetSec: Double? = null
    ) {
        buffer = newBuffer
        metadata = newMetadata
        currentFramePosition = 0.0
        cueFramePosition = 0.0
        slipFramePosition = 0.0
        playState = PlayState.STOPPED
        isScratching = false
        scratchVelocityRate = 0.0

        if (cachedBpm != null && cachedBpm > 0.0) {
            originalBpm = cachedBpm
            currentBpm = cachedBpm * (1.0 + tempoPercent * pitchRange.maxPercent)
            beatIntervalFrames = (sampleRate * 60.0 / originalBpm)
            firstBeatOffsetFrames = (cachedFirstBeatOffsetSec ?: 0.0) * sampleRate
        } else {
            val bpmRes = BpmDetector.detect(newBuffer)
            originalBpm = bpmRes.bpm
            currentBpm = if (bpmRes.bpm > 0) bpmRes.bpm * (1.0 + tempoPercent * pitchRange.maxPercent) else 0.0
            beatIntervalFrames = if (originalBpm > 0) (sampleRate * 60.0 / originalBpm) else (sampleRate * 0.5)
            firstBeatOffsetFrames = bpmRes.firstBeatOffsetSeconds * sampleRate
        }

        if (!cachedKey.isNullOrBlank() && cachedKey != "--") {
            musicalKey = cachedKey
            camelotKey = cachedKey.substringBefore(" ").substringBefore("/")
        } else {
            val keyRes = KeyDetector.detect(newBuffer)
            musicalKey = if (keyRes.keyName.isNotBlank()) "${keyRes.camelotCode} / ${keyRes.keyName}" else "--"
            camelotKey = keyRes.camelotCode.ifBlank { "--" }
        }

        waveformData = WaveformAnalyzer.analyze(newBuffer)

        // Clear cues
        for (i in 0 until 8) {
            hotCues[i] = hotCues[i].copy(positionSeconds = -1.0)
        }
        loopState = LoopState()
    }

    fun play() {
        if (buffer != null) {
            playState = PlayState.PLAYING
            slipFramePosition = currentFramePosition
        }
    }

    fun pause() {
        playState = PlayState.PAUSED
        isScratching = false
        scratchVelocityRate = 0.0
    }

    fun togglePlayPause() {
        if (playState == PlayState.PLAYING) pause() else play()
    }

    fun cuePress(isDown: Boolean) {
        val buf = buffer ?: return
        if (isDown) {
            if (playState == PlayState.PLAYING) {
                // If playing, pause and jump back to cue point
                playState = PlayState.PAUSED
                currentFramePosition = cueFramePosition
                slipFramePosition = cueFramePosition
            } else {
                // If paused, set cue point at current position or audition
                if (currentFramePosition != cueFramePosition && playState == PlayState.STOPPED) {
                    cueFramePosition = currentFramePosition
                }
                playState = PlayState.CUE_AUDITION
            }
        } else {
            // Cue released: return to cue point and pause
            if (playState == PlayState.CUE_AUDITION) {
                playState = PlayState.PAUSED
                currentFramePosition = cueFramePosition
            }
        }
    }

    fun setCuePointHere() {
        val target = if (quantize) quantizeFrame(currentFramePosition) else currentFramePosition
        cueFramePosition = target
    }

    fun triggerHotCue(index: Int) {
        if (index !in 0..7) return
        val cue = hotCues[index]
        if (cue.positionSeconds < 0) {
            // Set hot cue here
            val target = if (quantize) quantizeFrame(currentFramePosition) else currentFramePosition
            hotCues[index] = cue.copy(positionSeconds = target / sampleRate)
        } else {
            // Jump to hot cue
            val targetFrame = cue.positionSeconds * sampleRate
            currentFramePosition = targetFrame
            if (slipMode) {
                // slip continues advancing
            } else {
                slipFramePosition = targetFrame
            }
            if (playState != PlayState.PLAYING) {
                playState = PlayState.PLAYING
            }
        }
    }

    fun deleteHotCue(index: Int) {
        if (index in 0..7) {
            hotCues[index] = hotCues[index].copy(positionSeconds = -1.0)
        }
    }

    fun quantizeFrame(frame: Double): Double {
        if (beatIntervalFrames <= 0) return frame
        val rel = frame - firstBeatOffsetFrames
        val beatNum = Math.round(rel / beatIntervalFrames)
        return max(0.0, firstBeatOffsetFrames + beatNum * beatIntervalFrames)
    }

    fun setTempo(percent: Float) {
        tempoPercent = percent.coerceIn(-1.0f, 1.0f)
        val rateFactor = 1.0 + (tempoPercent * pitchRange.maxPercent)
        currentBpm = if (originalBpm > 0.0) originalBpm * rateFactor else 0.0
        beatIntervalFrames = if (currentBpm > 0.0) (sampleRate * 60.0 / currentBpm) else 0.0
        fxRack.currentBpm = if (currentBpm > 0.0) currentBpm else 120.0
    }

    fun setPitchRange(range: PitchRange) {
        pitchRange = range
        setTempo(tempoPercent)
    }

    fun pitchBend(amount: Float) {
        pitchBendRate = amount.coerceIn(-0.5f, 0.5f)
    }

    // Touch jog wheel scratch handlers
    fun startScratch() {
        isScratching = true
        scratchVelocityRate = 0.0
    }

    fun updateScratch(deltaAngleDegrees: Float, deltaTimeSeconds: Float) {
        if (!isScratching) return
        jogAngleDegrees = (jogAngleDegrees + deltaAngleDegrees) % 360f

        // Convert angular velocity to playback rate
        // 33.33 RPM = 360 deg in 1.8 seconds = 200 deg/sec = rate 1.0
        val degPerSec = if (deltaTimeSeconds > 0.0001f) deltaAngleDegrees / deltaTimeSeconds else 0f
        val calculatedRate = (degPerSec / 200.0).toDouble()
        // Smooth rate to avoid harsh discontinuities
        scratchVelocityRate = 0.6 * scratchVelocityRate + 0.4 * calculatedRate
    }

    fun endScratch() {
        isScratching = false
        scratchVelocityRate = 0.0
        if (slipMode) {
            currentFramePosition = slipFramePosition
        }
    }

    fun toggleLoop(lengthBeats: Float) {
        if (loopState.active && loopState.lengthBeats == lengthBeats) {
            exitLoop()
        } else {
            setLoop(lengthBeats, false)
        }
    }

    fun setLoop(lengthBeats: Float, isRoll: Boolean) {
        val start = if (quantize) quantizeFrame(currentFramePosition) else currentFramePosition
        val loopFrames = lengthBeats * (sampleRate * 60.0 / currentBpm)
        val end = start + loopFrames
        loopState = LoopState(
            active = true,
            startSeconds = start / sampleRate,
            endSeconds = end / sampleRate,
            lengthBeats = lengthBeats,
            isRoll = isRoll,
            slipReturnPositionSeconds = if (isRoll) currentFramePosition / sampleRate else 0.0
        )
    }

    fun exitLoop() {
        if (loopState.isRoll) {
            currentFramePosition = loopState.slipReturnPositionSeconds * sampleRate
        }
        loopState = LoopState()
    }

    fun halveLoop() {
        if (loopState.active) {
            val newBeats = max(0.03125f, loopState.lengthBeats / 2.0f)
            setLoop(newBeats, loopState.isRoll)
        }
    }

    fun doubleLoop() {
        if (loopState.active) {
            val newBeats = minOf(32.0f, loopState.lengthBeats * 2.0f)
            setLoop(newBeats, loopState.isRoll)
        }
    }

    /**
     * Renders next audio block on the real-time audio thread.
     */
    fun processBlock(outL: FloatArray, outR: FloatArray, frameCount: Int) {
        val buf = buffer
        if (buf == null || (playState == PlayState.STOPPED && !isScratching)) {
            outL.fill(0f, 0, frameCount)
            outR.fill(0f, 0, frameCount)
            vuLevelL = 0f
            vuLevelR = 0f
            return
        }

        val baseSpeed = 1.0 + (tempoPercent * pitchRange.maxPercent)
        var effectiveRate = when {
            isScratching -> scratchVelocityRate
            playState == PlayState.PLAYING || playState == PlayState.CUE_AUDITION -> {
                var r = baseSpeed + pitchBendRate
                if (reverse) r = -r
                r
            }
            else -> 0.0
        }

        // Decay scratch inertia if released
        if (!isScratching && abs(scratchVelocityRate) > 0.01) {
            scratchVelocityRate *= 0.85
            effectiveRate += scratchVelocityRate
        }

        if (abs(effectiveRate) < 0.001) {
            outL.fill(0f, 0, frameCount)
            outR.fill(0f, 0, frameCount)
            vuLevelL = 0f
            vuLevelR = 0f
            return
        }

        // Render frames using TimeStretcher
        val newPos = timeStretcher.processFrames(
            buffer = buf,
            position = currentFramePosition,
            rate = effectiveRate,
            keyLock = keyLock && !isScratching,
            outL = outL,
            outR = outR,
            frameCount = frameCount
        )
        currentFramePosition = newPos

        // Advance slip position
        if (playState == PlayState.PLAYING) {
            slipFramePosition = (slipFramePosition + frameCount * baseSpeed) % buf.frameCount
        }

        // Handle loop wrap
        if (loopState.active && loopState.endSeconds > loopState.startSeconds) {
            val loopStartFrame = loopState.startSeconds * sampleRate
            val loopEndFrame = loopState.endSeconds * sampleRate
            if (currentFramePosition >= loopEndFrame) {
                currentFramePosition = loopStartFrame + (currentFramePosition - loopEndFrame)
            }
        }

        // Update jog wheel rotation angle
        jogAngleDegrees = ((jogAngleDegrees + (effectiveRate * frameCount * 360.0 / (sampleRate * 1.8)).toFloat()) % 360f)

        // DSP Stages: EQ -> DJ Filter -> FX Rack -> Trim Gain & Fader
        for (i in 0 until frameCount) {
            outL[i] = filter.processLeft(eq.processLeft(outL[i]))
            outR[i] = filter.processRight(eq.processRight(outR[i]))
        }

        // FX Rack
        fxRack.processBlock(outL, outR, outL, outR, frameCount)

        // Volume & Pan
        val gain = trimGain * channelFader
        val panL = if (pan > 0) 1f - pan else 1f
        val panR = if (pan < 0) 1f + pan else 1f

        var maxL = 0f
        var maxR = 0f

        for (i in 0 until frameCount) {
            outL[i] *= (gain * panL)
            outR[i] *= (gain * panR)

            val aL = abs(outL[i])
            val aR = abs(outR[i])
            if (aL > maxL) maxL = aL
            if (aR > maxR) maxR = aR
        }

        // VU meter smoothing
        vuLevelL = vuLevelL * 0.7f + maxL * 0.3f
        vuLevelR = vuLevelR * 0.7f + maxR * 0.3f
    }

    fun getUiState(): DeckUiState {
        val buf = buffer
        val dur = buf?.durationSeconds ?: 0.0
        val pos = currentFramePosition / sampleRate

        // Calculate phase in beats (-0.5 .. +0.5)
        val relFrames = currentFramePosition - firstBeatOffsetFrames
        val beatFrac = if (beatIntervalFrames > 0) {
            val b = (relFrames / beatIntervalFrames)
            var frac = (b - Math.floor(b)).toFloat()
            if (frac > 0.5f) frac -= 1.0f
            frac
        } else 0f

        val beatNumber = if (beatIntervalFrames > 0) {
            (((relFrames / beatIntervalFrames).toInt() % 4) + 1).coerceIn(1, 4)
        } else 1

        return DeckUiState(
            deckId = deckId,
            metadata = metadata,
            playState = playState,
            currentPositionSeconds = pos,
            durationSeconds = dur,
            currentBpm = currentBpm,
            originalBpm = originalBpm,
            tempoPercent = tempoPercent,
            pitchRange = pitchRange,
            keyLock = keyLock,
            slipMode = slipMode,
            slipPositionSeconds = slipFramePosition / sampleRate,
            vinylMode = vinylMode,
            reverse = reverse,
            quantize = quantize,
            musicalKey = musicalKey,
            camelotKey = camelotKey,
            phaseOffset = beatFrac,
            beatNumber = beatNumber,
            cuePositionSeconds = cueFramePosition / sampleRate,
            hotCues = hotCues.toList(),
            loopState = loopState,
            jogAngleDegrees = jogAngleDegrees,
            isScratching = isScratching,
            isPitchBending = abs(pitchBendRate) > 0.001f,
            pitchBendRate = pitchBendRate,
            overviewWaveformPeaks = waveformData?.overviewPeaks,
            detailWaveformPeaks = waveformData?.detailPeaks
        )
    }
}
