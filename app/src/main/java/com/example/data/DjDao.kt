package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {
    @Query("SELECT * FROM tracks ORDER BY lastPlayedTimestamp DESC")
    fun getAllTracks(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks ORDER BY title ASC")
    suspend fun getAllTracksList(): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE title LIKE '%' || :query || '%' OR artist LIKE '%' || :query || '%' ORDER BY title ASC")
    fun searchTracks(query: String): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE filePath = :path LIMIT 1")
    suspend fun getTrackByPath(path: String): TrackEntity?

    @Query("SELECT * FROM tracks WHERE id = :id LIMIT 1")
    suspend fun getTrackById(id: Long): TrackEntity?

    @Query("SELECT * FROM tracks WHERE analysisVersion < :targetVersion ORDER BY id ASC")
    suspend fun getStaleOrUnanalyzedTracks(targetVersion: Int): List<TrackEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrack(track: TrackEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTracksIgnoreDuplicates(tracks: List<TrackEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTracks(tracks: List<TrackEntity>)

    @Update
    suspend fun updateTrack(track: TrackEntity)

    @Query(
        """
        UPDATE tracks 
        SET bpm = :bpm, 
            bpmConfidence = :bpmConfidence, 
            musicalKey = :musicalKey, 
            keyConfidence = :keyConfidence, 
            beatInterval = :beatInterval, 
            firstBeatOffset = :firstBeatOffset, 
            analysisVersion = :analysisVersion, 
            analysisTimestamp = :analysisTimestamp
        WHERE id = :id
        """
    )
    suspend fun updateAnalysis(
        id: Long,
        bpm: Double,
        bpmConfidence: Float,
        musicalKey: String,
        keyConfidence: Float,
        beatInterval: Double,
        firstBeatOffset: Double,
        analysisVersion: Int,
        analysisTimestamp: Long
    )

    @Query("DELETE FROM tracks WHERE id = :id")
    suspend fun deleteTrackById(id: Long)

    @Query("DELETE FROM tracks WHERE filePath = :path")
    suspend fun deleteTrackByPath(path: String)
}

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY updatedAt DESC")
    fun getAllProjects(): Flow<List<ProjectEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: ProjectEntity): Long

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun deleteProject(id: Long)
}
