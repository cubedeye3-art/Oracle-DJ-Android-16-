package com.example.audio.engine

import com.example.audio.mastering.MasteringChain
import com.example.audio.model.CrossfaderAssign
import com.example.audio.model.CrossfaderCurve
import com.example.audio.model.MixerState
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

class MixerEngine(
    val deckA: DeckAudioPlayer,
    val deckB: DeckAudioPlayer,
    private var sampleRate: Int = 48000
) {
    var state: MixerState = MixerState()
    val masteringChain = MasteringChain(sampleRate)

    // Temporary channel buffers
    private val bufferA_L = FloatArray(4096)
    private val bufferA_R = FloatArray(4096)
    private val bufferB_L = FloatArray(4096)
    private val bufferB_R = FloatArray(4096)

    // Pre-master and master peak levels
    var preMasterPeakL = 0f
    var preMasterPeakR = 0f
    var masterPeakL = 0f
    var masterPeakR = 0f

    fun setSampleRate(sr: Int) {
        if (sr > 0) {
            sampleRate = sr
            deckA.setSampleRate(sr)
            deckB.setSampleRate(sr)
            masteringChain.setSampleRate(sr)
        }
    }

    fun setCrossfaderPosition(pos: Float) {
        state = state.copy(crossfaderPosition = pos.coerceIn(0f, 1f))
    }

    fun setCrossfaderCurve(curve: CrossfaderCurve) {
        state = state.copy(crossfaderCurve = curve)
    }

    fun setMasterGain(gain: Float) {
        state = state.copy(masterGain = gain.coerceIn(0f, 2f))
    }

    /**
     * Calculates crossfader gain coefficients (gainA, gainB) for the current position and curve.
     */
    fun calculateCrossfaderGains(pos: Float, curve: CrossfaderCurve): Pair<Float, Float> {
        val x = pos.coerceIn(0f, 1f)
        return when (curve) {
            CrossfaderCurve.LINEAR -> {
                Pair(1.0f - x, x)
            }
            CrossfaderCurve.CONSTANT_POWER -> {
                val rad = x * (PI.toFloat() / 2.0f)
                Pair(cos(rad), sin(rad))
            }
            CrossfaderCurve.SCRATCH -> {
                val gainA = if (x > 0.95f) 0.0f else if (x > 0.5f) (1.0f - (x - 0.5f) * 2.0f).coerceIn(0f, 1f) else 1.0f
                val gainB = if (x < 0.05f) 0.0f else if (x < 0.5f) (x * 2.0f).coerceIn(0f, 1f) else 1.0f
                Pair(gainA, gainB)
            }
        }
    }

    /**
     * Real-time audio callback: renders both decks, mixes through crossfader,
     * processes master mastering chain, and writes final stereo frames to outL and outR.
     */
    fun processBlock(
        outL: FloatArray,
        outR: FloatArray,
        preMasterCaptureL: FloatArray?,
        preMasterCaptureR: FloatArray?,
        frameCount: Int
    ) {
        val count = minOf(frameCount, bufferA_L.size)

        // 1. Process Deck A
        deckA.processBlock(bufferA_L, bufferA_R, count)

        // 2. Process Deck B
        deckB.processBlock(bufferB_L, bufferB_R, count)

        // 3. Crossfader curves
        val (xfadeA, xfadeB) = calculateCrossfaderGains(state.crossfaderPosition, state.crossfaderCurve)

        val assignA = state.channelA.crossfaderAssign
        val assignB = state.channelB.crossfaderAssign

        val multA = when (assignA) {
            CrossfaderAssign.DECK_A -> xfadeA
            CrossfaderAssign.DECK_B -> xfadeB
            CrossfaderAssign.THRU -> 1.0f
        }

        val multB = when (assignB) {
            CrossfaderAssign.DECK_A -> xfadeA
            CrossfaderAssign.DECK_B -> xfadeB
            CrossfaderAssign.THRU -> 1.0f
        }

        var prePeakL = 0f
        var prePeakR = 0f

        // Sum channels to Master bus
        for (i in 0 until count) {
            val sL = (bufferA_L[i] * multA) + (bufferB_L[i] * multB)
            val sR = (bufferA_R[i] * multA) + (bufferB_R[i] * multB)

            outL[i] = sL
            outR[i] = sR

            if (preMasterCaptureL != null) preMasterCaptureL[i] = sL
            if (preMasterCaptureR != null) preMasterCaptureR[i] = sR

            val aL = abs(sL)
            val aR = abs(sR)
            if (aL > prePeakL) prePeakL = aL
            if (aR > prePeakR) prePeakR = aR
        }

        preMasterPeakL = preMasterPeakL * 0.7f + prePeakL * 0.3f
        preMasterPeakR = preMasterPeakR * 0.7f + prePeakR * 0.3f

        // 4. Pass through 11-stage transparent mastering chain
        masteringChain.processBlock(outL, outR, count)

        // 5. Apply master volume fader & final soft clip protection
        val mGain = state.masterGain
        var postPeakL = 0f
        var postPeakR = 0f

        for (i in 0 until count) {
            val fL = (outL[i] * mGain).coerceIn(-1.0f, 1.0f)
            val fR = (outR[i] * mGain).coerceIn(-1.0f, 1.0f)

            outL[i] = fL
            outR[i] = fR

            val aL = abs(fL)
            val aR = abs(fR)
            if (aL > postPeakL) postPeakL = aL
            if (aR > postPeakR) postPeakR = aR
        }

        masterPeakL = masterPeakL * 0.7f + postPeakL * 0.3f
        masterPeakR = masterPeakR * 0.7f + postPeakR * 0.3f
    }
}
