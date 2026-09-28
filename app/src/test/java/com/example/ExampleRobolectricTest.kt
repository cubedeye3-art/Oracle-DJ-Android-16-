package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.audio.decoder.SampleTrackGenerator
import com.example.audio.decoder.TrackLoadErrorCode
import com.example.audio.decoder.TrackLoadException
import com.example.audio.decoder.WavDecoder
import com.example.audio.streaming.AVAILABLE_BITRATES
import com.example.audio.streaming.StreamPlatform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Oracle DJ", appName)
    }

    @Test
    fun `verify live stream platform configurations`() {
        // Facebook Live
        val fb = StreamPlatform.FACEBOOK
        assertEquals("Facebook Live", fb.displayName)
        assertTrue(fb.defaultIngestUrl.contains("facebook.com"))

        // YouTube Live
        val yt = StreamPlatform.YOUTUBE
        assertEquals("YouTube Live", yt.displayName)
        assertTrue(yt.defaultIngestUrl.contains("youtube.com"))

        // X (Twitter)
        val x = StreamPlatform.X_TWITTER
        assertEquals("X (Twitter)", x.displayName)
        assertTrue(x.defaultIngestUrl.contains("periscope.tv"))

        // Audio Bitrates
        assertTrue(AVAILABLE_BITRATES.any { it.kbps == 128 })
        assertTrue(AVAILABLE_BITRATES.any { it.kbps == 192 })
        assertTrue(AVAILABLE_BITRATES.any { it.kbps == 256 })
        assertTrue(AVAILABLE_BITRATES.any { it.kbps == 320 })
    }

    @Test
    fun `synthetic audio generator works for Deck A and Deck B`() {
        val deckA = SampleTrackGenerator.generateDeckATrack()
        assertNotNull(deckA.buffer)
        assertNotNull(deckA.metadata)
        assertEquals(48000, deckA.buffer.sampleRate)
        assertEquals(2, deckA.buffer.channels)
        assertTrue(deckA.buffer.frameCount > 0)
        assertEquals(deckA.buffer.frameCount, deckA.buffer.leftChannel.size)
        assertEquals(deckA.buffer.frameCount, deckA.buffer.rightChannel.size)

        val deckB = SampleTrackGenerator.generateDeckBTrack()
        assertNotNull(deckB.buffer)
        assertEquals(48000, deckB.buffer.sampleRate)
        assertEquals(2, deckB.buffer.channels)
        assertTrue(deckB.buffer.frameCount > 0)
    }

    @Test
    fun `wav decoder decodes 16-bit PCM stereo WAV correctly`() {
        val sampleRate = 44100
        val channels = 2
        val frames = 100
        val wavBytes = createSyntheticWav(
            audioFormat = 1,
            channels = channels,
            sampleRate = sampleRate,
            bitsPerSample = 16,
            frameCount = frames
        )

        val result = WavDecoder.decode(ByteArrayInputStream(wavBytes), "test16.wav", wavBytes.size.toLong(), "test16.wav")
        assertEquals(sampleRate, result.buffer.sampleRate)
        assertEquals(16, result.buffer.originalBitDepth)
        assertEquals(channels, result.buffer.channels)
        assertEquals(frames, result.buffer.frameCount)
        assertEquals(frames, result.buffer.leftChannel.size)
        assertEquals(frames, result.buffer.rightChannel.size)
    }

    @Test
    fun `wav decoder decodes 24-bit PCM stereo WAV correctly`() {
        val sampleRate = 48000
        val channels = 2
        val frames = 50
        val wavBytes = createSyntheticWav(
            audioFormat = 1,
            channels = channels,
            sampleRate = sampleRate,
            bitsPerSample = 24,
            frameCount = frames
        )

        val result = WavDecoder.decode(ByteArrayInputStream(wavBytes), "test24.wav", wavBytes.size.toLong(), "test24.wav")
        assertEquals(sampleRate, result.buffer.sampleRate)
        assertEquals(24, result.buffer.originalBitDepth)
        assertEquals(frames, result.buffer.frameCount)
    }

    @Test
    fun `wav decoder decodes 32-bit IEEE float WAV correctly`() {
        val sampleRate = 96000
        val channels = 2
        val frames = 64
        val wavBytes = createSyntheticWav(
            audioFormat = 3, // IEEE Float
            channels = channels,
            sampleRate = sampleRate,
            bitsPerSample = 32,
            frameCount = frames
        )

        val result = WavDecoder.decode(ByteArrayInputStream(wavBytes), "test32float.wav", wavBytes.size.toLong(), "test32float.wav")
        assertEquals(96000, result.buffer.sampleRate)
        assertEquals(96000, result.buffer.originalSampleRate)
        assertEquals(32, result.buffer.originalBitDepth)
        assertEquals(frames, result.buffer.frameCount)
    }

    @Test
    fun `wav decoder handles mono WAV and replicates to left and right channels`() {
        val sampleRate = 44100
        val channels = 1 // Mono
        val frames = 80
        val wavBytes = createSyntheticWav(
            audioFormat = 1,
            channels = channels,
            sampleRate = sampleRate,
            bitsPerSample = 16,
            frameCount = frames
        )

        val result = WavDecoder.decode(ByteArrayInputStream(wavBytes), "mono.wav", wavBytes.size.toLong(), "mono.wav")
        assertEquals(1, result.buffer.channels)
        assertEquals(frames, result.buffer.frameCount)
        assertEquals(frames, result.buffer.leftChannel.size)
        assertEquals(frames, result.buffer.rightChannel.size)
        // Check mono replication: left and right channels should be equal
        for (i in 0 until frames) {
            assertEquals(result.buffer.leftChannel[i], result.buffer.rightChannel[i], 0.0001f)
        }
    }

    @Test
    fun `wav decoder supports 192 kHz high sample rate metadata`() {
        val sampleRate = 192000
        val channels = 2
        val frames = 40
        val wavBytes = createSyntheticWav(
            audioFormat = 1,
            channels = channels,
            sampleRate = sampleRate,
            bitsPerSample = 24,
            frameCount = frames
        )

        val result = WavDecoder.decode(ByteArrayInputStream(wavBytes), "hi_res_192k.wav", wavBytes.size.toLong(), "hi_res_192k.wav")
        assertEquals(192000, result.buffer.sampleRate)
        assertEquals(192000, result.buffer.originalSampleRate)
        assertEquals(24, result.buffer.originalBitDepth)
    }

    @Test
    fun `track load error codes provide informative user messages`() {
        val codes = TrackLoadErrorCode.values()
        assertTrue(codes.contains(TrackLoadErrorCode.PERMISSION_DENIED))
        assertTrue(codes.contains(TrackLoadErrorCode.FILE_NOT_FOUND))
        assertTrue(codes.contains(TrackLoadErrorCode.UNSUPPORTED_FORMAT))
        assertTrue(codes.contains(TrackLoadErrorCode.EXTRACTOR_FAILURE))
        assertTrue(codes.contains(TrackLoadErrorCode.CODEC_INIT_FAILURE))
        assertTrue(codes.contains(TrackLoadErrorCode.CODEC_DECODE_FAILURE))
        assertTrue(codes.contains(TrackLoadErrorCode.INVALID_PCM_FORMAT))
        assertTrue(codes.contains(TrackLoadErrorCode.EMPTY_AUDIO))
        assertTrue(codes.contains(TrackLoadErrorCode.OUT_OF_MEMORY))
        assertTrue(codes.contains(TrackLoadErrorCode.DECK_LOAD_FAILURE))
        assertTrue(codes.contains(TrackLoadErrorCode.PLAYBACK_FAILURE))

        for (code in codes) {
            assertTrue(code.userMessage.isNotBlank())
        }
    }

    private fun createSyntheticWav(
        audioFormat: Int,
        channels: Int,
        sampleRate: Int,
        bitsPerSample: Int,
        frameCount: Int
    ): ByteArray {
        val bytesPerSample = bitsPerSample / 8
        val dataSize = frameCount * channels * bytesPerSample
        val totalSize = 44 + dataSize
        val buffer = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN)

        // RIFF header
        buffer.put("RIFF".toByteArray())
        buffer.putInt(totalSize - 8)
        buffer.put("WAVE".toByteArray())

        // fmt chunk
        buffer.put("fmt ".toByteArray())
        buffer.putInt(16) // Subchunk1Size for PCM
        buffer.putShort(audioFormat.toShort())
        buffer.putShort(channels.toShort())
        buffer.putInt(sampleRate)
        buffer.putInt(sampleRate * channels * bytesPerSample) // byteRate
        buffer.putShort((channels * bytesPerSample).toShort()) // blockAlign
        buffer.putShort(bitsPerSample.toShort())

        // data chunk
        buffer.put("data".toByteArray())
        buffer.putInt(dataSize)

        // Generate synthetic audio samples (e.g. 440 Hz tone)
        for (i in 0 until frameCount) {
            val sampleVal = (Math.sin(2.0 * Math.PI * 440.0 * i / sampleRate) * 0.7).toFloat()
            for (c in 0 until channels) {
                when (bitsPerSample) {
                    8 -> {
                        val uVal = ((sampleVal * 127f) + 128f).toInt().coerceIn(0, 255)
                        buffer.put(uVal.toByte())
                    }
                    16 -> {
                        val sVal = (sampleVal * 32767f).toInt().toShort()
                        buffer.putShort(sVal)
                    }
                    24 -> {
                        val s24 = (sampleVal * 8388607f).toInt()
                        buffer.put((s24 and 0xFF).toByte())
                        buffer.put(((s24 shr 8) and 0xFF).toByte())
                        buffer.put(((s24 shr 16) and 0xFF).toByte())
                    }
                    32 -> {
                        if (audioFormat == 3) {
                            buffer.putFloat(sampleVal)
                        } else {
                            buffer.putInt((sampleVal * 2147483647.0).toInt())
                        }
                    }
                }
            }
        }

        return buffer.array()
    }
}
