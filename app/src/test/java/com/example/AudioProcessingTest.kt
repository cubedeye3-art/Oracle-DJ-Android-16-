package com.example

import com.example.audio.analysis.BpmDetector
import com.example.audio.analysis.KeyDetector
import com.example.audio.analysis.WaveformAnalyzer
import com.example.audio.decoder.WavDecoder
import com.example.audio.dsp.ChannelEqFilter
import com.example.audio.dsp.DJFilter
import com.example.audio.dsp.LookaheadLimiter
import com.example.audio.dsp.TimeStretcher
import com.example.audio.engine.MixerEngine
import com.example.audio.fx.FxRack
import com.example.audio.model.AudioBuffer
import com.example.audio.model.CrossfaderCurve
import com.example.audio.model.FxType
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AudioProcessingTest {

    @Test
    fun testWavDecoder192kHz24Bit() {
        val sampleRate = 192000
        val channels = 2
        val bits = 24
        val frameCount = 1920 // 10ms of audio

        val dataSize = frameCount * channels * 3
        val buffer = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)

        // RIFF header
        buffer.put("RIFF".toByteArray())
        buffer.putInt(36 + dataSize)
        buffer.put("WAVE".toByteArray())

        // fmt chunk
        buffer.put("fmt ".toByteArray())
        buffer.putInt(16)
        buffer.putShort(1) // PCM
        buffer.putShort(channels.toShort())
        buffer.putInt(sampleRate)
        buffer.putInt(sampleRate * channels * 3)
        buffer.putShort((channels * 3).toShort())
        buffer.putShort(bits.toShort())

        // data chunk
        buffer.put("data".toByteArray())
        buffer.putInt(dataSize)

        // Put 1 kHz sine wave in 24-bit little endian
        for (i in 0 until frameCount) {
            val sampleVal = (sin(2.0 * PI * 1000.0 * i / sampleRate) * 0.7 * 8388607.0).toInt()
            for (c in 0 until channels) {
                buffer.put((sampleVal and 0xFF).toByte())
                buffer.put(((sampleVal shr 8) and 0xFF).toByte())
                buffer.put(((sampleVal shr 16) and 0xFF).toByte())
            }
        }

        val decoded = WavDecoder.decode(ByteArrayInputStream(buffer.array()), "test192k24b.wav")
        assertEquals(192000, decoded.metadata.sampleRate)
        assertEquals(24, decoded.metadata.bitDepth)
        assertEquals(2, decoded.metadata.channels)
        assertEquals(frameCount, decoded.buffer.frameCount)
        assertTrue(decoded.buffer.leftChannel[48] > 0.5f)
        assertTrue(decoded.metadata.peakDb > -4.0f)
    }

    @Test
    fun testWavDecoder32BitFloat() {
        val sampleRate = 96000
        val channels = 2
        val frameCount = 960

        val dataSize = frameCount * channels * 4
        val buffer = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)

        buffer.put("RIFF".toByteArray())
        buffer.putInt(36 + dataSize)
        buffer.put("WAVE".toByteArray())

        buffer.put("fmt ".toByteArray())
        buffer.putInt(16)
        buffer.putShort(3) // 3 = IEEE Float
        buffer.putShort(channels.toShort())
        buffer.putInt(sampleRate)
        buffer.putInt(sampleRate * channels * 4)
        buffer.putShort((channels * 4).toShort())
        buffer.putShort(32)

        buffer.put("data".toByteArray())
        buffer.putInt(dataSize)

        for (i in 0 until frameCount) {
            val f = (sin(2.0 * PI * 440.0 * i / sampleRate) * 0.5).toFloat()
            buffer.putFloat(f)
            buffer.putFloat(f)
        }

        val decoded = WavDecoder.decode(ByteArrayInputStream(buffer.array()), "testFloat.wav")
        assertEquals(96000, decoded.metadata.sampleRate)
        assertEquals(32, decoded.metadata.bitDepth)
        assertEquals(frameCount, decoded.buffer.frameCount)
        assertTrue(abs(decoded.buffer.leftChannel[100]) > 0.01f)
    }

    @Test
    fun testBpmDetectionAndBeatGrid() {
        val sampleRate = 48000
        val bpm = 120.0
        val seconds = 5.0
        val totalFrames = (seconds * sampleRate).toInt()
        val left = FloatArray(totalFrames)
        val right = FloatArray(totalFrames)

        val beatFrames = (sampleRate * 60.0 / bpm).toInt()
        for (f in 0 until totalFrames step beatFrames) {
            for (k in 0 until 500) {
                if (f + k < totalFrames) {
                    left[f + k] = 0.9f
                    right[f + k] = 0.9f
                }
            }
        }

        val buf = AudioBuffer(left, right, sampleRate, 16, sampleRate, 2)
        val result = BpmDetector.detect(buf)
        assertTrue("Detected BPM ${result.bpm} should be near 120", abs(result.bpm - 120.0) < 5.0)
        assertTrue(result.confidence > 0.3f)
    }

    @Test
    fun testBpmDetection180BpmFastTempo() {
        val sampleRate = 48000
        val bpm = 180.0
        val seconds = 6.0
        val totalFrames = (seconds * sampleRate).toInt()
        val left = FloatArray(totalFrames)
        val right = FloatArray(totalFrames)

        val beatFrames = (sampleRate * 60.0 / bpm).toInt()
        for (f in 0 until totalFrames step beatFrames) {
            for (k in 0 until 400) {
                if (f + k < totalFrames) {
                    left[f + k] = 0.95f
                    right[f + k] = 0.95f
                }
            }
        }

        val buf = AudioBuffer(left, right, sampleRate, 16, sampleRate, 2)
        val result = BpmDetector.detect(buf)
        assertTrue("Detected BPM ${result.bpm} must resolve near 180, NOT 120 or 90", abs(result.bpm - 180.0) < 6.0)
        assertTrue("BPM must not fallback to 120", abs(result.bpm - 120.0) > 10.0)
    }

    @Test
    fun testBpmDetectionFailureDoesNotDefaultTo120() {
        val sampleRate = 48000
        // 4 seconds of flat silence
        val totalFrames = sampleRate * 4
        val left = FloatArray(totalFrames)
        val right = FloatArray(totalFrames)

        val buf = AudioBuffer(left, right, sampleRate, 16, sampleRate, 2)
        val result = BpmDetector.detect(buf)
        assertEquals("BPM for silent audio must be 0.0 (failure), NEVER 120.0", 0.0, result.bpm, 0.001)
        assertEquals(0.0f, result.confidence, 0.001f)
    }

    @Test
    fun testKeyDetectionProducesCamelotAndConfidence() {
        val sampleRate = 48000
        val totalFrames = sampleRate * 3
        val left = FloatArray(totalFrames)
        val right = FloatArray(totalFrames)

        // 440 Hz (Note A4) tone with harmonics
        for (i in 0 until totalFrames) {
            val s = (sin(2.0 * PI * 440.0 * i / sampleRate) * 0.7 +
                    sin(2.0 * PI * 554.37 * i / sampleRate) * 0.5 + // C#5
                    sin(2.0 * PI * 659.25 * i / sampleRate) * 0.5).toFloat() // E5 -> A Major chord
            left[i] = s
            right[i] = s
        }

        val buf = AudioBuffer(left, right, sampleRate, 16, sampleRate, 2)
        val result = KeyDetector.detect(buf)
        assertTrue(result.keyName.isNotBlank())
        assertTrue(result.camelotCode.isNotBlank())
        assertTrue(result.confidence > 0f)
    }

    @Test
    fun testWaveformGeneration() {
        val sampleRate = 48000
        val totalFrames = sampleRate * 2
        val left = FloatArray(totalFrames) { sin(it.toDouble() * 0.05).toFloat() * 0.8f }
        val right = FloatArray(totalFrames) { left[it] }

        val buf = AudioBuffer(left, right, sampleRate, 16, sampleRate, 2)
        val wave = WaveformAnalyzer.analyze(buf)

        assertNotNull(wave.overviewPeaks)
        assertEquals(600, wave.overviewPeaks.size)
        assertTrue(wave.overviewPeaks[10] > 0.1f)
    }

    @Test
    fun testChannelEqIsolatorKill() {
        val eq = ChannelEqFilter(48000)
        eq.setGains(0f, 0f, 0f)

        val input = 1.0f
        var output = input
        for (i in 0 until 200) {
            output = eq.processLeft(input)
        }
        assertTrue("EQ kill must reduce amplitude significantly: $output", abs(output) < 0.1f)
    }

    @Test
    fun testDJFilterLowPass() {
        val filter = DJFilter(48000)
        filter.setFilterValue(-0.8f) // Deep Low Pass

        var highFreqSignal = 0f
        for (i in 0 until 100) {
            highFreqSignal = filter.processLeft(sin(i * 1.5).toFloat())
        }
        assertTrue("LPF should suppress high frequency signal", abs(highFreqSignal) < 0.3f)
    }

    @Test
    fun testCrossfaderCurves() {
        val dummyA = com.example.audio.engine.DeckAudioPlayer(com.example.audio.model.DeckId.DECK_A, 48000)
        val dummyB = com.example.audio.engine.DeckAudioPlayer(com.example.audio.model.DeckId.DECK_B, 48000)
        val mixer = MixerEngine(dummyA, dummyB, 48000)

        // Center Linear
        val (linA, linB) = mixer.calculateCrossfaderGains(0.5f, CrossfaderCurve.LINEAR)
        assertEquals(0.5f, linA, 0.01f)
        assertEquals(0.5f, linB, 0.01f)

        // Center Constant Power (should be ~0.707 = -3dB)
        val (cpA, cpB) = mixer.calculateCrossfaderGains(0.5f, CrossfaderCurve.CONSTANT_POWER)
        assertEquals(0.707f, cpA, 0.01f)
        assertEquals(0.707f, cpB, 0.01f)

        // Scratch cut curve at extreme left
        val (scA1, scB1) = mixer.calculateCrossfaderGains(0.02f, CrossfaderCurve.SCRATCH)
        assertEquals(1.0f, scA1, 0.01f)
        assertEquals(0.0f, scB1, 0.01f)

        // Scratch cut curve at center (both channels full volume)
        val (scA2, scB2) = mixer.calculateCrossfaderGains(0.5f, CrossfaderCurve.SCRATCH)
        assertEquals(1.0f, scA2, 0.01f)
        assertEquals(1.0f, scB2, 0.01f)
    }

    @Test
    fun testLookaheadLimiterTruePeak() {
        val limiter = LookaheadLimiter(48000)
        limiter.ceilingDb = -0.5f

        val loudSignal = 3.5f
        var maxOut = 0f
        for (i in 0 until 300) {
            val (outL, outR) = limiter.process(loudSignal, loudSignal)
            if (abs(outL) > maxOut) maxOut = abs(outL)
            if (abs(outR) > maxOut) maxOut = abs(outR)
        }

        assertTrue("Limiter must clamp peak below ceiling: $maxOut", maxOut <= 0.95f)
    }

    @Test
    fun testFxRackRealtimeProcessing() {
        val fx = FxRack(48000)
        fx.state = fx.state.copy(
            enabled = true,
            type = FxType.BIT_CRUSHER,
            dryWet = 1.0f,
            param1 = 0.8f
        )

        val inL = FloatArray(128) { 0.54321f }
        val inR = FloatArray(128) { 0.54321f }
        val outL = FloatArray(128)
        val outR = FloatArray(128)

        fx.processBlock(inL, inR, outL, outR, 128)
        assertTrue(outL[0] != 0.54321f)
    }
}
