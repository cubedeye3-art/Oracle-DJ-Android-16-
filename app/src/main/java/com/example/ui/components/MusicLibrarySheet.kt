package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.decoder.TrackLoadError
import com.example.audio.model.DeckId
import com.example.data.LibraryAnalysisProgress
import com.example.data.TrackEntity
import com.example.ui.theme.DeckAPrimary
import com.example.ui.theme.DeckBPrimary
import com.example.ui.theme.DjBorder
import com.example.ui.theme.DjGreenSync
import com.example.ui.theme.DjRedHot
import com.example.ui.theme.DjSurfaceCard
import com.example.ui.theme.DjTextSecondary

@Composable
fun MusicLibrarySheet(
    tracks: List<TrackEntity>,
    onLoadToDeck: (DeckId, TrackEntity) -> Unit,
    onImportFileClicked: () -> Unit,
    onRescanClicked: () -> Unit,
    hasMediaPermission: Boolean = true,
    onRequestPermission: () -> Unit = {},
    trackLoadError: TrackLoadError? = null,
    onDismissError: () -> Unit = {},
    isLoadingTrack: Boolean = false,
    analysisProgress: LibraryAnalysisProgress = LibraryAnalysisProgress(),
    onScanAndAnalyseClicked: () -> Unit = {},
    onDeleteTrack: ((Long) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredTracks = remember(tracks, searchQuery) {
        if (searchQuery.isBlank()) tracks
        else tracks.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
            it.artist.contains(searchQuery, ignoreCase = true) ||
            it.album.contains(searchQuery, ignoreCase = true) ||
            it.musicalKey.contains(searchQuery, ignoreCase = true) ||
            it.formatName.contains(searchQuery, ignoreCase = true)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(DjSurfaceCard)
            .border(1.dp, DjBorder, RoundedCornerShape(8.dp))
            .padding(10.dp)
            .testTag("music_library_section"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Top Toolbar: Header + Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "BEATENGINE MUSIC LIBRARY",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black
                )
                if (isLoadingTrack) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        color = DjGreenSync,
                        strokeWidth = 2.dp
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                // Dedicated SCAN & ANALYSE LIBRARY button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (analysisProgress.isRunning) Color(0xFF382A12) else Color(0xFF281C38))
                        .border(1.dp, if (analysisProgress.isRunning) Color(0xFFFFD600) else Color(0xFFD500F9), RoundedCornerShape(4.dp))
                        .clickable(enabled = !analysisProgress.isRunning) { onScanAndAnalyseClicked() }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                        .testTag("btn_scan_and_analyse"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Analytics,
                            contentDescription = "Scan & Analyse",
                            tint = if (analysisProgress.isRunning) Color(0xFFFFD600) else Color(0xFFD500F9),
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = if (analysisProgress.isRunning) "ANALYSING..." else "SCAN & ANALYSE",
                            color = if (analysisProgress.isRunning) Color(0xFFFFD600) else Color(0xFFD500F9),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }

                // Import file button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF232838))
                        .border(1.dp, DjBorder, RoundedCornerShape(4.dp))
                        .clickable { onImportFileClicked() }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                        .testTag("btn_import_file"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.FileOpen,
                            contentDescription = "Import Audio File",
                            tint = DjGreenSync,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("IMPORT", color = DjGreenSync, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Rescan button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF232838))
                        .border(1.dp, DjBorder, RoundedCornerShape(4.dp))
                        .clickable { onRescanClicked() }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                        .testTag("btn_rescan_media"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Rescan MediaStore",
                            tint = Color.White,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("RESCAN", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Dedicated Library Analysis Progress HUD Card
        AnimatedVisibility(visible = analysisProgress.isRunning || analysisProgress.progressPercent >= 1.0f && analysisProgress.analysedCount > 0) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF141324))
                    .border(1.dp, Color(0xFF6200EA), RoundedCornerShape(6.dp))
                    .padding(8.dp)
                    .testTag("library_analysis_hud")
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (analysisProgress.isRunning) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(12.dp),
                                    color = Color(0xFFD500F9),
                                    strokeWidth = 2.dp
                                )
                            }
                            Text(
                                text = "LIBRARY BPM & KEY ANALYSIS ENGINE",
                                color = Color(0xFFD500F9),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                        Text(
                            text = "${(analysisProgress.progressPercent * 100).toInt()}%",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    LinearProgressIndicator(
                        progress = { analysisProgress.progressPercent.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = Color(0xFFD500F9),
                        trackColor = Color(0xFF281C38)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Tracks: ${analysisProgress.totalTracks} | Scanned: ${analysisProgress.scannedCount} | Analysed: ${analysisProgress.analysedCount} | Cached: ${analysisProgress.cachedCount} | Failed: ${analysisProgress.failedCount}",
                            color = DjTextSecondary,
                            fontSize = 8.5.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    if (analysisProgress.currentTrackTitle.isNotBlank()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Current: ${analysisProgress.currentTrackTitle}",
                                color = Color.White,
                                fontSize = 9.sp,
                                maxLines = 1,
                                modifier = Modifier.weight(1f)
                            )
                            if (analysisProgress.currentBpm > 0.0) {
                                Text(
                                    text = "${String.format("%.1f", analysisProgress.currentBpm)} BPM • ${analysisProgress.currentKey}",
                                    color = DjGreenSync,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // Active Error Banner
        AnimatedVisibility(visible = trackLoadError != null) {
            trackLoadError?.let { err ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(DjRedHot.copy(alpha = 0.15f))
                        .border(1.dp, DjRedHot, RoundedCornerShape(6.dp))
                        .padding(8.dp)
                        .testTag("track_load_error_banner")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                Icons.Default.ErrorOutline,
                                contentDescription = "Error",
                                tint = DjRedHot,
                                modifier = Modifier.size(16.dp)
                            )
                            Column {
                                Text(
                                    text = "[${err.code.name}] ${err.code.userMessage}",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = err.technicalMessage,
                                    color = Color(0xFFFFB4B4),
                                    fontSize = 9.sp,
                                    lineHeight = 12.sp
                                )
                            }
                        }
                        IconButton(
                            onClick = onDismissError,
                            modifier = Modifier
                                .size(24.dp)
                                .testTag("btn_dismiss_track_error")
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Dismiss Error",
                                tint = DjTextSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }

        // MediaStore Permission Card if not granted
        if (!hasMediaPermission) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF2C2210))
                    .border(1.dp, Color(0xFFFF9100), RoundedCornerShape(6.dp))
                    .padding(8.dp)
                    .testTag("permission_notice_card")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = "Permission Required",
                            tint = Color(0xFFFF9100),
                            modifier = Modifier.size(16.dp)
                        )
                        Column {
                            Text(
                                text = "AUDIO ACCESS PERMISSION REQUIRED",
                                color = Color(0xFFFF9100),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                text = "Grant permission to discover local MP3, WAV & AAC tracks.",
                                color = DjTextSecondary,
                                fontSize = 9.sp
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFFFF9100))
                            .clickable { onRequestPermission() }
                            .padding(horizontal = 8.dp, vertical = 5.dp)
                            .testTag("btn_grant_audio_permission"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "GRANT ACCESS",
                            color = Color.Black,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }
        }

        // Search Input
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search by title, artist, key, or format...", color = DjTextSecondary, fontSize = 11.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = DjTextSecondary, modifier = Modifier.size(16.dp)) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .testTag("library_search_input"),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = DjGreenSync,
                unfocusedBorderColor = DjBorder,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedContainerColor = Color(0xFF141724),
                unfocusedContainerColor = Color(0xFF141724)
            ),
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp)
        )

        // Tracks List
        if (filteredTracks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = if (searchQuery.isNotBlank()) "No tracks matching \"$searchQuery\"" else "No local tracks found",
                        color = DjTextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Use IMPORT or SCAN & ANALYSE to load and process audio files.",
                        color = DjTextSecondary.copy(alpha = 0.7f),
                        fontSize = 9.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(filteredTracks, key = { it.id }) { track ->
                    TrackListItem(
                        track = track,
                        onLoadDeckA = { onLoadToDeck(DeckId.DECK_A, track) },
                        onLoadDeckB = { onLoadToDeck(DeckId.DECK_B, track) },
                        onDelete = if (!track.filePath.startsWith("asset://") && onDeleteTrack != null) {
                            { onDeleteTrack(track.id) }
                        } else null
                    )
                }
            }
        }
    }
}

@Composable
private fun TrackListItem(
    track: TrackEntity,
    onLoadDeckA: () -> Unit,
    onLoadDeckB: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val durationSec = track.durationSeconds.toInt()
    val durStr = String.format("%02d:%02d", durationSec / 60, durationSec % 60)

    val bpmText = if (track.bpm > 0.0) {
        val confPct = (track.bpmConfidence * 100).toInt()
        "${String.format("%.1f", track.bpm)} BPM" + if (confPct > 0) " ($confPct%)" else ""
    } else {
        "-- BPM"
    }

    val keyText = if (track.musicalKey.isNotBlank()) track.musicalKey else "--"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF161824))
            .border(1.dp, Color(0xFF222638), RoundedCornerShape(6.dp))
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Track Information
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(track.artist, color = DjTextSecondary, fontSize = 10.sp, maxLines = 1)
                Text("•", color = DjTextSecondary, fontSize = 8.sp)
                Text(bpmText, color = if (track.bpm > 0) DjGreenSync else DjTextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text("•", color = DjTextSecondary, fontSize = 8.sp)
                Text(keyText, color = DeckAPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text("•", color = DjTextSecondary, fontSize = 8.sp)
                Text(durStr, color = DjTextSecondary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            }
            Text(
                text = "${track.formatName} • ${track.sampleRate / 1000}kHz / ${track.bitDepth}-bit",
                color = Color(0xFFFFD600),
                fontSize = 9.sp
            )
        }

        Spacer(modifier = Modifier.width(6.dp))

        // Delete button for custom imported tracks
        if (onDelete != null) {
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Remove Track",
                    tint = DjTextSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
        }

        // Load Buttons: A (Cyan) and B (Amber)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(DeckAPrimary.copy(alpha = 0.2f))
                    .border(1.dp, DeckAPrimary, RoundedCornerShape(4.dp))
                    .clickable { onLoadDeckA() }
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                    .testTag("btn_load_deck_a_${track.id}"),
                contentAlignment = Alignment.Center
            ) {
                Text("LOAD A", color = DeckAPrimary, fontSize = 10.sp, fontWeight = FontWeight.Black)
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(DeckBPrimary.copy(alpha = 0.2f))
                    .border(1.dp, DeckBPrimary, RoundedCornerShape(4.dp))
                    .clickable { onLoadDeckB() }
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                    .testTag("btn_load_deck_b_${track.id}"),
                contentAlignment = Alignment.Center
            ) {
                Text("LOAD B", color = DeckBPrimary, fontSize = 10.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}
