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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ui.theme.DjBorder
import com.example.ui.theme.DjGreenSync
import com.example.ui.theme.DjRedHot
import com.example.ui.theme.DjSurfaceCard
import com.example.ui.theme.DjTextSecondary

@Composable
fun RecordingDialog(
    isRecording: Boolean,
    onStartRecording: (sampleRate: Int, bitDepth: Int, preMaster: Boolean) -> Unit,
    onStopRecording: () -> Unit,
    onDismiss: () -> Unit
) {
    var selectedSr by remember { mutableIntStateOf(48000) }
    var selectedBits by remember { mutableIntStateOf(24) }
    var recordPreMaster by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(DjSurfaceCard)
                .border(1.dp, DjBorder, RoundedCornerShape(12.dp))
                .padding(16.dp)
                .testTag("recording_dialog")
        ) {
            Text(
                text = "HIGH-RESOLUTION MASTER MIX RECORDER",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black
            )

            Spacer(modifier = Modifier.height(10.dp))

            if (!isRecording) {
                // Sample Rate Options (44.1, 48, 96, 192 kHz)
                Text("EXPORT SAMPLE RATE", color = DjTextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(44100, 48000, 96000, 192000).forEach { sr ->
                        val isSelected = selectedSr == sr
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (isSelected) DjGreenSync.copy(alpha = 0.25f) else Color(0xFF1E2232))
                                .border(1.dp, if (isSelected) DjGreenSync else DjBorder, RoundedCornerShape(4.dp))
                                .clickable { selectedSr = sr }
                                .padding(vertical = 6.dp)
                                .testTag("rec_sr_$sr"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${sr / 1000} kHz",
                                color = if (isSelected) DjGreenSync else Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Bit Depth Options (16-bit, 24-bit, 32-bit float)
                Text("BIT DEPTH RESOLUTION", color = DjTextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(16, 24, 32).forEach { bits ->
                        val isSelected = selectedBits == bits
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (isSelected) DjGreenSync.copy(alpha = 0.25f) else Color(0xFF1E2232))
                                .border(1.dp, if (isSelected) DjGreenSync else DjBorder, RoundedCornerShape(4.dp))
                                .clickable { selectedBits = bits }
                                .padding(vertical = 6.dp)
                                .testTag("rec_bits_$bits"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (bits == 32) "32-bit Float" else "$bits-bit PCM",
                                color = if (isSelected) DjGreenSync else Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Bus Tap Selector: Mastered vs Pre-Master
                Text("MIX BUS TAP", color = DjTextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (!recordPreMaster) DjGreenSync.copy(alpha = 0.25f) else Color(0xFF1E2232))
                            .border(1.dp, if (!recordPreMaster) DjGreenSync else DjBorder, RoundedCornerShape(4.dp))
                            .clickable { recordPreMaster = false }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("MASTERED MIX", color = if (!recordPreMaster) DjGreenSync else Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (recordPreMaster) Color(0xFF00E5FF).copy(alpha = 0.25f) else Color(0xFF1E2232))
                            .border(1.dp, if (recordPreMaster) Color(0xFF00E5FF) else DjBorder, RoundedCornerShape(4.dp))
                            .clickable { recordPreMaster = true }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("PRE-MASTER BUS", color = if (recordPreMaster) Color(0xFF00E5FF) else Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF222638))
                            .clickable { onDismiss() }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("CANCEL", color = DjTextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(DjRedHot)
                            .clickable {
                                onStartRecording(selectedSr, selectedBits, recordPreMaster)
                                onDismiss()
                            }
                            .padding(vertical = 10.dp)
                            .testTag("btn_confirm_start_rec"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("START RECORDING", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black)
                    }
                }
            } else {
                // Currently recording view
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("RECORDING IN PROGRESS...", color = DjRedHot, fontSize = 13.sp, fontWeight = FontWeight.Black)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Lossless WAV stream written to storage in real-time.", color = DjTextSecondary, fontSize = 10.sp)

                    Spacer(modifier = Modifier.height(16.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(DjRedHot)
                            .clickable {
                                onStopRecording()
                                onDismiss()
                            }
                            .padding(vertical = 12.dp)
                            .testTag("btn_stop_recording"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("STOP & SAVE WAV EXPORT", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}
