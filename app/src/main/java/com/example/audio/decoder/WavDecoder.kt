package com.example.audio.decoder

import com.example.audio.model.AudioBuffer
import com.example.audio.model.AudioMetadata
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

object WavDecoder {

    data class DecodeResult(
        val buffer: AudioBuffer,
        val metadata: AudioMetadata
    )

    fun decode(file: File): DecodeResult {
        file.inputStream().use { stream ->
            return decode(stream, file.name, file.length(), file.absolutePath)
        }
    }

    fun decode(
        inputStream: InputStream,
        filename: String = "audio.wav",
        fileSizeBytes: Long = 0L,
        filePath: String = ""
    ): DecodeResult {
        val bytes = inputStream.readBytes()
        val byteBuffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        // Read RIFF header
        if (bytes.size < 44) {
            throw IllegalArgumentException("File is too small to be a valid WAV/RIFF file")
        }

        val riffHeader = String(bytes, 0, 4)
        if (riffHeader != "RIFF") {
            throw IllegalArgumentException("Invalid RIFF header: $riffHeader")
        }

        val waveHeader = String(bytes, 8, 4)
        if (waveHeader != "WAVE") {
            throw IllegalArgumentException("Invalid WAVE identifier: $waveHeader")
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
                    if (chunkSize >= 16) {
                        audioFormat = byteBuffer.getShort(chunkDataStart).toInt() and 0xFFFF
                        channels = byteBuffer.getShort(chunkDataStart + 2).toInt() and 0xFFFF
                        sampleRate = byteBuffer.getInt(chunkDataStart + 4)
                        bitsPerSample = byteBuffer.getShort(chunkDataStart + 14).toInt() and 0xFFFF

                        if (audioFormat == 65534 && chunkSize >= 40) { // WAVE_FORMAT_EXTENSIBLE
                            val subFormat = byteBuffer.getShort(chunkDataStart + 24).toInt() and 0xFFFF
                            audioFormat = subFormat
                        }
                    }
                }
                "data" -> {
                    dataOffset = chunkDataStart
                    dataSize = if (chunkSize > 0 && chunkDataStart + chunkSize <= bytes.size) {
                        chunkSize
                    } else {
                        bytes.size - chunkDataStart
                    }
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
            throw IllegalArgumentException("Missing 'data' chunk in WAV file")
        }

        val bytesPerSample = bitsPerSample / 8
        if (bytesPerSample <= 0 || channels <= 0) {
            throw IllegalArgumentException("Invalid WAV parameters: bits=$bitsPerSample, channels=$channels")
        }

        val totalFrames = dataSize / (channels * bytesPerSample)
        val left = FloatArray(totalFrames)
        val right = FloatArray(totalFrames)

        var readPos = dataOffset
        var maxAbs = 0f
        var sumSquares = 0.0

        for (i in 0 until totalFrames) {
            for (c in 0 until channels) {
                val sampleValue: Float = when (bitsPerSample) {
                    8 -> {
                        // 8-bit WAV is unsigned (0 .. 255, 128 is center)
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
                        val b2 = bytes[readPos + 2].toInt()
                        readPos += 3
                        val s24 = (b2 shl 16) or (b1 shl 8) or b0
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
                        right[i] = sampleValue
                    }
                } else if (c == 1) {
                    right[i] = sampleValue
                }

                val absVal = kotlin.math.abs(sampleValue)
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
            else -> "WAV ${bitsPerSample}-bit"
        }

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

        return DecodeResult(buffer, metadata)
    }
}
