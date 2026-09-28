package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val artist: String = "Unknown",
    val durationSeconds: Double = 0.0,
    val bpm: Double = 126.0,
    val musicalKey: String = "8A / Am",
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
