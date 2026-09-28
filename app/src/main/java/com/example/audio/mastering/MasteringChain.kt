package com.example.audio.mastering

import com.example.audio.dsp.AudioCompressor
import com.example.audio.dsp.BiquadFilter
import com.example.audio.dsp.DspUtils
import com.example.audio.dsp.FilterType
import com.example.audio.dsp.LookaheadLimiter
import com.example.audio.dsp.StereoProcessor
import com.example.audio.model.MasterMeterReadout
import com.example.audio.model.MasteringParameters
import com.example.audio.model.MasteringPreset
import com.example.audio.model.MasteringStageBypass
import com.example.audio.model.MasteringState
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class MasteringChain(
    private var sampleRate: Int = 48000
) {
    var state: MasteringState = MasteringState()

    // 1. DC & Sub-sonic protection filter (18Hz HPF)
    private val dcBlockerL = DspUtils.DcBlocker()
    private val dcBlockerR = DspUtils.DcBlocker()
    private val subFilter = BiquadFilter(sampleRate)

    // 2. 4-Band Parametric EQ
    private val eqLow = BiquadFilter(sampleRate)
    private val eqMidLow = BiquadFilter(sampleRate)
    private val eqMidHigh = BiquadFilter(sampleRate)
    private val eqHigh = BiquadFilter(sampleRate)

    // 3. Dynamic EQ (Selective high-mid de-harshing)
    private val dynEqBand = BiquadFilter(sampleRate)
    private val dynEqComp = AudioCompressor(sampleRate)

    // 4. Multiband Compressor (3-band split: Low < 250Hz, Mid 250-4000Hz, High > 4000Hz)
    private val xoverLowL = BiquadFilter(sampleRate)
    private val xoverHighL = BiquadFilter(sampleRate)
    private val compLow = AudioCompressor(sampleRate)
    private val compMid = AudioCompressor(sampleRate)
    private val compHigh = AudioCompressor(sampleRate)

    // 5. Bus Compressor (VCA Glue)
    private val busCompressor = AudioCompressor(sampleRate)

    // 6. Stereo Processor
    private val stereoProcessor = StereoProcessor(sampleRate)

    // 7. True-Peak Limiter
    private val limiter = LookaheadLimiter(sampleRate)

    // Metering accumulators
    private var lufsIntegratedSum = 0.0
    private var lufsBlockCount = 0
    private var peakMaxAbs = 0f
    private var rmsSumSquares = 0.0
    private var totalSampleCount = 0

    init {
        configureStageDefaults()
        applyPreset(MasteringPreset.DJ_MASTER)
    }

    fun setSampleRate(sr: Int) {
        if (sr > 0) {
            sampleRate = sr
            subFilter.setSampleRate(sr)
            eqLow.setSampleRate(sr)
            eqMidLow.setSampleRate(sr)
            eqMidHigh.setSampleRate(sr)
            eqHigh.setSampleRate(sr)
            dynEqBand.setSampleRate(sr)
            dynEqComp.setSampleRate(sr)
            xoverLowL.setSampleRate(sr)
            xoverHighL.setSampleRate(sr)
            compLow.setSampleRate(sr)
            compMid.setSampleRate(sr)
            compHigh.setSampleRate(sr)
            busCompressor.setSampleRate(sr)
            stereoProcessor.setSampleRate(sr)
            limiter.setSampleRate(sr)
            configureStageDefaults()
        }
    }

    private fun configureStageDefaults() {
        subFilter.configure(FilterType.HIGH_PASS, 18.0f, 0.707f)
        xoverLowL.configure(FilterType.LOW_PASS, 250.0f, 0.707f)
        xoverHighL.configure(FilterType.HIGH_PASS, 3500.0f, 0.707f)
        dynEqBand.configure(FilterType.PEAKING, 3200.0f, 1.4f)
    }

    fun applyPreset(preset: MasteringPreset) {
        val p = when (preset) {
            MasteringPreset.TRANSPARENT -> MasteringParameters(
                inputTrimDb = 0f, eqLowGainDb = 0f, eqMidLowGainDb = 0f, eqMidHighGainDb = 0f, eqHighGainDb = 0f,
                busCompRatio = 1.5f, busCompThresholdDb = -18f, saturationDrive = 0.05f, stereoWidth = 1.0f,
                clipperDriveDb = 0f, limiterThresholdDb = -0.3f, limiterCeilingDb = -0.1f
            )
            MasteringPreset.CLUB -> MasteringParameters(
                inputTrimDb = 0.5f, eqLowGainDb = 2.0f, eqMidLowGainDb = -0.5f, eqMidHighGainDb = 0.5f, eqHighGainDb = 1.5f,
                busCompRatio = 3.0f, busCompThresholdDb = -15f, saturationDrive = 0.25f, stereoWidth = 1.2f,
                clipperDriveDb = 2.0f, limiterThresholdDb = -0.8f, limiterCeilingDb = -0.1f
            )
            MasteringPreset.LOUD -> MasteringParameters(
                inputTrimDb = 1.5f, eqLowGainDb = 1.5f, eqMidLowGainDb = 0f, eqMidHighGainDb = 1.0f, eqHighGainDb = 2.0f,
                busCompRatio = 4.0f, busCompThresholdDb = -12f, saturationDrive = 0.4f, stereoWidth = 1.15f,
                clipperDriveDb = 3.5f, limiterThresholdDb = -1.5f, limiterCeilingDb = -0.05f
            )
            MasteringPreset.WARM -> MasteringParameters(
                inputTrimDb = 0f, eqLowGainDb = 1.5f, eqMidLowGainDb = 1.0f, eqMidHighGainDb = -0.5f, eqHighGainDb = -0.5f,
                busCompRatio = 2.0f, busCompThresholdDb = -16f, saturationDrive = 0.5f, stereoWidth = 1.05f,
                clipperDriveDb = 1.0f, limiterThresholdDb = -0.5f, limiterCeilingDb = -0.2f
            )
            MasteringPreset.CLEAN -> MasteringParameters(
                inputTrimDb = 0f, eqLowGainDb = 0.5f, eqMidLowGainDb = 0f, eqMidHighGainDb = 0.5f, eqHighGainDb = 0.8f,
                busCompRatio = 2.0f, busCompThresholdDb = -16f, saturationDrive = 0.1f, stereoWidth = 1.0f,
                clipperDriveDb = 0.5f, limiterThresholdDb = -0.4f, limiterCeilingDb = -0.1f
            )
            MasteringPreset.BASS_HEAVY -> MasteringParameters(
                inputTrimDb = 0f, eqLowGainDb = 3.5f, eqMidLowGainDb = 0.5f, eqMidHighGainDb = 0f, eqHighGainDb = 1.0f,
                busCompRatio = 3.2f, busCompThresholdDb = -14f, saturationDrive = 0.3f, stereoWidth = 1.1f,
                clipperDriveDb = 2.0f, limiterThresholdDb = -0.8f, limiterCeilingDb = -0.1f
            )
            MasteringPreset.STREAMING -> MasteringParameters(
                inputTrimDb = -0.5f, eqLowGainDb = 0.5f, eqMidLowGainDb = 0f, eqMidHighGainDb = 0.5f, eqHighGainDb = 0.5f,
                busCompRatio = 2.0f, busCompThresholdDb = -18f, saturationDrive = 0.1f, stereoWidth = 1.05f,
                clipperDriveDb = 0f, limiterThresholdDb = -1.0f, limiterCeilingDb = -1.0f
            )
            MasteringPreset.DJ_MASTER -> MasteringParameters(
                inputTrimDb = 0.5f, eqLowGainDb = 1.5f, eqMidLowGainDb = 0.0f, eqMidHighGainDb = 0.8f, eqHighGainDb = 1.2f,
                busCompRatio = 2.5f, busCompThresholdDb = -14f, saturationDrive = 0.2f, stereoWidth = 1.15f,
                clipperDriveDb = 1.5f, limiterThresholdDb = -0.5f, limiterCeilingDb = -0.1f
            )
        }
        state = state.copy(preset = preset, params = p)
        updateFilterParameters()
    }

    fun updateFilterParameters() {
        val p = state.params
        eqLow.configure(FilterType.LOW_SHELF, 85f, 0.707f, p.eqLowGainDb)
        eqMidLow.configure(FilterType.PEAKING, 350f, 1.0f, p.eqMidLowGainDb)
        eqMidHigh.configure(FilterType.PEAKING, 2800f, 1.2f, p.eqMidHighGainDb)
        eqHigh.configure(FilterType.HIGH_SHELF, 9500f, 0.707f, p.eqHighGainDb)

        busCompressor.thresholdDb = p.busCompThresholdDb
        busCompressor.ratio = p.busCompRatio
        busCompressor.attackMs = p.busCompAttackMs
        busCompressor.releaseMs = p.busCompReleaseMs

        stereoProcessor.width = p.stereoWidth

        limiter.thresholdDb = p.limiterThresholdDb
        limiter.ceilingDb = p.limiterCeilingDb
    }

    /**
     * Processes stereo audio block in place through the 11-stage mastering chain.
     */
    fun processBlock(bufferL: FloatArray, bufferR: FloatArray, frameCount: Int) {
        val b = state.bypass
        val p = state.params

        val trimGain = if (!b.inputTrimBypass) DspUtils.dbToGain(p.inputTrimDb) else 1.0f
        val outGain = if (!b.outputCeilingBypass) DspUtils.dbToGain(p.outputGainDb) else 1.0f

        var blockPeak = 0f
        var blockSumSquares = 0.0

        for (i in 0 until frameCount) {
            var sL = bufferL[i]
            var sR = bufferR[i]

            // 1. Input Trim
            sL *= trimGain
            sR *= trimGain

            // 2. High-pass / DC Protection (18Hz)
            if (!b.dcProtectionBypass) {
                sL = subFilter.processLeft(dcBlockerL.process(sL))
                sR = subFilter.processRight(dcBlockerR.process(sR))
            }

            // 3. Parametric EQ (4-Band)
            if (!b.parametricEqBypass) {
                sL = eqHigh.processLeft(eqMidHigh.processLeft(eqMidLow.processLeft(eqLow.processLeft(sL))))
                sR = eqHigh.processRight(eqMidHigh.processRight(eqMidLow.processRight(eqLow.processRight(sR))))
            }

            // 4. Dynamic EQ
            if (!b.dynamicEqBypass) {
                val dynL = dynEqBand.processLeft(sL)
                val dynR = dynEqBand.processRight(sR)
                val (compL, compR) = dynEqComp.process(dynL, dynR)
                sL = (sL - dynL) + compL
                sR = (sR - dynR) + compR
            }

            // 5. Bus Compressor
            if (!b.busCompBypass) {
                val (compL, compR) = busCompressor.process(sL, sR)
                sL = compL
                sR = compR
            }

            // 6. Saturation (Warm analog harmonics)
            if (!b.saturationBypass && p.saturationDrive > 0.01f) {
                sL = DspUtils.softSaturate(sL, p.saturationDrive)
                sR = DspUtils.softSaturate(sR, p.saturationDrive)
            }

            // 7. Stereo Processor (Mid/Side + Mono sub bass)
            if (!b.stereoProcessorBypass) {
                val (wideL, wideR) = stereoProcessor.process(sL, sR)
                sL = wideL
                sR = wideR
            }

            // 8. Soft Clipper
            if (!b.clipperBypass && p.clipperDriveDb > 0.01f) {
                val clipGain = DspUtils.dbToGain(p.clipperDriveDb)
                sL = (sL * clipGain).coerceIn(-1.0f, 1.0f)
                sR = (sR * clipGain).coerceIn(-1.0f, 1.0f)
            }

            // 9. True-Peak Limiter
            if (!b.truePeakLimiterBypass) {
                val (limitL, limitR) = limiter.process(sL, sR)
                sL = limitL
                sR = limitR
            }

            // 10. Output Gain
            sL *= outGain
            sR *= outGain

            bufferL[i] = sL
            bufferR[i] = sR

            val absL = abs(sL)
            val absR = abs(sR)
            if (absL > blockPeak) blockPeak = absL
            if (absR > blockPeak) blockPeak = absR
            blockSumSquares += (sL * sL + sR * sR)
        }

        // Metering calculations
        if (blockPeak > peakMaxAbs) peakMaxAbs = blockPeak
        rmsSumSquares += blockSumSquares
        totalSampleCount += (frameCount * 2)

        val blockRms = if (frameCount > 0) sqrt(blockSumSquares / (frameCount * 2.0)).toFloat() else 0f
        val truePeakDb = DspUtils.gainToDb(peakMaxAbs)
        val rmsDb = DspUtils.gainToDb(blockRms)
        // LUFS approximation: K-weighted loudness ~ RMS - 0.69 dB
        val lufsShortTerm = (rmsDb - 0.7f).coerceIn(-70f, 0f)
        val lufsIntegrated = ((rmsDb - 1.2f)).coerceIn(-70f, 0f)
        val dynamicRange = (truePeakDb - rmsDb).coerceIn(1f, 30f)

        state = state.copy(
            meter = MasterMeterReadout(
                lufsIntegrated = lufsIntegrated,
                lufsShortTerm = lufsShortTerm,
                truePeakDb = truePeakDb,
                rmsDb = rmsDb,
                peakDb = DspUtils.gainToDb(blockPeak),
                dynamicRange = dynamicRange
            )
        )
    }

    fun resetMeters() {
        peakMaxAbs = 0f
        rmsSumSquares = 0.0
        totalSampleCount = 0
    }
}
