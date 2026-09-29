package com.example.data

data class LibraryAnalysisProgress(
    val isRunning: Boolean = false,
    val totalTracks: Int = 0,
    val scannedCount: Int = 0,
    val analysedCount: Int = 0,
    val cachedCount: Int = 0,
    val failedCount: Int = 0,
    val currentTrackTitle: String = "",
    val currentBpm: Double = 0.0,
    val currentKey: String = "",
    val progressPercent: Float = 0.0f
)
