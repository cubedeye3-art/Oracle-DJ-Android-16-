package com.example.audio.model

data class DeckUiState(
    val deckId: DeckId,
    val metadata: AudioMetadata? = null,
    val playState: PlayState = PlayState.STOPPED,
    val currentPositionSeconds: Double = 0.0,
    val durationSeconds: Double = 0.0,
    val currentBpm: Double = 126.0,
    val originalBpm: Double = 126.0,
    val tempoPercent: Float = 0.0f, // -1.0 .. +1.0
    val pitchRange: PitchRange = PitchRange.EIGHT,
    val keyLock: Boolean = true,
    val slipMode: Boolean = false,
    val slipPositionSeconds: Double = 0.0,
    val vinylMode: Boolean = true,
    val reverse: Boolean = false,
    val quantize: Boolean = true,
    val musicalKey: String = "11B / A",
    val camelotKey: String = "11B",
    val phaseOffset: Float = 0.0f, // -0.5 .. +0.5 beats
    val beatNumber: Int = 1,
    val cuePositionSeconds: Double = 0.0,
    val hotCues: List<HotCue> = List(8) { index ->
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
    },
    val loopState: LoopState = LoopState(),
    val jogAngleDegrees: Float = 0f,
    val isScratching: Boolean = false,
    val isPitchBending: Boolean = false,
    val pitchBendRate: Float = 0f,
    val overviewWaveformPeaks: FloatArray? = null,
    val detailWaveformPeaks: FloatArray? = null,
    val detailWaveformColors: IntArray? = null
)
