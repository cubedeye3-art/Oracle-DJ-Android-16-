package com.example.audio.model

enum class CrossfaderCurve(val label: String) {
    LINEAR("Linear"),
    CONSTANT_POWER("Constant Power"),
    SCRATCH("Scratch Cut")
}

enum class CrossfaderAssign {
    DECK_A,
    DECK_B,
    THRU
}

data class ChannelMixerState(
    val gain: Float = 1.0f,         // 0.0 .. 2.0 (nominal 1.0 = 0dB)
    val high: Float = 1.0f,         // 0.0 (kill) .. 1.0 (flat) .. 2.0 (+6dB)
    val mid: Float = 1.0f,          // 0.0 (kill) .. 1.0 (flat) .. 2.0 (+6dB)
    val low: Float = 1.0f,          // 0.0 (kill) .. 1.0 (flat) .. 2.0 (+6dB)
    val filter: Float = 0.0f,       // -1.0 (LPF) .. 0.0 (off) .. +1.0 (HPF)
    val pan: Float = 0.0f,          // -1.0 (L) .. 0.0 (C) .. +1.0 (R)
    val fader: Float = 1.0f,        // 0.0 .. 1.0
    val cueMonitor: Boolean = false,
    val crossfaderAssign: CrossfaderAssign = CrossfaderAssign.DECK_A,
    val vuLevelL: Float = 0f,
    val vuLevelR: Float = 0f
)

data class MixerState(
    val channelA: ChannelMixerState = ChannelMixerState(crossfaderAssign = CrossfaderAssign.DECK_A),
    val channelB: ChannelMixerState = ChannelMixerState(crossfaderAssign = CrossfaderAssign.DECK_B),
    val crossfaderPosition: Float = 0.5f, // 0.0 (Deck A) .. 0.5 (Center) .. 1.0 (Deck B)
    val crossfaderCurve: CrossfaderCurve = CrossfaderCurve.CONSTANT_POWER,
    val masterGain: Float = 1.0f,
    val masterPan: Float = 0.0f,
    val preMasterVuL: Float = 0f,
    val preMasterVuR: Float = 0f,
    val masterVuL: Float = 0f,
    val masterVuR: Float = 0f
)
