package com.example.audio.streaming

enum class StreamState {
    IDLE,
    CONNECTING,
    LIVE,
    RECONNECTING,
    ERROR
}

data class AudioBitrateOption(
    val kbps: Int,
    val label: String,
    val description: String
)

val AVAILABLE_BITRATES = listOf(
    AudioBitrateOption(128, "128 kbps", "Standard Mobile (Low Data)"),
    AudioBitrateOption(192, "192 kbps", "High Fidelity (Recommended)"),
    AudioBitrateOption(256, "256 kbps", "Studio Master (HQ Stereo)"),
    AudioBitrateOption(320, "320 kbps", "Audiophile Pristine (Max)")
)

data class LiveStreamConfig(
    val platform: StreamPlatform = StreamPlatform.YOUTUBE,
    val ingestUrl: String = StreamPlatform.YOUTUBE.defaultIngestUrl,
    val streamKey: String = "",
    val streamTitle: String = "Oracle DJ Live Set - In The Mix",
    val bitrateKbps: Int = 256,
    val micTalkoverEnabled: Boolean = false,
    val micDuckingDb: Float = -12f,
    val autoReconnect: Boolean = true
)

data class LiveStreamMetrics(
    val state: StreamState = StreamState.IDLE,
    val platform: StreamPlatform = StreamPlatform.YOUTUBE,
    val durationSeconds: Long = 0L,
    val currentBitrateKbps: Int = 0,
    val framesSent: Long = 0L,
    val droppedFrames: Long = 0L,
    val networkLatencyMs: Int = 0,
    val networkStability: Float = 1.0f,
    val peakOutputLevelDb: Float = -60f,
    val errorMessage: String? = null,
    val connectedEndpoint: String = ""
)
