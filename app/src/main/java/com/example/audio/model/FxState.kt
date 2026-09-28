package com.example.audio.model

enum class FxType(val displayName: String, val param1Name: String, val param2Name: String, val param3Name: String) {
    DELAY("Delay", "Time", "Feedback", "Damping"),
    ECHO("Echo", "Length", "Feedback", "Freeze"),
    REVERB("Reverb", "Size", "Decay", "Damp"),
    FILTER_LFO("Filter LFO", "Rate", "Depth", "Resonance"),
    FLANGER("Flanger", "Rate", "Depth", "Feedback"),
    PHASER("Phaser", "Rate", "Depth", "Poles"),
    CHORUS("Chorus", "Rate", "Depth", "Voices"),
    BIT_CRUSHER("Bit Crusher", "Bit Depth", "Downsample", "Mix"),
    DISTORTION("Distortion", "Drive", "Tone", "Clipping"),
    SATURATION("Saturation", "Warmth", "Harmonics", "Curve"),
    VINYL("Vinyl Sim", "Crackle", "Warp", "Noise"),
    STUTTER("Stutter", "Length", "Quantize", "Decay"),
    GATE("Trance Gate", "Pattern", "Attack", "Release"),
    COMPRESSOR("Punch Comp", "Threshold", "Ratio", "Attack"),
    TRANSIENT_SHAPER("Trans Shaper", "Attack", "Sustain", "Punch"),
    FREQ_SHIFTER("Freq Shifter", "Shift Hz", "Feedback", "Range"),
    RING_MODULATOR("Ring Mod", "Carrier Hz", "Mod Depth", "Sine/Sq"),
    BEAT_REPEAT("Beat Repeat", "Repeat Len", "Grid", "Decay"),
    ROLL("Roll", "Size", "Filter", "Pitch"),
    TAPE_STOP("Tape Stop", "Stop Rate", "Brake Speed", "Curve")
}

enum class BeatDivision(val label: String, val multiplier: Float) {
    DIV_1_32("1/32", 0.125f),
    DIV_1_16("1/16", 0.25f),
    DIV_1_8("1/8", 0.5f),
    DIV_1_4("1/4", 1.0f),
    DIV_1_2("1/2", 2.0f),
    BAR_1("1 Bar", 4.0f),
    BAR_2("2 Bars", 8.0f),
    BAR_4("4 Bars", 16.0f)
}

data class FxUnitState(
    val type: FxType = FxType.DELAY,
    val enabled: Boolean = false,
    val dryWet: Float = 0.5f,
    val param1: Float = 0.5f,
    val param2: Float = 0.5f,
    val param3: Float = 0.5f,
    val beatDivision: BeatDivision = BeatDivision.DIV_1_4,
    val syncToBpm: Boolean = true
)
