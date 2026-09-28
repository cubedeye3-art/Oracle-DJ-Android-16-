package com.example.data

import android.content.Context
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class TrackRepository(
    private val context: Context,
    private val trackDao: TrackDao,
    private val projectDao: ProjectDao
) {
    val allTracks: Flow<List<TrackEntity>> = trackDao.getAllTracks()
    val allProjects: Flow<List<ProjectEntity>> = projectDao.getAllProjects()

    suspend fun initializeBuiltInTracks() = withContext(Dispatchers.IO) {
        val techno = TrackEntity(
            title = "Oracle Cyber Techno",
            artist = "Oracle DJ Studio",
            durationSeconds = 60.0,
            bpm = 128.0,
            musicalKey = "8A / Am",
            filePath = "asset://deck_a_techno.wav",
            sampleRate = 48000,
            bitDepth = 24,
            channels = 2,
            formatName = "WAV 24-bit 48kHz",
            isFavorite = true
        )
        val house = TrackEntity(
            title = "Neon Horizon House",
            artist = "Oracle DJ Studio",
            durationSeconds = 62.0,
            bpm = 124.0,
            musicalKey = "7B / F",
            filePath = "asset://deck_b_house.wav",
            sampleRate = 48000,
            bitDepth = 24,
            channels = 2,
            formatName = "WAV 24-bit 48kHz",
            isFavorite = true
        )
        trackDao.insertTrack(techno)
        trackDao.insertTrack(house)
    }

    suspend fun scanDeviceMedia() = withContext(Dispatchers.IO) {
        try {
            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.DURATION
            )
            val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
            val cursor = context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                "${MediaStore.Audio.Media.TITLE} ASC"
            )

            cursor?.use {
                val idCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val dataCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val durCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

                val discovered = mutableListOf<TrackEntity>()
                while (it.moveToNext()) {
                    val id = it.getLong(idCol)
                    val title = it.getString(titleCol) ?: "Track $id"
                    val artist = it.getString(artistCol) ?: "Unknown Artist"
                    val path = it.getString(dataCol) ?: ""
                    val durationMs = it.getLong(durCol)
                    if (path.isNotEmpty() && durationMs > 1000) {
                        discovered.add(
                            TrackEntity(
                                title = title,
                                artist = artist,
                                durationSeconds = durationMs / 1000.0,
                                bpm = 126.0,
                                musicalKey = "8A",
                                filePath = path,
                                sampleRate = 44100,
                                bitDepth = 16,
                                channels = 2,
                                formatName = path.substringAfterLast(".").uppercase()
                            )
                        )
                    }
                }
                if (discovered.isNotEmpty()) {
                    trackDao.insertTracks(discovered)
                }
            }
        } catch (e: Exception) {
            // Permission or security exception
        }
    }

    fun searchTracks(query: String): Flow<List<TrackEntity>> = trackDao.searchTracks(query)

    suspend fun saveTrack(track: TrackEntity): Long = trackDao.insertTrack(track)

    suspend fun saveProject(project: ProjectEntity): Long = projectDao.insertProject(project)

    suspend fun deleteProject(id: Long) = projectDao.deleteProject(id)
}
