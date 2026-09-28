package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.decoder.SampleTrackGenerator
import com.example.audio.decoder.UniversalAudioDecoder
import com.example.audio.engine.AudioDeviceManager
import com.example.audio.engine.AudioOutputEngine
import com.example.audio.engine.DeckAudioPlayer
import com.example.audio.engine.MixerEngine
import com.example.audio.model.BeatDivision
import com.example.audio.model.CrossfaderAssign
import com.example.audio.model.CrossfaderCurve
import com.example.audio.model.DeckId
import com.example.audio.model.DeckUiState
import com.example.audio.model.EngineTelemetry
import com.example.audio.model.FxType
import com.example.audio.model.LatencyProfile
import com.example.audio.model.MasteringPreset
import com.example.audio.model.MasteringState
import com.example.audio.model.MixerState
import com.example.audio.model.PitchRange
import com.example.audio.model.ThermalPerformanceMode
import com.example.audio.streaming.LiveStreamBroadcaster
import com.example.audio.streaming.LiveStreamConfig
import com.example.audio.streaming.LiveStreamMetrics
import com.example.audio.streaming.StreamPlatform
import com.example.data.DjDatabase
import com.example.data.ProjectEntity
import com.example.data.TrackEntity
import com.example.data.TrackRepository
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DjViewModel(application: Application) : AndroidViewModel(application) {

    private val db = DjDatabase.getInstance(application)
    val repository = TrackRepository(application, db.trackDao(), db.projectDao())

    val deckA: DeckAudioPlayer = DeckAudioPlayer(DeckId.DECK_A, 48000)
    val deckB: DeckAudioPlayer = DeckAudioPlayer(DeckId.DECK_B, 48000)
    val mixer: MixerEngine = MixerEngine(deckA, deckB, 48000)
    private val deviceManager: AudioDeviceManager = AudioDeviceManager(application) {
        // Headphones unplugged callback: pause both decks
        deckA.pause()
        deckB.pause()
    }
    val outputEngine: AudioOutputEngine = AudioOutputEngine(mixer, deviceManager)
    val liveStreamBroadcaster: LiveStreamBroadcaster = LiveStreamBroadcaster(application)

    // Reactive UI States
    private val _deckAState = MutableStateFlow(deckA.getUiState())
    val deckAState: StateFlow<DeckUiState> = _deckAState.asStateFlow()

    private val _deckBState = MutableStateFlow(deckB.getUiState())
    val deckBState: StateFlow<DeckUiState> = _deckBState.asStateFlow()

    private val _mixerState = MutableStateFlow(mixer.state)
    val mixerState: StateFlow<MixerState> = _mixerState.asStateFlow()

    private val _masteringState = MutableStateFlow(mixer.masteringChain.state)
    val masteringState: StateFlow<MasteringState> = _masteringState.asStateFlow()

    private val _telemetry = MutableStateFlow(outputEngine.getTelemetry())
    val telemetry: StateFlow<EngineTelemetry> = _telemetry.asStateFlow()

    val liveStreamMetrics: StateFlow<LiveStreamMetrics> = liveStreamBroadcaster.metrics

    val tracks: StateFlow<List<TrackEntity>> = repository.allTracks.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val projects: StateFlow<List<ProjectEntity>> = repository.allProjects.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    @Volatile var isRecordingMaster = false
        private set

    init {
        val nativeSr = deviceManager.nativeSampleRate
        if (nativeSr > 0 && nativeSr != 48000) {
            mixer.setSampleRate(nativeSr)
        }
        // Connect live stream broadcaster to audio output engine
        outputEngine.liveStreamBroadcaster = liveStreamBroadcaster

        // Start real-time audio thread
        outputEngine.start()

        // Initialize with default high-res algorithmic tracks
        viewModelScope.launch(Dispatchers.Default) {
            val trackA = SampleTrackGenerator.generateDeckATrack()
            deckA.loadTrack(trackA.buffer, trackA.metadata)

            val trackB = SampleTrackGenerator.generateDeckBTrack()
            deckB.loadTrack(trackB.buffer, trackB.metadata)

            // Seed DB if empty
            repository.initializeBuiltInTracks()
            repository.scanDeviceMedia()
        }

        // High-speed UI refresh loop (runs at 60 FPS)
        viewModelScope.launch(Dispatchers.Main) {
            while (isActive) {
                _deckAState.value = deckA.getUiState()
                _deckBState.value = deckB.getUiState()

                _mixerState.value = mixer.state.copy(
                    channelA = mixer.state.channelA.copy(
                        vuLevelL = deckA.vuLevelL,
                        vuLevelR = deckA.vuLevelR
                    ),
                    channelB = mixer.state.channelB.copy(
                        vuLevelL = deckB.vuLevelL,
                        vuLevelR = deckB.vuLevelR
                    ),
                    preMasterVuL = mixer.preMasterPeakL,
                    preMasterVuR = mixer.preMasterPeakR,
                    masterVuL = mixer.masterPeakL,
                    masterVuR = mixer.masterPeakR
                )

                _masteringState.value = mixer.masteringChain.state
                _telemetry.value = outputEngine.getTelemetry()

                delay(16) // ~60 FPS
            }
        }
    }

    // Transport controls
    fun togglePlayPause(deckId: DeckId) {
        val target = if (deckId == DeckId.DECK_A) deckA else deckB
        target.togglePlayPause()
    }

    fun cuePress(deckId: DeckId, isDown: Boolean) {
        val target = if (deckId == DeckId.DECK_A) deckA else deckB
        target.cuePress(isDown)
    }

    fun setCuePointHere(deckId: DeckId) {
        val target = if (deckId == DeckId.DECK_A) deckA else deckB
        target.setCuePointHere()
    }

    fun sync(deckId: DeckId) {
        val (slave, master) = if (deckId == DeckId.DECK_A) Pair(deckA, deckB) else Pair(deckB, deckA)
        val masterBpm = master.currentBpm
        if (masterBpm > 0 && slave.originalBpm > 0) {
            // Match tempo
            val neededFactor = masterBpm / slave.originalBpm
            val neededPercent = ((neededFactor - 1.0) / slave.pitchRange.maxPercent).toFloat()
            slave.setTempo(neededPercent)

            // Phase and beat align
            if (master.playState == com.example.audio.model.PlayState.PLAYING) {
                val masterBeatPhase = master.currentFramePosition % master.beatIntervalFrames
                val slaveBase = slave.currentFramePosition - (slave.currentFramePosition % slave.beatIntervalFrames)
                slave.currentFramePosition = (slaveBase + masterBeatPhase).coerceAtLeast(0.0)
            }
        }
    }

    fun setTempo(deckId: DeckId, percent: Float) {
        val target = if (deckId == DeckId.DECK_A) deckA else deckB
        target.setTempo(percent)
    }

    fun setPitchRange(deckId: DeckId, range: PitchRange) {
        val target = if (deckId == DeckId.DECK_A) deckA else deckB
        target.setPitchRange(range)
    }

    fun pitchBend(deckId: DeckId, amount: Float) {
        val target = if (deckId == DeckId.DECK_A) deckA else deckB
        target.pitchBend(amount)
    }

    fun toggleKeyLock(deckId: DeckId) {
        val target = if (deckId == DeckId.DECK_A) deckA else deckB
        target.keyLock = !target.keyLock
    }

    fun toggleSlip(deckId: DeckId) {
        val target = if (deckId == DeckId.DECK_A) deckA else deckB
        target.slipMode = !target.slipMode
    }

    fun toggleReverse(deckId: DeckId) {
        val target = if (deckId == DeckId.DECK_A) deckA else deckB
        target.reverse = !target.reverse
    }

    fun toggleQuantize(deckId: DeckId) {
        val target = if (deckId == DeckId.DECK_A) deckA else deckB
        target.quantize = !target.quantize
    }

    // Scratching
    fun startScratch(deckId: DeckId) {
        val target = if (deckId == DeckId.DECK_A) deckA else deckB
        target.startScratch()
    }

    fun updateScratch(deckId: DeckId, deltaAngle: Float, deltaT: Float) {
        val target = if (deckId == DeckId.DECK_A) deckA else deckB
        target.updateScratch(deltaAngle, deltaT)
    }

    fun endScratch(deckId: DeckId) {
        val target = if (deckId == DeckId.DECK_A) deckA else deckB
        target.endScratch()
    }

    // Loops
    fun toggleLoop(deckId: DeckId, beats: Float) {
        val target = if (deckId == DeckId.DECK_A) deckA else deckB
        target.toggleLoop(beats)
    }

    fun exitLoop(deckId: DeckId) {
        val target = if (deckId == DeckId.DECK_A) deckA else deckB
        target.exitLoop()
    }

    fun halveLoop(deckId: DeckId) {
        val target = if (deckId == DeckId.DECK_A) deckA else deckB
        target.halveLoop()
    }

    fun doubleLoop(deckId: DeckId) {
        val target = if (deckId == DeckId.DECK_A) deckA else deckB
        target.doubleLoop()
    }

    // Hot cues
    fun triggerHotCue(deckId: DeckId, index: Int) {
        val target = if (deckId == DeckId.DECK_A) deckA else deckB
        target.triggerHotCue(index)
    }

    fun deleteHotCue(deckId: DeckId, index: Int) {
        val target = if (deckId == DeckId.DECK_A) deckA else deckB
        target.deleteHotCue(index)
    }

    // Mixer
    fun setChannelGain(deckId: DeckId, gain: Float) {
        val target = if (deckId == DeckId.DECK_A) deckA else deckB
        target.trimGain = gain
    }

    fun setChannelEq(deckId: DeckId, low: Float, mid: Float, high: Float) {
        val target = if (deckId == DeckId.DECK_A) deckA else deckB
        target.eq.setGains(low, mid, high)
        if (deckId == DeckId.DECK_A) {
            mixer.state = mixer.state.copy(channelA = mixer.state.channelA.copy(low = low, mid = mid, high = high))
        } else {
            mixer.state = mixer.state.copy(channelB = mixer.state.channelB.copy(low = low, mid = mid, high = high))
        }
    }

    fun setChannelFilter(deckId: DeckId, filter: Float) {
        val target = if (deckId == DeckId.DECK_A) deckA else deckB
        target.filter.setFilterValue(filter)
        if (deckId == DeckId.DECK_A) {
            mixer.state = mixer.state.copy(channelA = mixer.state.channelA.copy(filter = filter))
        } else {
            mixer.state = mixer.state.copy(channelB = mixer.state.channelB.copy(filter = filter))
        }
    }

    fun setChannelFader(deckId: DeckId, fader: Float) {
        val target = if (deckId == DeckId.DECK_A) deckA else deckB
        target.channelFader = fader
        if (deckId == DeckId.DECK_A) {
            mixer.state = mixer.state.copy(channelA = mixer.state.channelA.copy(fader = fader))
        } else {
            mixer.state = mixer.state.copy(channelB = mixer.state.channelB.copy(fader = fader))
        }
    }

    fun setCrossfader(pos: Float) {
        mixer.setCrossfaderPosition(pos)
    }

    fun setCrossfaderCurve(curve: CrossfaderCurve) {
        mixer.setCrossfaderCurve(curve)
    }

    fun setCrossfaderAssign(deckId: DeckId, assign: CrossfaderAssign) {
        if (deckId == DeckId.DECK_A) {
            mixer.state = mixer.state.copy(channelA = mixer.state.channelA.copy(crossfaderAssign = assign))
        } else {
            mixer.state = mixer.state.copy(channelB = mixer.state.channelB.copy(crossfaderAssign = assign))
        }
    }

    fun setMasterGain(gain: Float) {
        mixer.setMasterGain(gain)
    }

    // FX Controls
    fun setFxEnabled(deckId: DeckId, enabled: Boolean) {
        val rack = if (deckId == DeckId.DECK_A) deckA.fxRack else deckB.fxRack
        rack.state = rack.state.copy(enabled = enabled)
    }

    fun setFxType(deckId: DeckId, type: FxType) {
        val rack = if (deckId == DeckId.DECK_A) deckA.fxRack else deckB.fxRack
        rack.state = rack.state.copy(type = type)
    }

    fun setFxDryWet(deckId: DeckId, dw: Float) {
        val rack = if (deckId == DeckId.DECK_A) deckA.fxRack else deckB.fxRack
        rack.state = rack.state.copy(dryWet = dw)
    }

    fun setFxParam1(deckId: DeckId, p1: Float) {
        val rack = if (deckId == DeckId.DECK_A) deckA.fxRack else deckB.fxRack
        rack.state = rack.state.copy(param1 = p1)
    }

    fun setFxParam2(deckId: DeckId, p2: Float) {
        val rack = if (deckId == DeckId.DECK_A) deckA.fxRack else deckB.fxRack
        rack.state = rack.state.copy(param2 = p2)
    }

    fun setFxParam3(deckId: DeckId, p3: Float) {
        val rack = if (deckId == DeckId.DECK_A) deckA.fxRack else deckB.fxRack
        rack.state = rack.state.copy(param3 = p3)
    }

    fun setFxBeatDivision(deckId: DeckId, div: BeatDivision) {
        val rack = if (deckId == DeckId.DECK_A) deckA.fxRack else deckB.fxRack
        rack.state = rack.state.copy(beatDivision = div)
    }

    // Mastering
    fun setMasteringPreset(preset: MasteringPreset) {
        mixer.masteringChain.applyPreset(preset)
    }

    fun toggleMasteringBypass(stage: String) {
        val b = mixer.masteringChain.state.bypass
        val updated = when (stage) {
            "trim" -> b.copy(inputTrimBypass = !b.inputTrimBypass)
            "dc" -> b.copy(dcProtectionBypass = !b.dcProtectionBypass)
            "eq" -> b.copy(parametricEqBypass = !b.parametricEqBypass)
            "dyneq" -> b.copy(dynamicEqBypass = !b.dynamicEqBypass)
            "comp" -> b.copy(busCompBypass = !b.busCompBypass)
            "sat" -> b.copy(saturationBypass = !b.saturationBypass)
            "stereo" -> b.copy(stereoProcessorBypass = !b.stereoProcessorBypass)
            "clip" -> b.copy(clipperBypass = !b.clipperBypass)
            "limiter" -> b.copy(truePeakLimiterBypass = !b.truePeakLimiterBypass)
            else -> b
        }
        mixer.masteringChain.state = mixer.masteringChain.state.copy(bypass = updated)
    }

    fun setLatencyProfile(profile: LatencyProfile) {
        outputEngine.setLatencyProfile(profile)
    }

    fun setPerformanceMode(mode: ThermalPerformanceMode) {
        outputEngine.performanceMode = mode
    }

    // Track loading
    fun loadTrack(deckId: DeckId, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val decoded = UniversalAudioDecoder.decodeUri(getApplication(), uri)
                val target = if (deckId == DeckId.DECK_A) deckA else deckB
                target.loadTrack(decoded.buffer, decoded.metadata)
            } catch (e: Exception) {
                // Error loading track
            }
        }
    }

    fun loadTrackEntity(deckId: DeckId, entity: TrackEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val target = if (deckId == DeckId.DECK_A) deckA else deckB
            if (entity.filePath.startsWith("asset://")) {
                val decoded = if (entity.filePath.contains("techno")) {
                    SampleTrackGenerator.generateDeckATrack()
                } else {
                    SampleTrackGenerator.generateDeckBTrack()
                }
                target.loadTrack(decoded.buffer, decoded.metadata)
            } else {
                val file = File(entity.filePath)
                if (file.exists()) {
                    val decoded = UniversalAudioDecoder.decodeFile(file)
                    target.loadTrack(decoded.buffer, decoded.metadata)
                }
            }
        }
    }

    fun startRecording(preMaster: Boolean = false, sampleRate: Int = 48000, bitDepth: Int = 24): File {
        val app = getApplication<Application>()
        val dir = app.getExternalFilesDir("Recordings") ?: app.filesDir
        dir.mkdirs()
        val file = File(dir, "OracleDJ_Mix_${System.currentTimeMillis()}.wav")
        outputEngine.startRecording(file, sampleRate, bitDepth, preMaster)
        isRecordingMaster = true
        return file
    }

    fun stopRecording(): File? {
        val file = outputEngine.stopRecording()
        isRecordingMaster = false
        return file
    }

    // Live Streaming API
    fun getStreamConfig(platform: StreamPlatform): LiveStreamConfig {
        return liveStreamBroadcaster.getSavedConfigForPlatform(platform)
    }

    fun startLiveStream(config: LiveStreamConfig) {
        liveStreamBroadcaster.startStream(config)
    }

    fun stopLiveStream() {
        liveStreamBroadcaster.stopStream()
    }

    fun toggleMicTalkover(enabled: Boolean) {
        liveStreamBroadcaster.toggleMicTalkover(enabled)
    }

    fun setLiveBitrate(kbps: Int) {
        liveStreamBroadcaster.setBitrate(kbps)
    }

    fun saveProject(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val project = ProjectEntity(
                name = name,
                deckATrackPath = deckA.metadata?.filePath ?: "",
                deckBTrackPath = deckB.metadata?.filePath ?: "",
                deckAPosSec = deckA.currentFramePosition / deckA.buffer!!.sampleRate,
                deckBPosSec = deckB.currentFramePosition / deckB.buffer!!.sampleRate,
                deckAPitch = deckA.tempoPercent,
                deckBPitch = deckB.tempoPercent,
                crossfaderPos = mixer.state.crossfaderPosition,
                masterGain = mixer.state.masterGain,
                masteringPreset = mixer.masteringChain.state.preset.name
            )
            repository.saveProject(project)
        }
    }

    fun refreshLibrary() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.scanDeviceMedia()
        }
    }

    override fun onCleared() {
        super.onCleared()
        liveStreamBroadcaster.stopStream()
        outputEngine.stop()
        deviceManager.release()
    }
}
