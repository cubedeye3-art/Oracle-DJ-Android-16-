package com.example.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.ContextCompat
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
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        val isGranted = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        if (!isGranted) {
            Log.w("TrackRepository", "scanDeviceMedia skipped: permission $permission is not granted")
            return@withContext
        }

        try {
            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.MIME_TYPE,
                MediaStore.Audio.Media.SIZE
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
                val dataCol = it.getColumnIndex(MediaStore.Audio.Media.DATA)
                val durCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val mimeCol = it.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE)
                val sizeCol = it.getColumnIndex(MediaStore.Audio.Media.SIZE)

                val discovered = mutableListOf<TrackEntity>()
                while (it.moveToNext()) {
                    val id = it.getLong(idCol)
                    val title = it.getString(titleCol) ?: "Track $id"
                    val artist = it.getString(artistCol) ?: "Unknown Artist"
                    val durationMs = it.getLong(durCol)
                    val mime = if (mimeCol >= 0) it.getString(mimeCol) ?: "" else ""
                    val path = if (dataCol >= 0) it.getString(dataCol) ?: "" else ""
                    val size = if (sizeCol >= 0) it.getLong(sizeCol) else 0L

                    // Construct canonical MediaStore content URI
                    val contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)

                    val format = when {
                        mime.contains("mpeg", ignoreCase = true) || mime.contains("mp3", ignoreCase = true) || path.endsWith(".mp3", true) -> "MP3"
                        mime.contains("wav", ignoreCase = true) || path.endsWith(".wav", true) -> "WAV"
                        mime.contains("flac", ignoreCase = true) || path.endsWith(".flac", true) -> "FLAC"
                        mime.contains("aac", ignoreCase = true) || mime.contains("mp4", ignoreCase = true) || path.endsWith(".m4a", true) || path.endsWith(".aac", true) -> "AAC"
                        mime.contains("ogg", ignoreCase = true) || path.endsWith(".ogg", true) -> "OGG"
                        else -> "AUDIO"
                    }

                    if (durationMs > 500) {
                        discovered.add(
                            TrackEntity(
                                title = title,
                                artist = artist,
                                durationSeconds = durationMs / 1000.0,
                                bpm = 126.0,
                                musicalKey = "8A",
                                filePath = contentUri.toString(),
                                sampleRate = 44100,
                                bitDepth = 16,
                                channels = 2,
                                formatName = format
                            )
                        )
                    }
                }
                Log.d("TrackRepository", "scanDeviceMedia: discovered ${discovered.size} audio tracks in MediaStore")
                if (discovered.isNotEmpty()) {
                    trackDao.insertTracks(discovered)
                }
            }
        } catch (e: Exception) {
            Log.e("TrackRepository", "scanDeviceMedia encountered error: ${e.message}", e)
        }
    }

    fun searchTracks(query: String): Flow<List<TrackEntity>> = trackDao.searchTracks(query)

    suspend fun saveTrack(track: TrackEntity): Long = trackDao.insertTrack(track)

    suspend fun deleteTrack(id: Long) = trackDao.deleteTrackById(id)

    suspend fun saveProject(project: ProjectEntity): Long = projectDao.insertProject(project)

    suspend fun deleteProject(id: Long) = projectDao.deleteProject(id)
}
