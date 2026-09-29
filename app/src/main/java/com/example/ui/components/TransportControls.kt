package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.model.DeckId
import com.example.audio.model.DeckUiState
import com.example.audio.model.PitchRange
import com.example.audio.model.PlayState
import com.example.ui.theme.DeckAPrimary
import com.example.ui.theme.DeckBPrimary
import com.example.ui.theme.DjBackground
import com.example.ui.theme.DjBorder
import com.example.ui.theme.DjGreenSync
import com.example.ui.theme.DjRedHot
import com.example.ui.theme.DjSurfaceCard
import com.example.ui.theme.DjTextPrimary
import com.example.ui.theme.DjTextSecondary

@Composable
fun TransportControls(
    deckState: DeckUiState,
    onPlayPause: () -> Unit,
    onCueDown: () -> Unit,
    onCueUp: () -> Unit,
    onSync: () -> Unit,
    onTempoChange: (Float) -> Unit,
    onPitchRangeChange: (PitchRange) -> Unit,
    onPitchBend: (Float) -> Unit,
    onToggleKeyLock: () -> Unit,
    onToggleSlip: () -> Unit,
    onToggleReverse: () -> Unit,
    onToggleQuantize: () -> Unit,
    modifier: Modifier = Modifier
) {
    val themeColor = if (deckState.deckId == DeckId.DECK_A) DeckAPrimary else DeckBPrimary
    val isPlaying = deckState.playState == PlayState.PLAYING
    val isCueing = deckState.playState == PlayState.CUE_AUDITION
    val deckName = deckState.deckId.name.lowercase()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(DjSurfaceCard)
            .border(1.dp, DjBorder, RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        // Top HUD: BPM, Key, Time Elapsed / Remaining
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = if (deckState.currentBpm > 0.0) String.format("%.1f", deckState.currentBpm) else "--.-",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = if (deckState.currentBpm > 0.0) "BPM (${String.format("%+.1f%%", deckState.tempoPercent * deckState.pitchRange.maxPercent * 100)})" else "NO BPM",
                    color = DjTextSecondary,
                    fontSize = 10.sp
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (deckState.musicalKey.isNotBlank()) deckState.musicalKey else "--",
                    color = themeColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "KEY LOCK: ${if (deckState.keyLock) "ON" else "OFF"}",
                    color = if (deckState.keyLock) DjGreenSync else DjTextSecondary,
                    fontSize = 9.sp
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                val elapsedSec = deckState.currentPositionSeconds.toInt()
                val remainSec = (deckState.durationSeconds - deckState.currentPositionSeconds).toInt().coerceAtLeast(0)
                Text(
                    text = String.format("%02d:%02d", elapsedSec / 60, elapsedSec % 60),
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = String.format("-%02d:%02d", remainSec / 60, remainSec % 60),
                    color = DjTextSecondary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Middle Row: Modes (KeyLock, Slip, Reverse, Quantize, Pitch Range)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ModeToggleButton(
                label = "MASTER TEMPO",
                active = deckState.keyLock,
                activeColor = themeColor,
                onClick = onToggleKeyLock,
                modifier = Modifier.weight(1f).testTag("${deckName}_keylock_btn")
            )
            ModeToggleButton(
                label = "SLIP",
                active = deckState.slipMode,
                activeColor = DjGreenSync,
                onClick = onToggleSlip,
                modifier = Modifier.weight(1f).testTag("${deckName}_slip_btn")
            )
            ModeToggleButton(
                label = "REVERSE",
                active = deckState.reverse,
                activeColor = DjRedHot,
                onClick = onToggleReverse,
                modifier = Modifier.weight(1f).testTag("${deckName}_reverse_btn")
            )
            ModeToggleButton(
                label = "QUANTIZE",
                active = deckState.quantize,
                activeColor = DjGreenSync,
                onClick = onToggleQuantize,
                modifier = Modifier.weight(1f).testTag("${deckName}_quantize_btn")
            )
            // Pitch Range Toggle
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF222738))
                    .clickable {
                        val nextRange = when (deckState.pitchRange) {
                            PitchRange.FOUR -> PitchRange.EIGHT
                            PitchRange.EIGHT -> PitchRange.SIXTEEN
                            PitchRange.SIXTEEN -> PitchRange.FIFTY
                            PitchRange.FIFTY -> PitchRange.HUNDRED
                            PitchRange.HUNDRED -> PitchRange.FOUR
                        }
                        onPitchRangeChange(nextRange)
                    }
                    .padding(horizontal = 6.dp, vertical = 6.dp)
                    .testTag("${deckName}_pitch_range_btn"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = deckState.pitchRange.formatLabel(),
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Pitch slider and nudging
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Nudge -
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF252A3C))
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                onPitchBend(-0.15f)
                                tryAwaitRelease()
                                onPitchBend(0f)
                            }
                        )
                    }
                    .testTag("${deckName}_nudge_minus"),
                contentAlignment = Alignment.Center
            ) {
                Text("-", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }

            Slider(
                value = deckState.tempoPercent,
                onValueChange = onTempoChange,
                valueRange = -1.0f..1.0f,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
                    .testTag("${deckName}_tempo_slider"),
                colors = SliderDefaults.colors(
                    thumbColor = themeColor,
                    activeTrackColor = themeColor,
                    inactiveTrackColor = DjBorder
                )
            )

            // Nudge +
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF252A3C))
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                onPitchBend(0.15f)
                                tryAwaitRelease()
                                onPitchBend(0f)
                            }
                        )
                    }
                    .testTag("${deckName}_nudge_plus"),
                contentAlignment = Alignment.Center
            ) {
                Text("+", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Big Primary Hardware Buttons: PLAY/PAUSE, CUE, SYNC
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // PLAY / PAUSE Button (Big Circle)
            Box(
                modifier = Modifier
                    .weight(1.3f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isPlaying) DjGreenSync else Color(0xFF1E2232))
                    .border(2.dp, if (isPlaying) Color.White else DjGreenSync, RoundedCornerShape(8.dp))
                    .clickable { onPlayPause() }
                    .testTag("${deckName}_play_pause_btn"),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play Pause",
                        tint = if (isPlaying) DjBackground else DjGreenSync,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isPlaying) "PAUSE" else "PLAY",
                        color = if (isPlaying) DjBackground else DjGreenSync,
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp
                    )
                }
            }

            // CUE Button
            Box(
                modifier = Modifier
                    .weight(1.2f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isCueing) Color(0xFFFF9100) else Color(0xFF1E2232))
                    .border(2.dp, Color(0xFFFF9100), RoundedCornerShape(8.dp))
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                onCueDown()
                                tryAwaitRelease()
                                onCueUp()
                            }
                        )
                    }
                    .testTag("${deckName}_cue_btn"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "CUE",
                    color = if (isCueing) DjBackground else Color(0xFFFF9100),
                    fontWeight = FontWeight.Black,
                    fontSize = 13.sp
                )
            }

            // SYNC Button
            Box(
                modifier = Modifier
                    .weight(1.0f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1E2232))
                    .border(1.dp, DjGreenSync, RoundedCornerShape(8.dp))
                    .clickable { onSync() }
                    .testTag("${deckName}_sync_btn"),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = "Sync",
                        tint = DjGreenSync,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "SYNC",
                        color = DjGreenSync,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
fun ModeToggleButton(
    label: String,
    active: Boolean,
    activeColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(if (active) activeColor.copy(alpha = 0.2f) else Color(0xFF1E2232))
            .border(1.dp, if (active) activeColor else DjBorder, RoundedCornerShape(4.dp))
            .clickable { onClick() }
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (active) activeColor else DjTextSecondary,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}
