package com.example.audio.engine

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Process
import com.example.audio.model.EngineTelemetry
import com.example.audio.model.LatencyProfile
import com.example.audio.model.ThermalPerformanceMode
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

class AudioOutputEngine(
    val mixer: MixerEngine,
    val deviceManager: AudioDeviceManager
) {
    private var audioTrack: AudioTrack? = null
    private var audioThread: Thread? = null
    private val isRunning = AtomicBoolean(false)

    var outputSampleRate: Int = 48000
        private set
    var latencyProfile: LatencyProfile = LatencyProfile.LOW
        private set
    var performanceMode: ThermalPerformanceMode = ThermalPerformanceMode.BALANCED

    // Audio buffers
    private var bufferFrames = 512
    private var renderBufferL = FloatArray(bufferFrames)
    private var renderBufferR = FloatArray(bufferFrames)
    private var preMasterBufL = FloatArray(bufferFrames)
    private var preMasterBufR = FloatArray(bufferFrames)
    private var interleavedFloat = FloatArray(bufferFrames * 2)

    // Telemetry
    @Volatile var dspCpuLoad: Float = 0f
    @Volatile var totalUnderruns: Int = 0
    private var underrunCheckCount = 0

    // Master recording state
    private val isRecording = AtomicBoolean(false)
    private var recordPreMaster = false
    private var recordSampleRate = 48000
    private var recordBitDepth = 24
    private var recordingFile: File? = null
    private var recordStream: FileOutputStream? = null
    private val recordQueue = ConcurrentLinkedQueue<FloatArray>()
    private var recordThread: Thread? = null
    private var totalRecordedBytes: Long = 0L

    // Live Streaming hook
    var liveStreamBroadcaster: com.example.audio.streaming.LiveStreamBroadcaster? = null

    init {
        outputSampleRate = deviceManager.nativeSampleRate
        mixer.setSampleRate(outputSampleRate)
    }

    fun start() {
        if (isRunning.get()) return
        isRunning.set(true)

        val minBufBytes = AudioTrack.getMinBufferSize(
            outputSampleRate,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_FLOAT
        )

        bufferFrames = latencyProfile.frames
        val bufferSizeBytes = maxOf(minBufBytes, bufferFrames * 2 * 4 * 2)

        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
            .setSampleRate(outputSampleRate)
            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
            .build()

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(format)
            .setBufferSizeInBytes(bufferSizeBytes)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .build()

        renderBufferL = FloatArray(bufferFrames)
        renderBufferR = FloatArray(bufferFrames)
        preMasterBufL = FloatArray(bufferFrames)
        preMasterBufR = FloatArray(bufferFrames)
        interleavedFloat = FloatArray(bufferFrames * 2)

        audioTrack?.play()

        audioThread = Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            runAudioLoop()
        }, "OracleDJ-RealtimeAudioThread")

        audioThread?.start()
    }

    private fun runAudioLoop() {
        val track = audioTrack ?: return
        val bufferDurationNs = (bufferFrames.toDouble() / outputSampleRate * 1_000_000_000.0).toLong()

        while (isRunning.get()) {
            val startTimeNs = System.nanoTime()

            // 1. Process DSP block
            mixer.processBlock(
                outL = renderBufferL,
                outR = renderBufferR,
                preMasterCaptureL = if (isRecording.get() && recordPreMaster) preMasterBufL else null,
                preMasterCaptureR = if (isRecording.get() && recordPreMaster) preMasterBufR else null,
                frameCount = bufferFrames
            )

            // 2. Interleave stereo floats for AudioTrack
            var idx = 0
            for (i in 0 until bufferFrames) {
                interleavedFloat[idx++] = renderBufferL[i]
                interleavedFloat[idx++] = renderBufferR[i]
            }

            // 3. Write to AudioTrack (non-blocking / blocking until space available)
            track.write(interleavedFloat, 0, bufferFrames * 2, AudioTrack.WRITE_BLOCKING)

            // 4. Send to live stream broadcaster
            liveStreamBroadcaster?.processAudioBlock(interleavedFloat, bufferFrames, outputSampleRate)

            // 5. Send to recorder ring queue without blocking
            if (isRecording.get()) {
                val copy = FloatArray(bufferFrames * 2)
                if (recordPreMaster) {
                    var rIdx = 0
                    for (i in 0 until bufferFrames) {
                        copy[rIdx++] = preMasterBufL[i]
                        copy[rIdx++] = preMasterBufR[i]
                    }
                } else {
                    System.arraycopy(interleavedFloat, 0, copy, 0, copy.size)
                }
                recordQueue.offer(copy)
            }

            // 5. Measure CPU DSP load
            val elapsedNs = System.nanoTime() - startTimeNs
            val load = (elapsedNs.toFloat() / bufferDurationNs.toFloat() * 100f).coerceIn(0f, 100f)
            dspCpuLoad = dspCpuLoad * 0.9f + load * 0.1f

            // 6. Check underruns
            underrunCheckCount++
            if (underrunCheckCount >= 100) {
                underrunCheckCount = 0
                val underruns = track.underrunCount
                if (underruns > totalUnderruns) {
                    totalUnderruns = underruns
                    // Auto-scale buffer size if persistent underruns occur
                    if (latencyProfile == LatencyProfile.ULTRA_LOW) {
                        setLatencyProfile(LatencyProfile.LOW)
                    }
                }
            }
        }
    }

    fun setLatencyProfile(newProfile: LatencyProfile) {
        if (newProfile == latencyProfile) return
        latencyProfile = newProfile
        restart()
    }

    fun restart() {
        stop()
        start()
    }

    fun stop() {
        isRunning.set(false)
        try {
            audioThread?.join(200)
        } catch (e: Exception) {
            // Ignore
        }
        audioThread = null
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) {
            // Ignore
        }
        audioTrack = null
    }

    // Recording API
    fun startRecording(file: File, sampleRate: Int = 48000, bitDepth: Int = 24, preMaster: Boolean = false) {
        if (isRecording.get()) return
        recordingFile = file
        recordSampleRate = sampleRate
        recordBitDepth = bitDepth
        recordPreMaster = preMaster
        totalRecordedBytes = 0L

        val fos = FileOutputStream(file)
        recordStream = fos
        // Write placeholder 44-byte WAV header
        fos.write(ByteArray(44))

        isRecording.set(true)

        recordThread = Thread({
            val byteBuffer = ByteBuffer.allocate(bufferFrames * 2 * 4).order(ByteOrder.LITTLE_ENDIAN)
            while (isRecording.get() || recordQueue.isNotEmpty()) {
                val block = recordQueue.poll()
                if (block != null) {
                    byteBuffer.clear()
                    if (recordBitDepth == 32) {
                        for (s in block) byteBuffer.putFloat(s)
                    } else if (recordBitDepth == 24) {
                        for (s in block) {
                            val scaled = (s.coerceIn(-1f, 1f) * 8388607.0f).toInt()
                            byteBuffer.put((scaled and 0xFF).toByte())
                            byteBuffer.put(((scaled shr 8) and 0xFF).toByte())
                            byteBuffer.put(((scaled shr 16) and 0xFF).toByte())
                        }
                    } else {
                        // 16-bit
                        for (s in block) {
                            val scaled = (s.coerceIn(-1f, 1f) * 32767.0f).toInt().toShort()
                            byteBuffer.putShort(scaled)
                        }
                    }
                    val bytesWritten = byteBuffer.position()
                    fos.write(byteBuffer.array(), 0, bytesWritten)
                    totalRecordedBytes += bytesWritten
                } else {
                    try {
                        Thread.sleep(10)
                    } catch (e: Exception) {
                        // Ignore
                    }
                }
            }
            fos.flush()
            fos.close()
            // Fix WAV header with actual data size
            writeWavHeader(file, recordSampleRate, recordBitDepth, 2, totalRecordedBytes)
        }, "OracleDJ-RecordWriterThread")
        recordThread?.start()
    }

    fun stopRecording(): File? {
        if (!isRecording.get()) return null
        isRecording.set(false)
        try {
            recordThread?.join(1000)
        } catch (e: Exception) {
            // Ignore
        }
        val file = recordingFile
        recordingFile = null
        recordStream = null
        return file
    }

    private fun writeWavHeader(file: File, sampleRate: Int, bitDepth: Int, channels: Int, dataBytes: Long) {
        val raf = RandomAccessFile(file, "rw")
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)

        val totalDataLen = dataBytes + 36
        val bytesPerSample = bitDepth / 8
        val byteRate = sampleRate * channels * bytesPerSample

        header.put("RIFF".toByteArray())
        header.putInt(totalDataLen.toInt())
        header.put("WAVE".toByteArray())
        header.put("fmt ".toByteArray())
        header.putInt(16) // Subchunk1Size for PCM
        header.putShort(if (bitDepth == 32) 3.toShort() else 1.toShort()) // 3 = IEEE Float, 1 = PCM
        header.putShort(channels.toShort())
        header.putInt(sampleRate)
        header.putInt(byteRate)
        header.putShort((channels * bytesPerSample).toShort())
        header.putShort(bitDepth.toShort())
        header.put("data".toByteArray())
        header.putInt(dataBytes.toInt())

        raf.seek(0)
        raf.write(header.array())
        raf.close()
    }

    fun getTelemetry(): EngineTelemetry {
        val metaA = mixer.deckA.metadata
        val metaB = mixer.deckB.metadata
        return EngineTelemetry(
            sourceSampleRateA = metaA?.sampleRate ?: 0,
            sourceBitDepthA = metaA?.bitDepth ?: 0,
            sourceChannelsA = metaA?.channels ?: 0,
            sourceFormatNameA = metaA?.formatName ?: "Empty",

            sourceSampleRateB = metaB?.sampleRate ?: 0,
            sourceBitDepthB = metaB?.bitDepth ?: 0,
            sourceChannelsB = metaB?.channels ?: 0,
            sourceFormatNameB = metaB?.formatName ?: "Empty",

            dspSampleRate = outputSampleRate,
            outputDeviceSampleRate = deviceManager.nativeSampleRate,
            outputDeviceName = deviceManager.currentDeviceName,
            isUsbDacConnected = deviceManager.isUsbAudio,
            isBluetooth = deviceManager.isBluetoothAudio,

            dspCpuLoadPercent = dspCpuLoad,
            bufferSizeFrames = bufferFrames,
            actualLatencyMs = (bufferFrames.toFloat() / outputSampleRate) * 1000f,
            underrunCount = totalUnderruns,
            droppedBufferCount = 0,
            latencyProfile = latencyProfile,
            performanceMode = performanceMode,
            thermalThrottlingStatus = deviceManager.thermalStatusString
        )
    }
}
