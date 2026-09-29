package com.example.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tracks",
    indices = [
        Index(value = ["filePath"], unique = true),
        Index(value = ["title"]),
        Index(value = ["artist"]),
        Index(value = ["bpm"]),
        Index(value = ["musicalKey"]),
        Index(value = ["analysisVersion"])
    ]
)
data class TrackEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val artist: String = "Unknown",
    val album: String = "",
    val durationSeconds: Double = 0.0,
    val bpm: Double = 0.0, // 0.0 represents unknown / failed / unanalyzed
    val bpmConfidence: Float = 0.0f,
    val musicalKey: String = "",
    val keyConfidence: Float = 0.0f,
    val beatInterval: Double = 0.0,
    val firstBeatOffset: Double = 0.0,
    val analysisVersion: Int = 0,
    val analysisTimestamp: Long = 0L,
    val fileModifiedTimestamp: Long = 0L,
    val filePath: String,
    val sampleRate: Int = 48000,
    val bitDepth: Int = 24,
    val channels: Int = 2,
    val formatName: String = "WAV",
    val isFavorite: Boolean = false,
    val lastPlayedTimestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val updatedAt: Long = System.currentTimeMillis(),
    val deckATrackPath: String = "",
    val deckBTrackPath: String = "",
    val deckAPosSec: Double = 0.0,
    val deckBPosSec: Double = 0.0,
    val deckAPitch: Float = 0.0f,
    val deckBPitch: Float = 0.0f,
    val crossfaderPos: Float = 0.5f,
    val masterGain: Float = 1.0f,
    val masteringPreset: String = "DJ_MASTER"
)
