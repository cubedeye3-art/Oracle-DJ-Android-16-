package com.example.ui.components

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
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
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
import com.example.audio.model.DeckId
import com.example.data.TrackEntity
import com.example.ui.theme.DeckAPrimary
import com.example.ui.theme.DeckBPrimary
import com.example.ui.theme.DjBorder
import com.example.ui.theme.DjGreenSync
import com.example.ui.theme.DjSurfaceCard
import com.example.ui.theme.DjTextPrimary
import com.example.ui.theme.DjTextSecondary

@Composable
fun MusicLibrarySheet(
    tracks: List<TrackEntity>,
    onLoadToDeck: (DeckId, TrackEntity) -> Unit,
    onImportFileClicked: () -> Unit,
    onRescanClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredTracks = remember(tracks, searchQuery) {
        if (searchQuery.isBlank()) tracks
        else tracks.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
            it.artist.contains(searchQuery, ignoreCase = true) ||
            it.musicalKey.contains(searchQuery, ignoreCase = true)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(DjSurfaceCard)
            .border(1.dp, DjBorder, RoundedCornerShape(8.dp))
            .padding(10.dp)
            .testTag("music_library_section")
    ) {
        // Top Toolbar: Search + Import + Rescan
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "OFFLINE MUSIC LIBRARY",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black
            )

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
                        Icon(Icons.Default.FileOpen, contentDescription = "Import", tint = DjGreenSync, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("IMPORT", color = DjGreenSync, fontSize = 10.sp, fontWeight = FontWeight.Bold)
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
                        Icon(Icons.Default.Refresh, contentDescription = "Rescan", tint = Color.White, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("RESCAN", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Search Input
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search by title, artist, key, or BPM...", color = DjTextSecondary, fontSize = 11.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = DjTextSecondary, modifier = Modifier.size(16.dp)) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .testTag("library_search_input"),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = DjGreenSync,
                unfocusedBorderColor = DjBorder,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            )
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Tracks List
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(filteredTracks, key = { it.id }) { track ->
                TrackListItem(
                    track = track,
                    onLoadDeckA = { onLoadToDeck(DeckId.DECK_A, track) },
                    onLoadDeckB = { onLoadToDeck(DeckId.DECK_B, track) }
                )
            }
        }
    }
}

@Composable
private fun TrackListItem(
    track: TrackEntity,
    onLoadDeckA: () -> Unit,
    onLoadDeckB: () -> Unit
) {
    val durationSec = track.durationSeconds.toInt()
    val durStr = String.format("%02d:%02d", durationSec / 60, durationSec % 60)

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
                Text("${String.format("%.1f", track.bpm)} BPM", color = DjGreenSync, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text("•", color = DjTextSecondary, fontSize = 8.sp)
                Text(track.musicalKey, color = DeckAPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text("•", color = DjTextSecondary, fontSize = 8.sp)
                Text(durStr, color = DjTextSecondary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            }
            Text(
                text = "${track.formatName} • ${track.sampleRate / 1000}kHz / ${track.bitDepth}-bit",
                color = Color(0xFFFFD600),
                fontSize = 9.sp
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

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
