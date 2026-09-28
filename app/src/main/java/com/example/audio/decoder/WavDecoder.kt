package com.example.audio.decoder

import android.util.Log
import com.example.audio.model.AudioBuffer
import com.example.audio.model.AudioMetadata
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt

object WavDecoder {

    data class DecodeResult(
        val buffer: AudioBuffer,
        val metadata: AudioMetadata
    )

    fun decode(file: File): DecodeResult {
        Log.d("WavDecoder", "WavDecoder: decode called for file: ${file.absolutePath} (size: ${file.length()} bytes)")
        if (!file.exists()) {
            throw TrackLoadException(TrackLoadErrorCode.FILE_NOT_FOUND, "WAV file does not exist: ${file.absolutePath}")
        }
        return file.inputStream().use { stream ->
            decode(stream, file.name, file.length(), file.absolutePath)
        }
    }

    fun decode(
        inputStream: InputStream,
        filename: String = "audio.wav",
        fileSizeBytes: Long = 0L,
        filePath: String = ""
    ): DecodeResult {
        Log.d("WavDecoder", "WavDecoder: decode start for $filename ($filePath)")

        val bytes: ByteArray
        try {
            bytes = inputStream.readBytes()
        } catch (e: OutOfMemoryError) {
            throw TrackLoadException(
                TrackLoadErrorCode.OUT_OF_MEMORY,
                "Out of memory reading WAV data for $filename: ${e.message}"
            )
        } catch (e: Exception) {
            throw TrackLoadException(
                TrackLoadErrorCode.FILE_NOT_FOUND,
                "Error reading WAV stream for $filename: ${e.message}",
                e
            )
        }

        if (bytes.size < 44) {
            throw TrackLoadException(
                TrackLoadErrorCode.UNSUPPORTED_FORMAT,
                "File too small to be a valid WAV file (${bytes.size} bytes)"
            )
        }

        val byteBuffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        // Read RIFF header
        val riffHeader = String(bytes, 0, 4)
        if (riffHeader != "RIFF") {
            throw TrackLoadException(
                TrackLoadErrorCode.UNSUPPORTED_FORMAT,
                "Invalid RIFF header: $riffHeader"
            )
        }

        val waveHeader = String(bytes, 8, 4)
        if (waveHeader != "WAVE") {
            throw TrackLoadException(
                TrackLoadErrorCode.UNSUPPORTED_FORMAT,
                "Invalid WAVE identifier: $waveHeader"
            )
        }

        var offset = 12
        var audioFormat = 1 // 1 = PCM, 3 = IEEE Float, 65534 = Extensible
        var channels = 2
        var sampleRate = 44100
        var bitsPerSample = 16
        var dataOffset = -1
        var dataSize = 0

        while (offset + 8 <= bytes.size) {
            val chunkId = String(bytes, offset, 4)
            val chunkSize = byteBuffer.getInt(offset + 4)
            val chunkDataStart = offset + 8

            when (chunkId) {
                "fmt " -> {
                    if (chunkSize >= 16 && chunkDataStart + 16 <= bytes.size) {
                        audioFormat = byteBuffer.getShort(chunkDataStart).toInt() and 0xFFFF
                        channels = byteBuffer.getShort(chunkDataStart + 2).toInt() and 0xFFFF
                        sampleRate = byteBuffer.getInt(chunkDataStart + 4)
                        bitsPerSample = byteBuffer.getShort(chunkDataStart + 14).toInt() and 0xFFFF

                        if (audioFormat == 65534 && chunkSize >= 40 && chunkDataStart + 26 <= bytes.size) {
                            // WAVE_FORMAT_EXTENSIBLE: subFormat GUID first two bytes is format code
                            val subFormat = byteBuffer.getShort(chunkDataStart + 24).toInt() and 0xFFFF
                            audioFormat = subFormat
                        }
                        Log.d("WavDecoder", "WavDecoder: fmt chunk parsed - format=$audioFormat, channels=$channels, sampleRate=$sampleRate, bitsPerSample=$bitsPerSample")
                    }
                }
                "data" -> {
                    dataOffset = chunkDataStart
                    dataSize = if (chunkSize > 0 && chunkDataStart + chunkSize <= bytes.size) {
                        chunkSize
                    } else {
                        bytes.size - chunkDataStart
                    }
                    Log.d("WavDecoder", "WavDecoder: data chunk found at offset $dataOffset, size $dataSize")
                    break
                }
            }
            offset = chunkDataStart + chunkSize
            // Word alignment: if chunk size is odd, skip 1 padding byte
            if (chunkSize % 2 != 0) {
                offset++
            }
        }

        if (dataOffset < 0) {
            throw TrackLoadException(
                TrackLoadErrorCode.UNSUPPORTED_FORMAT,
                "Missing 'data' chunk in WAV file ($filename)"
            )
        }

        val bytesPerSample = bitsPerSample / 8
        if (bytesPerSample <= 0 || channels <= 0) {
            throw TrackLoadException(
                TrackLoadErrorCode.INVALID_PCM_FORMAT,
                "Invalid WAV parameters: bits=$bitsPerSample, channels=$channels"
            )
        }

        val totalFrames = dataSize / (channels * bytesPerSample)
        if (totalFrames <= 0) {
            throw TrackLoadException(
                TrackLoadErrorCode.EMPTY_AUDIO,
                "WAV file contains 0 audio frames"
            )
        }

        val left: FloatArray
        val right: FloatArray
        try {
            left = FloatArray(totalFrames)
            right = FloatArray(totalFrames)
        } catch (e: OutOfMemoryError) {
            throw TrackLoadException(
                TrackLoadErrorCode.OUT_OF_MEMORY,
                "Out of memory allocating $totalFrames audio frames for $filename: ${e.message}"
            )
        }

        var readPos = dataOffset
        var maxAbs = 0f
        var sumSquares = 0.0

        for (i in 0 until totalFrames) {
            for (c in 0 until channels) {
                val sampleValue: Float = when (bitsPerSample) {
                    8 -> {
                        // 8-bit WAV is unsigned PCM (0..255, 128 is 0.0)
                        val uVal = bytes[readPos].toInt() and 0xFF
                        readPos += 1
                        (uVal - 128) / 128.0f
                    }
                    16 -> {
                        val sVal = byteBuffer.getShort(readPos)
                        readPos += 2
                        sVal / 32768.0f
                    }
                    24 -> {
                        val b0 = bytes[readPos].toInt() and 0xFF
                        val b1 = bytes[readPos + 1].toInt() and 0xFF
                        val b2 = bytes[readPos + 2].toInt() and 0xFF
                        readPos += 3
                        val raw = (b2 shl 16) or (b1 shl 8) or b0
                        val s24 = if ((raw and 0x800000) != 0) (raw or -0x1000000) else raw
                        s24 / 8388608.0f
                    }
                    32 -> {
                        if (audioFormat == 3) {
                            // 32-bit IEEE float
                            val fVal = byteBuffer.getFloat(readPos)
                            readPos += 4
                            fVal
                        } else {
                            // 32-bit signed integer
                            val iVal = byteBuffer.getInt(readPos)
                            readPos += 4
                            (iVal.toDouble() / 2147483648.0).toFloat()
                        }
                    }
                    else -> {
                        readPos += bytesPerSample
                        0f
                    }
                }

                if (c == 0) {
                    left[i] = sampleValue
                    if (channels == 1) {
                        right[i] = sampleValue // Mono duplicate to right
                    }
                } else if (c == 1) {
                    right[i] = sampleValue
                }

                val absVal = abs(sampleValue)
                if (absVal > maxAbs) maxAbs = absVal
                sumSquares += (sampleValue * sampleValue)
            }
        }

        val totalSamples = totalFrames * channels
        val rms = if (totalSamples > 0) sqrt(sumSquares / totalSamples).toFloat() else 0f
        val peakDb = if (maxAbs > 0.00001f) (20.0 * log10(maxAbs.toDouble())).toFloat() else -96f
        val rmsDb = if (rms > 0.00001f) (20.0 * log10(rms.toDouble())).toFloat() else -96f
        val durationSec = if (sampleRate > 0) totalFrames.toDouble() / sampleRate else 0.0

        val formatDesc = when {
            audioFormat == 3 -> "WAV IEEE Float"
            bitsPerSample == 24 -> "WAV 24-bit PCM"
            bitsPerSample == 32 -> "WAV 32-bit PCM"
            bitsPerSample == 16 -> "WAV 16-bit PCM"
            bitsPerSample == 8 -> "WAV 8-bit PCM"
            else -> "WAV ${bitsPerSample}-bit"
        }

        Log.d("WavDecoder", "WavDecoder: decode complete - format=$formatDesc, sampleRate=$sampleRate, channels=$channels, frames=$totalFrames, duration=${durationSec}s")

        val metadata = AudioMetadata(
            title = filename.substringBeforeLast("."),
            artist = "Local Studio",
            sampleRate = sampleRate,
            bitDepth = bitsPerSample,
            channels = channels,
            durationSeconds = durationSec,
            fileSizeBytes = if (fileSizeBytes > 0) fileSizeBytes else bytes.size.toLong(),
            peakDb = peakDb,
            rmsDb = rmsDb,
            formatName = formatDesc,
            filePath = filePath
        )

        val buffer = AudioBuffer(
            leftChannel = left,
            rightChannel = right,
            sampleRate = sampleRate,
            originalBitDepth = bitsPerSample,
            originalSampleRate = sampleRate,
            channels = channels
        )

        Log.d("WavDecoder", "AudioBuffer created: $buffer")
        return DecodeResult(buffer, metadata)
    }
}
