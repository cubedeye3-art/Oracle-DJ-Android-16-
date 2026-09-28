package com.example.audio.model

enum class LatencyProfile(val label: String, val frames: Int, val expectedLatencyMs: Float) {
    ULTRA_LOW("Ultra Low (Fastest)", 256, 5.3f),
    LOW("Low Latency", 512, 10.7f),
    BALANCED("Balanced (Recommended)", 1024, 21.3f),
    SAFE("Safe Buffer", 2048, 42.7f)
}

enum class ThermalPerformanceMode(val label: String, val description: String) {
    QUALITY("Quality Mode", "Max DSP precision, oversampled clipping & anti-aliasing"),
    BALANCED("Balanced Mode", "Dynamic load balancing with optimal battery & thermals"),
    BATTERY("Battery Saver", "Simplified background analysis & energy conservation")
}

data class EngineTelemetry(
    val sourceSampleRateA: Int = 0,
    val sourceBitDepthA: Int = 0,
    val sourceChannelsA: Int = 0,
    val sourceFormatNameA: String = "None",

    val sourceSampleRateB: Int = 0,
    val sourceBitDepthB: Int = 0,
    val sourceChannelsB: Int = 0,
    val sourceFormatNameB: String = "None",

    val dspSampleRate: Int = 48000,
    val outputDeviceSampleRate: Int = 48000,
    val outputDeviceName: String = "Speaker (POCO M8 Audio Subsystem)",
    val isUsbDacConnected: Boolean = false,
    val isBluetooth: Boolean = false,

    val dspCpuLoadPercent: Float = 0f,
    val bufferSizeFrames: Int = 512,
    val actualLatencyMs: Float = 10.7f,
    val underrunCount: Int = 0,
    val droppedBufferCount: Int = 0,

    val latencyProfile: LatencyProfile = LatencyProfile.LOW,
    val performanceMode: ThermalPerformanceMode = ThermalPerformanceMode.BALANCED,
    val thermalThrottlingStatus: String = "Normal (31°C)"
)
