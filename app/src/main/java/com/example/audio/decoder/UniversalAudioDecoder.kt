package com.example.audio.decoder

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import com.example.audio.model.AudioBuffer
import com.example.audio.model.AudioMetadata
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.log10
import kotlin.math.sqrt

object UniversalAudioDecoder {

    fun decodeUri(context: Context, uri: Uri): WavDecoder.DecodeResult {
        val scheme = uri.scheme
        val isWav = uri.lastPathSegment?.lowercase()?.endsWith(".wav") == true

        if (isWav || scheme == "content") {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    // Peek first 4 bytes to check if RIFF/WAV
                    val headerBytes = ByteArray(12)
                    val readLen = stream.read(headerBytes)
                    if (readLen >= 12 && String(headerBytes, 0, 4) == "RIFF" && String(headerBytes, 8, 4) == "WAVE") {
                        context.contentResolver.openInputStream(uri)?.use { fullStream ->
                            return WavDecoder.decode(fullStream, uri.lastPathSegment ?: "track.wav", 0L, uri.toString())
                        }
                    }
                }
            } catch (e: Exception) {
                // Fallback to MediaCodec
            }
        }

        return decodeWithMediaCodec(context, uri)
    }

    fun decodeFile(file: File): WavDecoder.DecodeResult {
        if (file.extension.equals("wav", ignoreCase = true)) {
            try {
                return WavDecoder.decode(file)
            } catch (e: Exception) {
                // Fallback to MediaExtractor/MediaCodec
            }
        }
        return decodeWithMediaCodec(file.absolutePath)
    }

    private fun decodeWithMediaCodec(filePath: String): WavDecoder.DecodeResult {
        val extractor = MediaExtractor()
        extractor.setDataSource(filePath)
        return processExtractor(extractor, File(filePath).name, File(filePath).length(), filePath)
    }

    private fun decodeWithMediaCodec(context: Context, uri: Uri): WavDecoder.DecodeResult {
        val extractor = MediaExtractor()
        extractor.setDataSource(context, uri, null)
        val name = uri.lastPathSegment ?: "track.audio"
        return processExtractor(extractor, name, 0L, uri.toString())
    }

    private fun processExtractor(
        extractor: MediaExtractor,
        name: String,
        fileSize: Long,
        path: String
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
            throw IllegalArgumentException("No audio track found in file")
        }

        extractor.selectTrack(trackIndex)
        val mime = format.getString(MediaFormat.KEY_MIME) ?: "audio/raw"
        val sampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) format.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 44100
        val channels = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 2
        val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0L

        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(format, null, null, 0)
        codec.start()

        val info = MediaCodec.BufferInfo()
        val pcmChunks = mutableListOf<FloatArray>()
        var isEOS = false

        while (!isEOS) {
            val inIndex = codec.dequeueInputBuffer(10000)
            if (inIndex >= 0) {
                val inputBuffer = codec.getInputBuffer(inIndex)
                if (inputBuffer != null) {
                    val sampleSize = extractor.readSampleData(inputBuffer, 0)
                    if (sampleSize < 0) {
                        codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        isEOS = true
                    } else {
                        codec.queueInputBuffer(inIndex, 0, sampleSize, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }

            var outIndex = codec.dequeueOutputBuffer(info, 10000)
            while (outIndex >= 0) {
                val outBuffer = codec.getOutputBuffer(outIndex)
                if (outBuffer != null && info.size > 0) {
                    outBuffer.position(info.offset)
                    outBuffer.limit(info.offset + info.size)
                    val shortBuffer = outBuffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                    val floats = FloatArray(shortBuffer.remaining())
                    for (k in floats.indices) {
                        floats[k] = shortBuffer.get() / 32768.0f
                    }
                    pcmChunks.add(floats)
                }
                codec.releaseOutputBuffer(outIndex, false)
                if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    isEOS = true
                    break
                }
                outIndex = codec.dequeueOutputBuffer(info, 0)
            }
        }

        codec.stop()
        codec.release()
        extractor.release()

        val totalSamples = pcmChunks.sumOf { it.size }
        val totalFrames = if (channels > 0) totalSamples / channels else totalSamples
        val left = FloatArray(totalFrames)
        val right = FloatArray(totalFrames)

        var frameIdx = 0
        var maxAbs = 0f
        var sumSquares = 0.0

        for (chunk in pcmChunks) {
            var i = 0
            while (i < chunk.size && frameIdx < totalFrames) {
                val lVal = chunk[i++]
                val rVal = if (channels > 1 && i < chunk.size) chunk[i++] else lVal
                left[frameIdx] = lVal
                right[frameIdx] = rVal

                val a1 = kotlin.math.abs(lVal)
                val a2 = kotlin.math.abs(rVal)
                if (a1 > maxAbs) maxAbs = a1
                if (a2 > maxAbs) maxAbs = a2
                sumSquares += (lVal * lVal + rVal * rVal)
                frameIdx++
            }
        }

        val rms = if (totalFrames > 0) sqrt(sumSquares / (totalFrames * 2.0)).toFloat() else 0f
        val peakDb = if (maxAbs > 0.00001f) (20.0 * log10(maxAbs.toDouble())).toFloat() else -96f
        val rmsDb = if (rms > 0.00001f) (20.0 * log10(rms.toDouble())).toFloat() else -96f
        val durationSec = if (durationUs > 0) durationUs / 1_000_000.0 else (totalFrames.toDouble() / sampleRate)

        val formatDesc = when {
            mime.contains("flac") -> "FLAC Lossless"
            mime.contains("mp4a") || mime.contains("aac") -> "AAC Stereo"
            mime.contains("mpeg") || mime.contains("mp3") -> "MP3 320k"
            mime.contains("vorbis") || mime.contains("opus") -> "Opus/OGG"
            else -> "Audio Compressed"
        }

        val metadata = AudioMetadata(
            title = name.substringBeforeLast("."),
            artist = "Local Library",
            sampleRate = sampleRate,
            bitDepth = 16,
            channels = channels,
            durationSeconds = durationSec,
            fileSizeBytes = fileSize,
            peakDb = peakDb,
            rmsDb = rmsDb,
            formatName = formatDesc,
            filePath = path
        )

        val buffer = AudioBuffer(
            leftChannel = left,
            rightChannel = right,
            sampleRate = sampleRate,
            originalBitDepth = 16,
            originalSampleRate = sampleRate,
            channels = channels
        )

        return WavDecoder.DecodeResult(buffer, metadata)
    }
}
