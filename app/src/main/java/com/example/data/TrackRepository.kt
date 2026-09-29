package com.example.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.example.audio.analysis.BpmDetector
import com.example.audio.analysis.KeyDetector
import com.example.audio.decoder.UniversalAudioDecoder
import com.example.util.DjLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class TrackRepository(
    private val context: Context,
    private val trackDao: TrackDao,
    private val projectDao: ProjectDao
) {
    private val TAG = "TrackRepository"
    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    val allTracks: Flow<List<TrackEntity>> = trackDao.getAllTracks()
    val allProjects: Flow<List<ProjectEntity>> = projectDao.getAllProjects()

    private val _analysisProgress = MutableStateFlow(LibraryAnalysisProgress())
    val analysisProgress: StateFlow<LibraryAnalysisProgress> = _analysisProgress.asStateFlow()

    private var activeAnalysisJob: Job? = null

    suspend fun initializeBuiltInTracks() = withContext(Dispatchers.IO) {
        val count = trackDao.getAllTracksList().size
        if (count == 0) {
            val techno = TrackEntity(
                title = "Oracle Cyber Techno",
                artist = "BeatEngine Pro Studio",
                album = "Studio Tracks",
                durationSeconds = 60.0,
                bpm = 128.0,
                bpmConfidence = 0.98f,
                musicalKey = "8A / Am",
                keyConfidence = 0.95f,
                beatInterval = 60.0 / 128.0,
                firstBeatOffset = 0.0,
                analysisVersion = BpmDetector.ANALYSIS_VERSION,
                analysisTimestamp = System.currentTimeMillis(),
                filePath = "asset://deck_a_techno.wav",
                sampleRate = 48000,
                bitDepth = 24,
                channels = 2,
                formatName = "WAV 24-bit 48kHz",
                isFavorite = true
            )
            val house = TrackEntity(
                title = "Neon Horizon House",
                artist = "BeatEngine Pro Studio",
                album = "Studio Tracks",
                durationSeconds = 62.0,
                bpm = 124.0,
                bpmConfidence = 0.98f,
                musicalKey = "7B / F",
                keyConfidence = 0.95f,
                beatInterval = 60.0 / 124.0,
                firstBeatOffset = 0.0,
                analysisVersion = BpmDetector.ANALYSIS_VERSION,
                analysisTimestamp = System.currentTimeMillis(),
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
    }

    suspend fun scanDeviceMedia(): Int = withContext(Dispatchers.IO) {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        val isGranted = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        if (!isGranted) {
            DjLogger.w(TAG, "scanDeviceMedia skipped: permission $permission is not granted")
            return@withContext 0
        }

        var discoveredCount = 0
        try {
            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.MIME_TYPE,
                MediaStore.Audio.Media.SIZE,
                MediaStore.Audio.Media.DATE_MODIFIED
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
                val albumCol = it.getColumnIndex(MediaStore.Audio.Media.ALBUM)
                val dataCol = it.getColumnIndex(MediaStore.Audio.Media.DATA)
                val durCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val mimeCol = it.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE)
                val sizeCol = it.getColumnIndex(MediaStore.Audio.Media.SIZE)
                val modifiedCol = it.getColumnIndex(MediaStore.Audio.Media.DATE_MODIFIED)

                while (it.moveToNext()) {
                    val id = it.getLong(idCol)
                    val title = it.getString(titleCol) ?: "Track $id"
                    val artist = it.getString(artistCol) ?: "Unknown Artist"
                    val album = if (albumCol >= 0) it.getString(albumCol) ?: "" else ""
                    val durationMs = it.getLong(durCol)
                    val mime = if (mimeCol >= 0) it.getString(mimeCol) ?: "" else ""
                    val path = if (dataCol >= 0) it.getString(dataCol) ?: "" else ""
                    val size = if (sizeCol >= 0) it.getLong(sizeCol) else 0L
                    val modifiedSec = if (modifiedCol >= 0) it.getLong(modifiedCol) else 0L

                    val contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                    val uriString = contentUri.toString()

                    val format = when {
                        mime.contains("mpeg", ignoreCase = true) || mime.contains("mp3", ignoreCase = true) || path.endsWith(".mp3", true) -> "MP3"
                        mime.contains("wav", ignoreCase = true) || path.endsWith(".wav", true) -> "WAV"
                        mime.contains("flac", ignoreCase = true) || path.endsWith(".flac", true) -> "FLAC"
                        mime.contains("aac", ignoreCase = true) || mime.contains("mp4", ignoreCase = true) || path.endsWith(".m4a", true) || path.endsWith(".aac", true) -> "AAC"
                        mime.contains("ogg", ignoreCase = true) || path.endsWith(".ogg", true) -> "OGG"
                        else -> "AUDIO"
                    }

                    if (durationMs > 500) {
                        // Check if track already exists in database
                        val existing = trackDao.getTrackByPath(uriString)
                        if (existing == null) {
                            val newTrack = TrackEntity(
                                title = title,
                                artist = artist,
                                album = album,
                                durationSeconds = durationMs / 1000.0,
                                bpm = 0.0, // Unanalyzed
                                bpmConfidence = 0.0f,
                                musicalKey = "",
                                keyConfidence = 0.0f,
                                beatInterval = 0.0,
                                firstBeatOffset = 0.0,
                                analysisVersion = 0,
                                analysisTimestamp = 0L,
                                fileModifiedTimestamp = modifiedSec,
                                filePath = uriString,
                                sampleRate = 44100,
                                bitDepth = 16,
                                channels = 2,
                                formatName = format
                            )
                            trackDao.insertTrack(newTrack)
                            discoveredCount++
                        } else if (existing.fileModifiedTimestamp != modifiedSec && modifiedSec > 0) {
                            // File was modified: mark stale for reanalysis
                            trackDao.updateTrack(
                                existing.copy(
                                    title = title,
                                    artist = artist,
                                    album = album,
                                    durationSeconds = durationMs / 1000.0,
                                    fileModifiedTimestamp = modifiedSec,
                                    analysisVersion = 0
                                )
                            )
                        }
                    }
                }
                DjLogger.d(TAG, "scanDeviceMedia: discovered and synced $discoveredCount new tracks from MediaStore")
            }
        } catch (e: Exception) {
            DjLogger.e(TAG, "scanDeviceMedia encountered error: ${e.message}", e)
        }
        return@withContext discoveredCount
    }

    /**
     * Dedicated background job: SCAN & ANALYSE LIBRARY.
     * Survives UI navigation, processes without freezing Compose, updates Room cache.
     */
    fun startScanAndAnalyseLibrary() {
        if (activeAnalysisJob?.isActive == true) {
            DjLogger.d(TAG, "startScanAndAnalyseLibrary already running, skipping restart.")
            return
        }

        activeAnalysisJob = repositoryScope.launch {
            try {
                _analysisProgress.value = LibraryAnalysisProgress(
                    isRunning = true,
                    currentTrackTitle = "Scanning MediaStore..."
                )

                // 1. Initial MediaStore sync
                scanDeviceMedia()

                // 2. Fetch all tracks for analysis audit
                val allDbTracks = trackDao.getAllTracksList()
                val totalCount = allDbTracks.size
                var scanned = 0
                var analysed = 0
                var cached = 0
                var failed = 0

                _analysisProgress.value = _analysisProgress.value.copy(
                    totalTracks = totalCount,
                    isRunning = true
                )

                for (track in allDbTracks) {
                    scanned++
                    val pct = if (totalCount > 0) scanned.toFloat() / totalCount else 1.0f

                    // If already analyzed with current algorithm version and has valid analysis:
                    if (track.analysisVersion >= BpmDetector.ANALYSIS_VERSION && track.bpm > 0.0) {
                        cached++
                        _analysisProgress.value = LibraryAnalysisProgress(
                            isRunning = true,
                            totalTracks = totalCount,
                            scannedCount = scanned,
                            analysedCount = analysed,
                            cachedCount = cached,
                            failedCount = failed,
                            currentTrackTitle = track.title,
                            currentBpm = track.bpm,
                            currentKey = track.musicalKey,
                            progressPercent = pct
                        )
                        continue
                    }

                    // Otherwise, perform background analysis
                    _analysisProgress.value = LibraryAnalysisProgress(
                        isRunning = true,
                        totalTracks = totalCount,
                        scannedCount = scanned,
                        analysedCount = analysed,
                        cachedCount = cached,
                        failedCount = failed,
                        currentTrackTitle = "Analysing ${track.title}...",
                        currentBpm = 0.0,
                        currentKey = "",
                        progressPercent = pct
                    )

                    try {
                        val decoded = if (track.filePath.startsWith("content://")) {
                            UniversalAudioDecoder.decodeUri(context, Uri.parse(track.filePath))
                        } else if (track.filePath.startsWith("asset://")) {
                            null // built-in already analyzed
                        } else {
                            val f = File(track.filePath)
                            if (f.exists()) UniversalAudioDecoder.decodeFile(f) else null
                        }

                        if (decoded != null) {
                            val bpmRes = BpmDetector.detect(decoded.buffer)
                            val keyRes = KeyDetector.detect(decoded.buffer)

                            val keyString = if (keyRes.keyName.isNotBlank()) {
                                "${keyRes.camelotCode} / ${keyRes.keyName}"
                            } else {
                                ""
                            }

                            trackDao.updateAnalysis(
                                id = track.id,
                                bpm = bpmRes.bpm,
                                bpmConfidence = bpmRes.confidence,
                                musicalKey = keyString,
                                keyConfidence = keyRes.confidence,
                                beatInterval = bpmRes.beatIntervalSeconds,
                                firstBeatOffset = bpmRes.firstBeatOffsetSeconds,
                                analysisVersion = BpmDetector.ANALYSIS_VERSION,
                                analysisTimestamp = System.currentTimeMillis()
                            )

                            analysed++
                            _analysisProgress.value = LibraryAnalysisProgress(
                                isRunning = true,
                                totalTracks = totalCount,
                                scannedCount = scanned,
                                analysedCount = analysed,
                                cachedCount = cached,
                                failedCount = failed,
                                currentTrackTitle = track.title,
                                currentBpm = bpmRes.bpm,
                                currentKey = keyString,
                                progressPercent = pct
                            )
                        } else {
                            if (!track.filePath.startsWith("asset://")) {
                                failed++
                            }
                        }
                    } catch (e: Exception) {
                        DjLogger.e(TAG, "Analysis failed for track ID=${track.id} (${track.title}): ${e.message}", e)
                        failed++
                        // Record failure explicitly in DB so we don't spin indefinitely
                        trackDao.updateAnalysis(
                            id = track.id,
                            bpm = 0.0,
                            bpmConfidence = 0.0f,
                            musicalKey = "",
                            keyConfidence = 0.0f,
                            beatInterval = 0.0,
                            firstBeatOffset = 0.0,
                            analysisVersion = BpmDetector.ANALYSIS_VERSION,
                            analysisTimestamp = System.currentTimeMillis()
                        )
                    }
                }

                _analysisProgress.value = LibraryAnalysisProgress(
                    isRunning = false,
                    totalTracks = totalCount,
                    scannedCount = scanned,
                    analysedCount = analysed,
                    cachedCount = cached,
                    failedCount = failed,
                    currentTrackTitle = "Analysis Complete ($analysed analysed, $cached cached, $failed failed)",
                    progressPercent = 1.0f
                )
            } catch (e: Exception) {
                DjLogger.e(TAG, "Library analysis aborted: ${e.message}", e)
                _analysisProgress.value = _analysisProgress.value.copy(
                    isRunning = false,
                    currentTrackTitle = "Analysis stopped: ${e.message}"
                )
            }
        }
    }

    fun searchTracks(query: String): Flow<List<TrackEntity>> = trackDao.searchTracks(query)

    suspend fun saveTrack(track: TrackEntity): Long = trackDao.insertTrack(track)

    suspend fun deleteTrack(id: Long) = trackDao.deleteTrackById(id)

    suspend fun saveProject(project: ProjectEntity): Long = projectDao.insertProject(project)

    suspend fun deleteProject(id: Long) = projectDao.deleteProject(id)
}
