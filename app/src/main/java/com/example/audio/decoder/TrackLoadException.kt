package com.example.audio.decoder

enum class TrackLoadErrorCode(val userMessage: String) {
    PERMISSION_DENIED("Audio storage permission is required to read music files."),
    FILE_NOT_FOUND("Audio file or URI could not be found or opened."),
    UNSUPPORTED_FORMAT("The audio file format is not supported."),
    EXTRACTOR_FAILURE("Unable to extract audio track from container."),
    CODEC_INIT_FAILURE("Failed to initialize audio decoder."),
    CODEC_DECODE_FAILURE("Audio decoder failed during decoding."),
    INVALID_PCM_FORMAT("Decoded audio contains an invalid or unsupported PCM format."),
    EMPTY_AUDIO("No audio samples were decoded from this track."),
    OUT_OF_MEMORY("Audio file is too large to decode into device memory."),
    DECK_LOAD_FAILURE("Failed to load decoded audio buffer into DJ deck."),
    PLAYBACK_FAILURE("Playback engine failed to start.")
}

class TrackLoadException(
    val code: TrackLoadErrorCode,
    message: String,
    cause: Throwable? = null
) : Exception("[$code] $message", cause)

data class TrackLoadError(
    val code: TrackLoadErrorCode,
    val technicalMessage: String,
    val uriString: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
