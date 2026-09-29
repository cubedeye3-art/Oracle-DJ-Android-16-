package com.example.audio.decoder

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.example.audio.model.AudioBuffer
import com.example.audio.model.AudioMetadata
import com.example.util.DjLogger
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt

object UniversalAudioDecoder {

    private const val TAG_LOAD = "BEATENGINE_LOAD"
    private const val TAG_DECODER = "UniversalAudioDecoder"

    data class ProbedMediaInfo(
        val title: String,
        val artist: String,
        val mimeType: String,
        val fileSize: Long,
        val durationUs: Long = 0L
    )

    fun decodeUri(context: Context, uri: Uri): WavDecoder.DecodeResult {
        DjLogger.d(TAG_LOAD, "decodeUri called with URI: $uri")
        val scheme = uri.scheme
        val uriStr = uri.toString()

        // 1. Probe metadata from ContentResolver or file
        val probed = probeMetadata(context, uri)
        DjLogger.d(
            TAG_LOAD,
            "BEATENGINE_LOAD:\nURI=$uri\nTITLE=${probed.title}\nARTIST=${probed.artist}\nMIME=${probed.mimeType}\nSIZE=${probed.fileSize}"
        )

        // 2. Check if WAV / RIFF audio
        val isWavCandidate = uri.lastPathSegment?.lowercase()?.endsWith(".wav") == true ||
                probed.mimeType.contains("wav", ignoreCase = true) ||
                scheme == "content" || scheme == "file"

        if (isWavCandidate) {
            try {
                val isRiff = context.contentResolver.openInputStream(uri)?.use { stream ->
                    val headerBytes = ByteArray(12)
                    val readLen = stream.read(headerBytes)
                    readLen >= 12 && String(headerBytes, 0, 4) == "RIFF" && String(headerBytes, 8, 4) == "WAVE"
                } ?: false

                if (isRiff) {
                    DjLogger.d(TAG_DECODER, "RIFF/WAVE header verified. Routing to WavDecoder.")
                    context.contentResolver.openInputStream(uri)?.use { fullStream ->
                        return WavDecoder.decode(
                            inputStream = fullStream,
                            filename = if (probed.title.endsWith(".wav", true)) probed.title else "${probed.title}.wav",
                            fileSizeBytes = probed.fileSize,
                            filePath = uriStr
                        )
                    }
                }
            } catch (e: TrackLoadException) {
                if (e.code == TrackLoadErrorCode.OUT_OF_MEMORY) throw e
                DjLogger.w(TAG_DECODER, "WavDecoder error: ${e.message}. Attempting MediaCodec fallback...")
            } catch (e: Exception) {
                DjLogger.w(TAG_DECODER, "WavDecoder probe or decode failed, falling back to MediaCodec: ${e.message}")
            }
        }

        // 3. Decode via MediaExtractor and MediaCodec
        return decodeWithMediaCodec(context, uri, probed)
    }

    fun decodeFile(file: File): WavDecoder.DecodeResult {
        DjLogger.d(TAG_LOAD, "decodeFile called with file: ${file.absolutePath} (length: ${file.length()} bytes)")
        if (!file.exists()) {
            throw TrackLoadException(TrackLoadErrorCode.FILE_NOT_FOUND, "File does not exist: ${file.absolutePath}")
        }

        val name = file.name
        val isWav = file.extension.equals("wav", ignoreCase = true)

        if (isWav) {
            try {
                return WavDecoder.decode(file)
            } catch (e: TrackLoadException) {
                if (e.code == TrackLoadErrorCode.OUT_OF_MEMORY) throw e
                DjLogger.w(TAG_DECODER, "WavDecoder failed on file, fallback to MediaCodec: ${e.message}")
            } catch (e: Exception) {
                DjLogger.w(TAG_DECODER, "WavDecoder failed on file, fallback to MediaCodec: ${e.message}")
            }
        }

        val probed = ProbedMediaInfo(
            title = name.substringBeforeLast("."),
            artist = "Local Library",
            mimeType = guessMimeTypeFromExtension(file.extension),
            fileSize = file.length()
        )

        DjLogger.d(
            TAG_LOAD,
            "BEATENGINE_LOAD:\nURI=${file.toURI()}\nTITLE=${probed.title}\nARTIST=${probed.artist}\nMIME=${probed.mimeType}\nSIZE=${probed.fileSize}"
        )

        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
        } catch (e: Exception) {
            extractor.release()
            throw TrackLoadException(
                TrackLoadErrorCode.EXTRACTOR_FAILURE,
                "MediaExtractor failed to open file ${file.absolutePath}: ${e.message}",
                e
            )
        }

        return processExtractor(extractor, probed, file.absolutePath)
    }

    private fun decodeWithMediaCodec(
        context: Context,
        uri: Uri,
        probed: ProbedMediaInfo
    ): WavDecoder.DecodeResult {
        val extractor = MediaExtractor()
        var afd: android.content.res.AssetFileDescriptor? = null

        try {
            try {
                extractor.setDataSource(context, uri, null)
            } catch (e: Exception) {
                DjLogger.w(TAG_DECODER, "Direct extractor.setDataSource(context, uri, null) failed: ${e.message}. Trying openAssetFileDescriptor...")
                afd = context.contentResolver.openAssetFileDescriptor(uri, "r")
                if (afd != null) {
                    if (afd.declaredLength < 0) {
                        extractor.setDataSource(afd.fileDescriptor)
                    } else {
                        extractor.setDataSource(afd.fileDescriptor, afd.startOffset, afd.declaredLength)
                    }
                } else {
                    throw e
                }
            }
        } catch (e: SecurityException) {
            extractor.release()
            afd?.close()
            throw TrackLoadException(
                TrackLoadErrorCode.PERMISSION_DENIED,
                "Security exception opening URI $uri: ${e.message}",
                e
            )
        } catch (e: IOException) {
            extractor.release()
            afd?.close()
            throw TrackLoadException(
                TrackLoadErrorCode.FILE_NOT_FOUND,
                "I/O error opening URI $uri: ${e.message}",
                e
            )
        } catch (e: Exception) {
            extractor.release()
            afd?.close()
            throw TrackLoadException(
                TrackLoadErrorCode.EXTRACTOR_FAILURE,
                "Failed to configure MediaExtractor for $uri: ${e.message}",
                e
            )
        }

        try {
            return processExtractor(extractor, probed, uri.toString())
        } finally {
            try {
                afd?.close()
            } catch (_: Exception) {}
        }
    }

    private fun processExtractor(
        extractor: MediaExtractor,
        probed: ProbedMediaInfo,
        sourceLocation: String
    ): WavDecoder.DecodeResult {
        var trackIndex = -1
        var format: MediaFormat? = null

        for (i in 0 until extractor.trackCount) {
            val f = extractor.getTrackFormat(i)
            val mime = f.getString(MediaFormat.KEY_MIME) ?: ""
            if (mime.startsWith("audio/")) {
                trackIndex = i
                format = f
                break
            }
        }

        if (trackIndex < 0 || format == null) {
            extractor.release()
            throw TrackLoadException(
                TrackLoadErrorCode.EXTRACTOR_FAILURE,
                "No audio track found in media container ($sourceLocation)"
            )
        }

        extractor.selectTrack(trackIndex)

        val mime = format.getString(MediaFormat.KEY_MIME) ?: "audio/raw"
        val initialSampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
            format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        } else {
            44100
        }
        val initialChannels = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
            format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        } else {
            2
        }
        val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) {
            format.getLong(MediaFormat.KEY_DURATION)
        } else {
            probed.durationUs
        }

        DjLogger.d(
            TAG_DECODER,
            "UniversalAudioDecoder:\nsource format=$format\ncodec=$mime\nsample rate=$initialSampleRate\nchannels=$initialChannels\nPCM encoding=UNKNOWN (pending output format)"
        )

        val codec: MediaCodec
        try {
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()
        } catch (e: Exception) {
            extractor.release()
            throw TrackLoadException(
                TrackLoadErrorCode.CODEC_INIT_FAILURE,
                "Failed to initialize MediaCodec for MIME $mime: ${e.message}",
                e
            )
        }

        DjLogger.d(TAG_DECODER, "decode start")

        val info = MediaCodec.BufferInfo()
        val pcmChunks = mutableListOf<FloatArray>()
        var inputEOS = false
        var outputEOS = false

        var decodedSampleRate = initialSampleRate
        var decodedChannels = initialChannels
        var pcmEncoding = AudioFormat.ENCODING_PCM_16BIT
        var consecutiveNoOutput = 0

        try {
            while (!outputEOS) {
                if (!inputEOS) {
                    val inIndex = codec.dequeueInputBuffer(10000L)
                    if (inIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inIndex)
                        if (inputBuffer != null) {
                            inputBuffer.clear()
                            val sampleSize = extractor.readSampleData(inputBuffer, 0)
                            if (sampleSize < 0) {
                                DjLogger.d(TAG_DECODER, "MediaExtractor reached input EOS, queuing BUFFER_FLAG_END_OF_STREAM")
                                codec.queueInputBuffer(inIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputEOS = true
                            } else {
                                codec.queueInputBuffer(inIndex, 0, sampleSize, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                }

                val outIndex = codec.dequeueOutputBuffer(info, 10000L)
                when (outIndex) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val newFormat = codec.outputFormat
                        DjLogger.d(TAG_DECODER, "output format changed: $newFormat")
                        if (newFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                            decodedSampleRate = newFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        }
                        if (newFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                            decodedChannels = newFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        }
                        if (newFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                            pcmEncoding = newFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
                        }
                        DjLogger.d(
                            TAG_DECODER,
                            "UniversalAudioDecoder updated format: sampleRate=$decodedSampleRate, channels=$decodedChannels, pcmEncoding=$pcmEncoding"
                        )
                        consecutiveNoOutput = 0
                    }
                    MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        consecutiveNoOutput++
                        if (inputEOS && consecutiveNoOutput > 60) {
                            DjLogger.w(TAG_DECODER, "Decoder drain timed out after input EOS. Ending decode loop.")
                            outputEOS = true
                        }
                    }
                    MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> {
                        consecutiveNoOutput = 0
                    }
                    else -> {
                        if (outIndex >= 0) {
                            consecutiveNoOutput = 0
                            val outBuffer = codec.getOutputBuffer(outIndex)
                            if (outBuffer != null && info.size > 0) {
                                outBuffer.position(info.offset)
                                outBuffer.limit(info.offset + info.size)

                                val floats = decodePcmBufferToFloats(outBuffer, pcmEncoding, info.size)
                                if (floats.isNotEmpty()) {
                                    pcmChunks.add(floats)
                                }
                            }
                            codec.releaseOutputBuffer(outIndex, false)

                            if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                DjLogger.d(TAG_DECODER, "Output BUFFER_FLAG_END_OF_STREAM received. Decoding complete.")
                                outputEOS = true
                            }
                        }
                    }
                }
            }
        } catch (e: OutOfMemoryError) {
            throw TrackLoadException(
                TrackLoadErrorCode.OUT_OF_MEMORY,
                "Out of memory during MediaCodec output buffering for $sourceLocation: ${e.message}"
            )
        } catch (e: Exception) {
            throw TrackLoadException(
                TrackLoadErrorCode.CODEC_DECODE_FAILURE,
                "MediaCodec decode failure: ${e.message}",
                e
            )
        } finally {
            try { codec.stop() } catch (_: Exception) {}
            try { codec.release() } catch (_: Exception) {}
            try { extractor.release() } catch (_: Exception) {}
        }

        val totalSamples = pcmChunks.sumOf { it.size.toLong() }
        DjLogger.d(TAG_DECODER, "decoded frames/sample count: totalSamples=$totalSamples, channels=$decodedChannels")

        if (totalSamples == 0L) {
            throw TrackLoadException(
                TrackLoadErrorCode.EMPTY_AUDIO,
                "Decoded audio produced 0 samples ($sourceLocation)"
            )
        }

        val totalFramesLong = if (decodedChannels > 0) totalSamples / decodedChannels else totalSamples
        if (totalFramesLong > Int.MAX_VALUE) {
            throw TrackLoadException(
                TrackLoadErrorCode.OUT_OF_MEMORY,
                "Audio track frame count exceeds maximum array capacity ($totalFramesLong frames)"
            )
        }
        val totalFrames = totalFramesLong.toInt()

        val left: FloatArray
        val right: FloatArray
        try {
            left = FloatArray(totalFrames)
            right = FloatArray(totalFrames)
        } catch (e: OutOfMemoryError) {
            throw TrackLoadException(
                TrackLoadErrorCode.OUT_OF_MEMORY,
                "Out of memory allocating $totalFrames frames for $sourceLocation: ${e.message}"
            )
        }

        var frameIdx = 0
        var maxAbs = 0f
        var sumSquares = 0.0

        for (chunk in pcmChunks) {
            var i = 0
            while (i < chunk.size && frameIdx < totalFrames) {
                val lVal = chunk[i++]
                val rVal = if (decodedChannels > 1 && i < chunk.size) chunk[i++] else lVal
                left[frameIdx] = lVal
                right[frameIdx] = rVal

                val a1 = abs(lVal)
                val a2 = abs(rVal)
                if (a1 > maxAbs) maxAbs = a1
                if (a2 > maxAbs) maxAbs = a2
                sumSquares += (lVal * lVal + rVal * rVal)
                frameIdx++
            }
        }

        val rms = if (totalFrames > 0) sqrt(sumSquares / (totalFrames * 2.0)).toFloat() else 0f
        val peakDb = if (maxAbs > 0.00001f) (20.0 * log10(maxAbs.toDouble())).toFloat() else -96f
        val rmsDb = if (rms > 0.00001f) (20.0 * log10(rms.toDouble())).toFloat() else -96f
        val durationSec = if (durationUs > 0) {
            durationUs / 1_000_000.0
        } else if (decodedSampleRate > 0) {
            totalFrames.toDouble() / decodedSampleRate
        } else {
            0.0
        }

        val formatDesc = when {
            mime.contains("flac") -> "FLAC Lossless"
            mime.contains("mp4a") || mime.contains("aac") -> "AAC Stereo"
            mime.contains("mpeg") || mime.contains("mp3") -> "MP3 Audio"
            mime.contains("vorbis") || mime.contains("opus") -> "Opus/OGG"
            else -> "Audio Decoded"
        }

        val bitDepth = when (pcmEncoding) {
            AudioFormat.ENCODING_PCM_FLOAT -> 32
            AudioFormat.ENCODING_PCM_32BIT -> 32
            AudioFormat.ENCODING_PCM_24BIT_PACKED -> 24
            AudioFormat.ENCODING_PCM_8BIT -> 8
            else -> 16
        }

        DjLogger.d(
            TAG_DECODER,
            "decode complete: sampleRate=$decodedSampleRate, channels=$decodedChannels, bitDepth=$bitDepth, duration=${durationSec}s"
        )

        val metadata = AudioMetadata(
            title = probed.title,
            artist = probed.artist,
            sampleRate = decodedSampleRate,
            bitDepth = bitDepth,
            channels = decodedChannels,
            durationSeconds = durationSec,
            fileSizeBytes = probed.fileSize,
            peakDb = peakDb,
            rmsDb = rmsDb,
            formatName = formatDesc,
            filePath = sourceLocation
        )

        val buffer = AudioBuffer(
            leftChannel = left,
            rightChannel = right,
            sampleRate = decodedSampleRate,
            originalBitDepth = bitDepth,
            originalSampleRate = decodedSampleRate,
            channels = decodedChannels
        )

        DjLogger.d(TAG_DECODER, "AudioBuffer created: $buffer")
        return WavDecoder.DecodeResult(buffer, metadata)
    }

    private fun decodePcmBufferToFloats(
        buffer: ByteBuffer,
        pcmEncoding: Int,
        sizeBytes: Int
    ): FloatArray {
        val le = buffer.order(ByteOrder.LITTLE_ENDIAN)
        return when (pcmEncoding) {
            AudioFormat.ENCODING_PCM_FLOAT -> {
                val fb = le.asFloatBuffer()
                val remaining = fb.remaining()
                val floats = FloatArray(remaining)
                fb.get(floats)
                floats
            }
            AudioFormat.ENCODING_PCM_16BIT -> {
                val sb = le.asShortBuffer()
                val remaining = sb.remaining()
                val floats = FloatArray(remaining)
                for (i in 0 until remaining) {
                    floats[i] = sb.get() / 32768.0f
                }
                floats
            }
            AudioFormat.ENCODING_PCM_8BIT -> {
                val remaining = sizeBytes
                val floats = FloatArray(remaining)
                for (i in 0 until remaining) {
                    val b = le.get().toInt() and 0xFF
                    floats[i] = (b - 128) / 128.0f
                }
                floats
            }
            AudioFormat.ENCODING_PCM_24BIT_PACKED -> {
                val remainingFrames = sizeBytes / 3
                val floats = FloatArray(remainingFrames)
                for (i in 0 until remainingFrames) {
                    val b0 = le.get().toInt() and 0xFF
                    val b1 = le.get().toInt() and 0xFF
                    val b2 = le.get().toInt() and 0xFF
                    val raw = (b2 shl 16) or (b1 shl 8) or b0
                    val s24 = if ((raw and 0x800000) != 0) (raw or -0x1000000) else raw
                    floats[i] = s24 / 8388608.0f
                }
                floats
            }
            AudioFormat.ENCODING_PCM_32BIT -> {
                val ib = le.asIntBuffer()
                val remaining = ib.remaining()
                val floats = FloatArray(remaining)
                for (i in 0 until remaining) {
                    floats[i] = (ib.get().toDouble() / 2147483648.0).toFloat()
                }
                floats
            }
            else -> {
                val sb = le.asShortBuffer()
                val remaining = sb.remaining()
                val floats = FloatArray(remaining)
                for (i in 0 until remaining) {
                    floats[i] = sb.get() / 32768.0f
                }
                floats
            }
        }
    }

    private fun probeMetadata(context: Context, uri: Uri): ProbedMediaInfo {
        var title = uri.lastPathSegment ?: "Track"
        var artist = "Local Library"
        var mimeType = context.contentResolver.getType(uri) ?: ""
        var fileSize = 0L

        try {
            val cursor = context.contentResolver.query(
                uri,
                arrayOf(
                    OpenableColumns.DISPLAY_NAME,
                    OpenableColumns.SIZE,
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ARTIST
                ),
                null,
                null,
                null
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIdx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = it.getColumnIndex(OpenableColumns.SIZE)
                    val titleIdx = it.getColumnIndex(MediaStore.Audio.Media.TITLE)
                    val artistIdx = it.getColumnIndex(MediaStore.Audio.Media.ARTIST)

                    if (nameIdx >= 0) {
                        it.getString(nameIdx)?.let { name ->
                            title = name.substringBeforeLast(".")
                        }
                    }
                    if (titleIdx >= 0) {
                        it.getString(titleIdx)?.let { t ->
                            if (t.isNotBlank()) title = t
                        }
                    }
                    if (artistIdx >= 0) {
                        it.getString(artistIdx)?.let { a ->
                            if (a.isNotBlank()) artist = a
                        }
                    }
                    if (sizeIdx >= 0) {
                        fileSize = it.getLong(sizeIdx)
                    }
                }
            }
        } catch (e: Exception) {
            DjLogger.w(TAG_DECODER, "Could not query ContentResolver for metadata: ${e.message}")
        }

        if (mimeType.isBlank()) {
            val seg = uri.lastPathSegment?.lowercase() ?: ""
            mimeType = guessMimeTypeFromExtension(seg.substringAfterLast(".", ""))
        }

        return ProbedMediaInfo(title, artist, mimeType, fileSize)
    }

    private fun guessMimeTypeFromExtension(extension: String): String {
        return when (extension.lowercase()) {
            "mp3" -> "audio/mpeg"
            "aac" -> "audio/aac"
            "m4a" -> "audio/mp4"
            "wav" -> "audio/x-wav"
            "flac" -> "audio/flac"
            "ogg" -> "audio/ogg"
            else -> "audio/raw"
        }
    }
}
