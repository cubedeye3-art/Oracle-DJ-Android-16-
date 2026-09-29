package com.example.ui

import android.Manifest
import android.content.res.Configuration
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.model.DeckId
import com.example.audio.streaming.StreamPlatform
import com.example.audio.streaming.StreamState
import com.example.ui.components.DualWaveformView
import com.example.ui.components.FxRackSection
import com.example.ui.components.HardwareStatusSection
import com.example.ui.components.HotCuePadView
import com.example.ui.components.JogWheel
import com.example.ui.components.LiveStreamSection
import com.example.ui.components.LiveStreamingDialog
import com.example.ui.components.LoopControlView
import com.example.ui.components.MasteringSection
import com.example.ui.components.MixerControls
import com.example.ui.components.MusicLibrarySheet
import com.example.ui.components.RecordingDialog
import com.example.ui.components.TransportControls
import com.example.ui.theme.DeckAPrimary
import com.example.ui.theme.DeckBPrimary
import com.example.ui.theme.DjBackground
import com.example.ui.theme.DjBorder
import com.example.ui.theme.DjGreenSync
import com.example.ui.theme.DjRedHot
import com.example.ui.theme.DjSurface
import com.example.ui.theme.DjTextSecondary

enum class DjNavTab(val label: String) {
    DECKS("Decks"),
    MIXER("Mixer"),
    FX("FX Rack"),
    MASTERING("Mastering"),
    STREAM("Live"),
    LIBRARY("Library"),
    HARDWARE("Hardware")
}

@Composable
fun DjMainScreen(viewModel: DjViewModel) {
    val deckAState by viewModel.deckAState.collectAsState()
    val deckBState by viewModel.deckBState.collectAsState()
    val mixerState by viewModel.mixerState.collectAsState()
    val masteringState by viewModel.masteringState.collectAsState()
    val telemetry by viewModel.telemetry.collectAsState()
    val tracks by viewModel.tracks.collectAsState()
    val liveStreamMetrics by viewModel.liveStreamMetrics.collectAsState()
    val hasMediaPermission by viewModel.hasMediaPermission.collectAsState()
    val trackLoadError by viewModel.trackLoadError.collectAsState()
    val isLoadingTrack by viewModel.isLoadingTrack.collectAsState()
    val libraryAnalysisProgress by viewModel.libraryAnalysisProgress.collectAsState()

    var activeTab by remember { mutableStateOf(DjNavTab.DECKS) }
    var showRecordDialog by remember { mutableStateOf(false) }
    var showLiveStreamDialog by remember { mutableStateOf(false) }
    var activeDeckTabInPortrait by remember { mutableStateOf(DeckId.DECK_A) }
    var deckAPerfTab by remember { mutableStateOf(0) } // 0: Hot Cues, 1: Loops
    var deckBPerfTab by remember { mutableStateOf(0) } // 0: Hot Cues, 1: Loops

    BackHandler(enabled = activeTab != DjNavTab.DECKS) {
        activeTab = DjNavTab.DECKS
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.loadTrack(activeDeckTabInPortrait, uri)
        }
    }

    val mediaPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        viewModel.onMediaPermissionResult(isGranted)
    }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (!hasMediaPermission) {
            permissionLauncher.launch(mediaPermission)
        }
    }

    val config = LocalConfiguration.current
    val isLandscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(DjBackground)
            .statusBarsPadding()
            .navigationBarsPadding(),
        topBar = {
            // Header Bar: App Name, Recording Indicator, Dual Waveforms
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DjSurface)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "BEAT",
                            color = Color(0xFF00E5FF),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = "ENGINE",
                            color = Color(0xFFFF0055),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "PRO",
                            color = Color(0xFFFFD600),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "PRO DJ WORKSTATION",
                            color = DjTextSecondary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Save session
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF1E2232))
                                .border(1.dp, DjBorder, RoundedCornerShape(4.dp))
                                .clickable { viewModel.saveProject("Session_${System.currentTimeMillis()}") }
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                                .testTag("btn_save_session"),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Save, contentDescription = "Save", tint = Color.White, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("SAVE", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Live Stream button (YouTube, Facebook, X)
                        val isStreamLive = liveStreamMetrics.state == StreamState.LIVE || liveStreamMetrics.state == StreamState.CONNECTING || liveStreamMetrics.state == StreamState.RECONNECTING
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (isStreamLive) Color(liveStreamMetrics.platform.brandColorHex).copy(alpha = 0.85f) else Color(0xFF1E2232))
                                .border(1.dp, if (isStreamLive) Color.White else Color(0xFF00E5FF).copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                                .clickable { showLiveStreamDialog = true }
                                .padding(horizontal = 7.dp, vertical = 4.dp)
                                .testTag("btn_live_stream"),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Sensors,
                                    contentDescription = "Live Stream",
                                    tint = if (isStreamLive) (if (liveStreamMetrics.platform == StreamPlatform.X_TWITTER) Color.Black else Color.White) else Color(0xFF00E5FF),
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isStreamLive) "LIVE: ${liveStreamMetrics.platform.shortName.uppercase()}" else "STREAM",
                                    color = if (isStreamLive) (if (liveStreamMetrics.platform == StreamPlatform.X_TWITTER) Color.Black else Color.White) else Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }

                        // Record button
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (viewModel.isRecordingMaster) DjRedHot else Color(0xFF1E2232))
                                .border(1.dp, if (viewModel.isRecordingMaster) Color.White else DjRedHot, RoundedCornerShape(4.dp))
                                .clickable { showRecordDialog = true }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .testTag("btn_record_master"),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.FiberManualRecord, contentDescription = "Rec", tint = if (viewModel.isRecordingMaster) Color.White else DjRedHot, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (viewModel.isRecordingMaster) "REC ON" else "REC",
                                    color = if (viewModel.isRecordingMaster) Color.White else DjRedHot,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // High-resolution Dual Waveform View
                DualWaveformView(
                    deckA = deckAState,
                    deckB = deckBState,
                    isLandscape = isLandscape
                )
            }
        },
        bottomBar = {
            // Navigation Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DjSurface)
                    .border(1.dp, DjBorder)
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                DjNavTab.values().forEach { tab ->
                    val isSelected = activeTab == tab
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSelected) DjGreenSync.copy(alpha = 0.2f) else Color.Transparent)
                            .clickable { activeTab = tab }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                            .testTag("nav_tab_${tab.name.lowercase()}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tab.label,
                            color = if (isSelected) DjGreenSync else DjTextSecondary,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(DjBackground)
        ) {
            if (isLandscape && activeTab != DjNavTab.DECKS && activeTab != DjNavTab.MIXER) {
                // LANDSCAPE SECONDARY TABS: FX Rack, Mastering Chain, Music Library, Hardware Telemetry
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(6.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    when (activeTab) {
                        DjNavTab.FX -> {
                            FxRackSection(
                                fxStateA = viewModel.deckA.fxRack.state,
                                fxStateB = viewModel.deckB.fxRack.state,
                                onToggleFx = { d, en -> viewModel.setFxEnabled(d, en) },
                                onSelectFxType = { d, t -> viewModel.setFxType(d, t) },
                                onDryWetChange = { d, dw -> viewModel.setFxDryWet(d, dw) },
                                onParam1Change = { d, p -> viewModel.setFxParam1(d, p) },
                                onParam2Change = { d, p -> viewModel.setFxParam2(d, p) },
                                onParam3Change = { d, p -> viewModel.setFxParam3(d, p) },
                                onBeatDivisionChange = { d, div -> viewModel.setFxBeatDivision(d, div) }
                            )
                        }
                        DjNavTab.MASTERING -> {
                            MasteringSection(
                                masteringState = masteringState,
                                onSelectPreset = { viewModel.setMasteringPreset(it) },
                                onToggleBypass = { viewModel.toggleMasteringBypass(it) }
                            )
                        }
                        DjNavTab.STREAM -> {
                            LiveStreamSection(
                                metrics = liveStreamMetrics,
                                currentConfig = viewModel.getStreamConfig(liveStreamMetrics.platform),
                                onGetPlatformConfig = { viewModel.getStreamConfig(it) },
                                onStartStream = { viewModel.startLiveStream(it) },
                                onStopStream = { viewModel.stopLiveStream() },
                                onToggleMicTalkover = { viewModel.toggleMicTalkover(it) }
                            )
                        }
                        DjNavTab.LIBRARY -> {
                            MusicLibrarySheet(
                                tracks = tracks,
                                onLoadToDeck = { d, t ->
                                    viewModel.loadTrackEntity(d, t)
                                    activeDeckTabInPortrait = d
                                    activeTab = DjNavTab.DECKS
                                },
                                onImportFileClicked = { filePickerLauncher.launch("audio/*") },
                                onRescanClicked = {
                                    if (hasMediaPermission) {
                                        viewModel.refreshLibrary()
                                    } else {
                                        permissionLauncher.launch(mediaPermission)
                                    }
                                },
                                hasMediaPermission = hasMediaPermission,
                                onRequestPermission = { permissionLauncher.launch(mediaPermission) },
                                trackLoadError = trackLoadError,
                                onDismissError = { viewModel.clearTrackLoadError() },
                                isLoadingTrack = isLoadingTrack,
                                analysisProgress = libraryAnalysisProgress,
                                onScanAndAnalyseClicked = {
                                    if (hasMediaPermission) {
                                        viewModel.scanAndAnalyseLibrary()
                                    } else {
                                        permissionLauncher.launch(mediaPermission)
                                    }
                                },
                                onDeleteTrack = { viewModel.deleteTrack(it) }
                            )
                        }
                        DjNavTab.HARDWARE -> {
                            HardwareStatusSection(
                                telemetry = telemetry,
                                onSelectLatency = { viewModel.setLatencyProfile(it) },
                                onSelectPerformanceMode = { viewModel.setPerformanceMode(it) }
                            )
                        }
                        else -> {}
                    }
                }
            } else if (isLandscape) {
                // LANDSCAPE DUAL-DECK TURNTABLE WORKSTATION: Deck A (Left) - Mixer (Center) - Deck B (Right)
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Deck A (Left Panel)
                    Column(
                        modifier = Modifier
                            .weight(1.3f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Deck A Header with Track Info & Load Button
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF161824))
                                .border(1.dp, DeckAPrimary.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 5.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(DeckAPrimary)
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Text("DECK A", color = Color.Black, fontSize = 9.sp, fontWeight = FontWeight.Black)
                                }
                                Text(
                                    text = deckAState.metadata?.title ?: "No Track Loaded",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(DeckAPrimary.copy(alpha = 0.2f))
                                    .border(1.dp, DeckAPrimary, RoundedCornerShape(4.dp))
                                    .clickable {
                                        activeDeckTabInPortrait = DeckId.DECK_A
                                        activeTab = DjNavTab.LIBRARY
                                    }
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                                    .testTag("deck_a_load_btn"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("LOAD", color = DeckAPrimary, fontSize = 10.sp, fontWeight = FontWeight.Black)
                            }
                        }

                        JogWheel(
                            deckState = deckAState,
                            onStartScratch = { viewModel.startScratch(DeckId.DECK_A) },
                            onUpdateScratch = { dAngle, dt -> viewModel.updateScratch(DeckId.DECK_A, dAngle, dt) },
                            onEndScratch = { viewModel.endScratch(DeckId.DECK_A) },
                            onPitchBend = { viewModel.pitchBend(DeckId.DECK_A, it) },
                            modifier = Modifier.fillMaxWidth().height(160.dp)
                        )
                        TransportControls(
                            deckState = deckAState,
                            onPlayPause = { viewModel.togglePlayPause(DeckId.DECK_A) },
                            onCueDown = { viewModel.cuePress(DeckId.DECK_A, true) },
                            onCueUp = { viewModel.cuePress(DeckId.DECK_A, false) },
                            onSync = { viewModel.sync(DeckId.DECK_A) },
                            onTempoChange = { viewModel.setTempo(DeckId.DECK_A, it) },
                            onPitchRangeChange = { viewModel.setPitchRange(DeckId.DECK_A, it) },
                            onPitchBend = { viewModel.pitchBend(DeckId.DECK_A, it) },
                            onToggleKeyLock = { viewModel.toggleKeyLock(DeckId.DECK_A) },
                            onToggleSlip = { viewModel.toggleSlip(DeckId.DECK_A) },
                            onToggleReverse = { viewModel.toggleReverse(DeckId.DECK_A) },
                            onToggleQuantize = { viewModel.toggleQuantize(DeckId.DECK_A) }
                        )

                        // Deck A Performance Selector: Hot Cues vs Beat Loops
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (deckAPerfTab == 0) DeckAPrimary.copy(alpha = 0.25f) else Color(0xFF1A1D2A))
                                    .border(1.dp, if (deckAPerfTab == 0) DeckAPrimary else DjBorder, RoundedCornerShape(4.dp))
                                    .clickable { deckAPerfTab = 0 }
                                    .padding(vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("HOT CUES (8)", color = if (deckAPerfTab == 0) DeckAPrimary else DjTextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (deckAPerfTab == 1) DjGreenSync.copy(alpha = 0.25f) else Color(0xFF1A1D2A))
                                    .border(1.dp, if (deckAPerfTab == 1) DjGreenSync else DjBorder, RoundedCornerShape(4.dp))
                                    .clickable { deckAPerfTab = 1 }
                                    .padding(vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("BEAT LOOPS", color = if (deckAPerfTab == 1) DjGreenSync else DjTextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        if (deckAPerfTab == 0) {
                            HotCuePadView(
                                deckId = DeckId.DECK_A,
                                hotCues = deckAState.hotCues,
                                onTriggerCue = { viewModel.triggerHotCue(DeckId.DECK_A, it) },
                                onDeleteCue = { viewModel.deleteHotCue(DeckId.DECK_A, it) }
                            )
                        } else {
                            LoopControlView(
                                deckId = DeckId.DECK_A,
                                loopState = deckAState.loopState,
                                onToggleLoop = { viewModel.toggleLoop(DeckId.DECK_A, it) },
                                onExitLoop = { viewModel.exitLoop(DeckId.DECK_A) },
                                onHalveLoop = { viewModel.halveLoop(DeckId.DECK_A) },
                                onDoubleLoop = { viewModel.doubleLoop(DeckId.DECK_A) }
                            )
                        }
                    }

                    // Center 2-Channel Mixer
                    MixerControls(
                        mixerState = mixerState,
                        onGainChange = { d, g -> viewModel.setChannelGain(d, g) },
                        onEqChange = { d, l, m, h -> viewModel.setChannelEq(d, l, m, h) },
                        onFilterChange = { d, f -> viewModel.setChannelFilter(d, f) },
                        onFaderChange = { d, v -> viewModel.setChannelFader(d, v) },
                        onCrossfaderChange = { viewModel.setCrossfader(it) },
                        onCrossfaderCurveChange = { viewModel.setCrossfaderCurve(it) },
                        onCrossfaderAssignChange = { d, a -> viewModel.setCrossfaderAssign(d, a) },
                        onMasterGainChange = { viewModel.setMasterGain(it) },
                        onCueMonitorToggle = { viewModel.toggleCueMonitor(it) },
                        modifier = Modifier
                            .weight(1.4f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                    )

                    // Deck B (Right Panel)
                    Column(
                        modifier = Modifier
                            .weight(1.3f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Deck B Header with Track Info & Load Button
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF161824))
                                .border(1.dp, DeckBPrimary.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 5.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(DeckBPrimary)
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Text("DECK B", color = Color.Black, fontSize = 9.sp, fontWeight = FontWeight.Black)
                                }
                                Text(
                                    text = deckBState.metadata?.title ?: "No Track Loaded",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(DeckBPrimary.copy(alpha = 0.2f))
                                    .border(1.dp, DeckBPrimary, RoundedCornerShape(4.dp))
                                    .clickable {
                                        activeDeckTabInPortrait = DeckId.DECK_B
                                        activeTab = DjNavTab.LIBRARY
                                    }
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                                    .testTag("deck_b_load_btn"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("LOAD", color = DeckBPrimary, fontSize = 10.sp, fontWeight = FontWeight.Black)
                            }
                        }

                        JogWheel(
                            deckState = deckBState,
                            onStartScratch = { viewModel.startScratch(DeckId.DECK_B) },
                            onUpdateScratch = { dAngle, dt -> viewModel.updateScratch(DeckId.DECK_B, dAngle, dt) },
                            onEndScratch = { viewModel.endScratch(DeckId.DECK_B) },
                            onPitchBend = { viewModel.pitchBend(DeckId.DECK_B, it) },
                            modifier = Modifier.fillMaxWidth().height(160.dp)
                        )
                        TransportControls(
                            deckState = deckBState,
                            onPlayPause = { viewModel.togglePlayPause(DeckId.DECK_B) },
                            onCueDown = { viewModel.cuePress(DeckId.DECK_B, true) },
                            onCueUp = { viewModel.cuePress(DeckId.DECK_B, false) },
                            onSync = { viewModel.sync(DeckId.DECK_B) },
                            onTempoChange = { viewModel.setTempo(DeckId.DECK_B, it) },
                            onPitchRangeChange = { viewModel.setPitchRange(DeckId.DECK_B, it) },
                            onPitchBend = { viewModel.pitchBend(DeckId.DECK_B, it) },
                            onToggleKeyLock = { viewModel.toggleKeyLock(DeckId.DECK_B) },
                            onToggleSlip = { viewModel.toggleSlip(DeckId.DECK_B) },
                            onToggleReverse = { viewModel.toggleReverse(DeckId.DECK_B) },
                            onToggleQuantize = { viewModel.toggleQuantize(DeckId.DECK_B) }
                        )

                        // Deck B Performance Selector: Hot Cues vs Beat Loops
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (deckBPerfTab == 0) DeckBPrimary.copy(alpha = 0.25f) else Color(0xFF1A1D2A))
                                    .border(1.dp, if (deckBPerfTab == 0) DeckBPrimary else DjBorder, RoundedCornerShape(4.dp))
                                    .clickable { deckBPerfTab = 0 }
                                    .padding(vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("HOT CUES (8)", color = if (deckBPerfTab == 0) DeckBPrimary else DjTextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (deckBPerfTab == 1) DjGreenSync.copy(alpha = 0.25f) else Color(0xFF1A1D2A))
                                    .border(1.dp, if (deckBPerfTab == 1) DjGreenSync else DjBorder, RoundedCornerShape(4.dp))
                                    .clickable { deckBPerfTab = 1 }
                                    .padding(vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("BEAT LOOPS", color = if (deckBPerfTab == 1) DjGreenSync else DjTextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        if (deckBPerfTab == 0) {
                            HotCuePadView(
                                deckId = DeckId.DECK_B,
                                hotCues = deckBState.hotCues,
                                onTriggerCue = { viewModel.triggerHotCue(DeckId.DECK_B, it) },
                                onDeleteCue = { viewModel.deleteHotCue(DeckId.DECK_B, it) }
                            )
                        } else {
                            LoopControlView(
                                deckId = DeckId.DECK_B,
                                loopState = deckBState.loopState,
                                onToggleLoop = { viewModel.toggleLoop(DeckId.DECK_B, it) },
                                onExitLoop = { viewModel.exitLoop(DeckId.DECK_B) },
                                onHalveLoop = { viewModel.halveLoop(DeckId.DECK_B) },
                                onDoubleLoop = { viewModel.doubleLoop(DeckId.DECK_B) }
                            )
                        }
                    }
                }
            } else {
                // PORTRAIT MODE: Tabbed Navigation for High Touch Precision
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(6.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    when (activeTab) {
                        DjNavTab.DECKS -> {
                            // Deck A / Deck B Switcher in Decks tab
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (activeDeckTabInPortrait == DeckId.DECK_A) DeckAPrimary.copy(alpha = 0.25f) else Color(0xFF1E2232))
                                        .border(2.dp, if (activeDeckTabInPortrait == DeckId.DECK_A) DeckAPrimary else DjBorder, RoundedCornerShape(6.dp))
                                        .clickable { activeDeckTabInPortrait = DeckId.DECK_A }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "DECK A (${deckAState.metadata?.title ?: "Empty"})",
                                        color = if (activeDeckTabInPortrait == DeckId.DECK_A) DeckAPrimary else DjTextSecondary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Black,
                                        maxLines = 1
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (activeDeckTabInPortrait == DeckId.DECK_B) DeckBPrimary.copy(alpha = 0.25f) else Color(0xFF1E2232))
                                        .border(2.dp, if (activeDeckTabInPortrait == DeckId.DECK_B) DeckBPrimary else DjBorder, RoundedCornerShape(6.dp))
                                        .clickable { activeDeckTabInPortrait = DeckId.DECK_B }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "DECK B (${deckBState.metadata?.title ?: "Empty"})",
                                        color = if (activeDeckTabInPortrait == DeckId.DECK_B) DeckBPrimary else DjTextSecondary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Black,
                                        maxLines = 1
                                    )
                                }
                            }

                            val currentDeck = if (activeDeckTabInPortrait == DeckId.DECK_A) deckAState else deckBState
                            val dId = activeDeckTabInPortrait

                            // Big Responsive JogWheel
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                JogWheel(
                                    deckState = currentDeck,
                                    onStartScratch = { viewModel.startScratch(dId) },
                                    onUpdateScratch = { dAngle, dt -> viewModel.updateScratch(dId, dAngle, dt) },
                                    onEndScratch = { viewModel.endScratch(dId) },
                                    onPitchBend = { viewModel.pitchBend(dId, it) },
                                    modifier = Modifier.size(240.dp)
                                )
                            }

                            // Big Transport Controls
                            TransportControls(
                                deckState = currentDeck,
                                onPlayPause = { viewModel.togglePlayPause(dId) },
                                onCueDown = { viewModel.cuePress(dId, true) },
                                onCueUp = { viewModel.cuePress(dId, false) },
                                onSync = { viewModel.sync(dId) },
                                onTempoChange = { viewModel.setTempo(dId, it) },
                                onPitchRangeChange = { viewModel.setPitchRange(dId, it) },
                                onPitchBend = { viewModel.pitchBend(dId, it) },
                                onToggleKeyLock = { viewModel.toggleKeyLock(dId) },
                                onToggleSlip = { viewModel.toggleSlip(dId) },
                                onToggleReverse = { viewModel.toggleReverse(dId) },
                                onToggleQuantize = { viewModel.toggleQuantize(dId) }
                            )

                            // 8 Hot Cues Pads
                            HotCuePadView(
                                deckId = dId,
                                hotCues = currentDeck.hotCues,
                                onTriggerCue = { viewModel.triggerHotCue(dId, it) },
                                onDeleteCue = { viewModel.deleteHotCue(dId, it) }
                            )

                            // Loop Section
                            LoopControlView(
                                deckId = dId,
                                loopState = currentDeck.loopState,
                                onToggleLoop = { viewModel.toggleLoop(dId, it) },
                                onExitLoop = { viewModel.exitLoop(dId) },
                                onHalveLoop = { viewModel.halveLoop(dId) },
                                onDoubleLoop = { viewModel.doubleLoop(dId) }
                            )
                        }

                        DjNavTab.MIXER -> {
                            MixerControls(
                                mixerState = mixerState,
                                onGainChange = { d, g -> viewModel.setChannelGain(d, g) },
                                onEqChange = { d, l, m, h -> viewModel.setChannelEq(d, l, m, h) },
                                onFilterChange = { d, f -> viewModel.setChannelFilter(d, f) },
                                onFaderChange = { d, v -> viewModel.setChannelFader(d, v) },
                                onCrossfaderChange = { viewModel.setCrossfader(it) },
                                onCrossfaderCurveChange = { viewModel.setCrossfaderCurve(it) },
                                onCrossfaderAssignChange = { d, a -> viewModel.setCrossfaderAssign(d, a) },
                                onMasterGainChange = { viewModel.setMasterGain(it) },
                                onCueMonitorToggle = { viewModel.toggleCueMonitor(it) }
                            )
                        }

                        DjNavTab.FX -> {
                            FxRackSection(
                                fxStateA = viewModel.deckA.fxRack.state,
                                fxStateB = viewModel.deckB.fxRack.state,
                                onToggleFx = { d, en -> viewModel.setFxEnabled(d, en) },
                                onSelectFxType = { d, t -> viewModel.setFxType(d, t) },
                                onDryWetChange = { d, dw -> viewModel.setFxDryWet(d, dw) },
                                onParam1Change = { d, p -> viewModel.setFxParam1(d, p) },
                                onParam2Change = { d, p -> viewModel.setFxParam2(d, p) },
                                onParam3Change = { d, p -> viewModel.setFxParam3(d, p) },
                                onBeatDivisionChange = { d, div -> viewModel.setFxBeatDivision(d, div) }
                            )
                        }

                        DjNavTab.MASTERING -> {
                            MasteringSection(
                                masteringState = masteringState,
                                onSelectPreset = { viewModel.setMasteringPreset(it) },
                                onToggleBypass = { viewModel.toggleMasteringBypass(it) }
                            )
                        }

                        DjNavTab.STREAM -> {
                            LiveStreamSection(
                                metrics = liveStreamMetrics,
                                currentConfig = viewModel.getStreamConfig(liveStreamMetrics.platform),
                                onGetPlatformConfig = { viewModel.getStreamConfig(it) },
                                onStartStream = { viewModel.startLiveStream(it) },
                                onStopStream = { viewModel.stopLiveStream() },
                                onToggleMicTalkover = { viewModel.toggleMicTalkover(it) }
                            )
                        }

                        DjNavTab.LIBRARY -> {
                            MusicLibrarySheet(
                                tracks = tracks,
                                onLoadToDeck = { d, t ->
                                    viewModel.loadTrackEntity(d, t)
                                    activeDeckTabInPortrait = d
                                    activeTab = DjNavTab.DECKS
                                },
                                onImportFileClicked = { filePickerLauncher.launch("audio/*") },
                                onRescanClicked = {
                                    if (hasMediaPermission) {
                                        viewModel.refreshLibrary()
                                    } else {
                                        permissionLauncher.launch(mediaPermission)
                                    }
                                },
                                hasMediaPermission = hasMediaPermission,
                                onRequestPermission = { permissionLauncher.launch(mediaPermission) },
                                trackLoadError = trackLoadError,
                                onDismissError = { viewModel.clearTrackLoadError() },
                                isLoadingTrack = isLoadingTrack,
                                analysisProgress = libraryAnalysisProgress,
                                onScanAndAnalyseClicked = {
                                    if (hasMediaPermission) {
                                        viewModel.scanAndAnalyseLibrary()
                                    } else {
                                        permissionLauncher.launch(mediaPermission)
                                    }
                                },
                                onDeleteTrack = { viewModel.deleteTrack(it) }
                            )
                        }

                        DjNavTab.HARDWARE -> {
                            HardwareStatusSection(
                                telemetry = telemetry,
                                onSelectLatency = { viewModel.setLatencyProfile(it) },
                                onSelectPerformanceMode = { viewModel.setPerformanceMode(it) }
                            )
                        }
                    }
                }
            }

            // Recording Dialog
            if (showRecordDialog) {
                RecordingDialog(
                    isRecording = viewModel.isRecordingMaster,
                    onStartRecording = { sr, bits, preMaster ->
                        viewModel.startRecording(preMaster, sr, bits)
                    },
                    onStopRecording = {
                        viewModel.stopRecording()
                    },
                    onDismiss = { showRecordDialog = false }
                )
            }

            // Live Streaming Dialog (YouTube, Facebook, X)
            if (showLiveStreamDialog) {
                LiveStreamingDialog(
                    metrics = liveStreamMetrics,
                    currentConfig = viewModel.getStreamConfig(liveStreamMetrics.platform),
                    onGetPlatformConfig = { viewModel.getStreamConfig(it) },
                    onStartStream = { viewModel.startLiveStream(it) },
                    onStopStream = { viewModel.stopLiveStream() },
                    onToggleMicTalkover = { viewModel.toggleMicTalkover(it) },
                    onDismiss = { showLiveStreamDialog = false }
                )
            }
        }
    }
}
