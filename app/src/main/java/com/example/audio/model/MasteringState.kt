package com.example.audio.model

enum class MasteringPreset(val label: String, val description: String) {
    TRANSPARENT("Transparent", "Uncolored transparent peak limiting and subtle bus glue"),
    CLUB("Club", "Punchy sub-bass boost, crisp highs, and dynamic sidechain impact"),
    LOUD("Loud", "Aggressive multi-band compression and soft clipping for maximum RMS"),
    WARM("Warm", "Analog tube saturation, roll-off above 16kHz, smooth low-mids"),
    CLEAN("Clean", "Linear-phase style EQ balance, transparent dynamics"),
    BASS_HEAVY("Bass Heavy", "Deep sub harmonic enhancement below 80Hz"),
    STREAMING("Streaming", "Targeted -14 LUFS integrated with -1.0 dBFS true peak ceiling"),
    DJ_MASTER("DJ Master", "Standard high-energy festival and club PA master profile")
}

data class MasteringStageBypass(
    val inputTrimBypass: Boolean = false,
    val dcProtectionBypass: Boolean = false,
    val parametricEqBypass: Boolean = false,
    val dynamicEqBypass: Boolean = false,
    val multibandCompBypass: Boolean = false,
    val busCompBypass: Boolean = false,
    val saturationBypass: Boolean = false,
    val stereoProcessorBypass: Boolean = false,
    val clipperBypass: Boolean = false,
    val truePeakLimiterBypass: Boolean = false,
    val outputCeilingBypass: Boolean = false
)

data class MasteringParameters(
    val inputTrimDb: Float = 0.0f,
    val eqLowGainDb: Float = 0.5f,
    val eqMidLowGainDb: Float = 0.0f,
    val eqMidHighGainDb: Float = 0.5f,
    val eqHighGainDb: Float = 1.0f,
    val dynEqThresholdDb: Float = -12.0f,
    val multibandThresholdDb: Float = -14.0f,
    val busCompThresholdDb: Float = -16.0f,
    val busCompRatio: Float = 2.5f,
    val busCompAttackMs: Float = 30.0f,
    val busCompReleaseMs: Float = 150.0f,
    val saturationDrive: Float = 0.2f,
    val stereoWidth: Float = 1.15f,     // 0.0 (mono) .. 1.0 (normal) .. 2.0 (super-wide)
    val clipperDriveDb: Float = 1.5f,
    val limiterThresholdDb: Float = -0.5f,
    val limiterCeilingDb: Float = -0.1f,
    val outputGainDb: Float = 0.0f
)

data class MasterMeterReadout(
    val lufsIntegrated: Float = -14.2f,
    val lufsShortTerm: Float = -13.8f,
    val truePeakDb: Float = -0.1f,
    val rmsDb: Float = -11.5f,
    val peakDb: Float = -0.05f,
    val dynamicRange: Float = 9.8f
)

data class MasteringState(
    val preset: MasteringPreset = MasteringPreset.DJ_MASTER,
    val bypass: MasteringStageBypass = MasteringStageBypass(),
    val params: MasteringParameters = MasteringParameters(),
    val meter: MasterMeterReadout = MasterMeterReadout()
)
