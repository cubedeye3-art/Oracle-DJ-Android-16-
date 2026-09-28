package com.example.audio.model

/**
 * High-resolution audio buffer storing stereo samples in 32-bit floating point format (-1.0f .. 1.0f).
 */
class AudioBuffer(
    val leftChannel: FloatArray,
    val rightChannel: FloatArray,
    val sampleRate: Int,
    val originalBitDepth: Int,
    val originalSampleRate: Int,
    val channels: Int
) {
    val frameCount: Int = leftChannel.size
    val durationSeconds: Double = if (sampleRate > 0) frameCount.toDouble() / sampleRate else 0.0

    fun getSample(channel: Int, frameIndex: Int): Float {
        if (frameIndex < 0 || frameIndex >= frameCount) return 0f
        return if (channel == 0) leftChannel[frameIndex] else rightChannel[frameIndex]
    }
}

/**
 * Source file technical audio metadata.
 */
data class AudioMetadata(
    val title: String,
    val artist: String = "Unknown Artist",
    val sampleRate: Int,
    val bitDepth: Int,
    val channels: Int,
    val durationSeconds: Double,
    val fileSizeBytes: Long,
    val peakDb: Float = 0f,
    val rmsDb: Float = -12f,
    val formatName: String = "WAV",
    val filePath: String = ""
)

enum class DeckId {
    DECK_A,
    DECK_B
}

enum class PlayState {
    STOPPED,
    PLAYING,
    PAUSED,
    CUE_AUDITION
}

enum class PitchRange(val maxPercent: Float) {
    FOUR(0.04f),
    EIGHT(0.08f),
    SIXTEEN(0.16f),
    FIFTY(0.50f),
    HUNDRED(1.00f);

    fun formatLabel(): String = "±${(maxPercent * 100).toInt()}%"
}

enum class SyncMode {
    OFF,
    BPM_SYNC,
    BEAT_SYNC,
    PHASE_SYNC
}

data class HotCue(
    val id: Int,
    val positionSeconds: Double,
    val label: String,
    val colorHex: Long
)

data class LoopState(
    val active: Boolean = false,
    val startSeconds: Double = 0.0,
    val endSeconds: Double = 0.0,
    val lengthBeats: Float = 4f,
    val isRoll: Boolean = false,
    val slipReturnPositionSeconds: Double = 0.0
)
