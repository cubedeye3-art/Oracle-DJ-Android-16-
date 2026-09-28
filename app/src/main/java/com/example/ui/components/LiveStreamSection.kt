package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.SensorsOff
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.streaming.AVAILABLE_BITRATES
import com.example.audio.streaming.LiveStreamConfig
import com.example.audio.streaming.LiveStreamMetrics
import com.example.audio.streaming.StreamPlatform
import com.example.audio.streaming.StreamState
import com.example.ui.theme.DjBorder
import com.example.ui.theme.DjGreenSync
import com.example.ui.theme.DjRedHot
import com.example.ui.theme.DjSurfaceCard
import com.example.ui.theme.DjSurfaceElevated
import com.example.ui.theme.DjTextSecondary

@Composable
fun LiveStreamSection(
    metrics: LiveStreamMetrics,
    currentConfig: LiveStreamConfig,
    onGetPlatformConfig: (StreamPlatform) -> LiveStreamConfig,
    onStartStream: (LiveStreamConfig) -> Unit,
    onStopStream: () -> Unit,
    onToggleMicTalkover: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedPlatform by remember { mutableStateOf(metrics.platform) }
    var ingestUrl by remember { mutableStateOf(currentConfig.ingestUrl) }
    var streamKey by remember { mutableStateOf(currentConfig.streamKey) }
    var streamTitle by remember { mutableStateOf(currentConfig.streamTitle) }
    var selectedBitrate by remember { mutableIntStateOf(currentConfig.bitrateKbps) }
    var micTalkover by remember { mutableStateOf(currentConfig.micTalkoverEnabled) }
    var showStreamKey by remember { mutableStateOf(false) }
    var showSetupGuide by remember { mutableStateOf(false) }

    val clipboardManager = LocalClipboardManager.current
    val isLive = metrics.state == StreamState.LIVE || metrics.state == StreamState.CONNECTING || metrics.state == StreamState.RECONNECTING

    LaunchedEffect(selectedPlatform) {
        if (!isLive) {
            val cfg = onGetPlatformConfig(selectedPlatform)
            ingestUrl = cfg.ingestUrl
            streamKey = cfg.streamKey
            selectedBitrate = cfg.bitrateKbps
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(DjSurfaceCard)
            .border(1.dp, if (isLive) DjRedHot else DjBorder, RoundedCornerShape(10.dp))
            .padding(12.dp)
            .testTag("live_stream_section"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Section Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(
                            when (metrics.state) {
                                StreamState.LIVE -> DjRedHot.copy(alpha = pulseAlpha)
                                StreamState.CONNECTING -> Color(0xFFFFAA00).copy(alpha = pulseAlpha)
                                StreamState.RECONNECTING -> Color(0xFFFFCC00).copy(alpha = pulseAlpha)
                                StreamState.ERROR -> Color.Red
                                StreamState.IDLE -> DjTextSecondary
                            }
                        )
                )
                Text(
                    text = "LIVE STREAMING BROADCAST CONSOLE",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black
                )
            }

            if (isLive) {
                val uptimeMin = metrics.durationSeconds / 60
                val uptimeSec = metrics.durationSeconds % 60
                Text(
                    text = String.format("ON AIR  %02d:%02d", uptimeMin, uptimeSec),
                    color = DjRedHot,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black
                )
            } else {
                Text(
                    text = "READY",
                    color = DjGreenSync,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Live Telemetry Bar when broadcasting
        if (isLive) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(DjSurfaceElevated)
                    .border(1.dp, DjRedHot.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                    .padding(8.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("TARGET: ${metrics.platform.displayName.uppercase()}", color = Color(metrics.platform.brandColorHex), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Text("${metrics.currentBitrateKbps} kbps | ${metrics.networkLatencyMs}ms ping", color = DjGreenSync, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                    }

                    // Level meter
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("VU", color = DjTextSecondary, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        val normLevel = ((metrics.peakOutputLevelDb + 48f) / 48f).coerceIn(0.05f, 1.0f)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color(0xFF10121A))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(normLevel)
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(
                                        if (normLevel > 0.85f) DjRedHot else if (normLevel > 0.65f) Color(0xFFFFBB00) else DjGreenSync
                                    )
                            )
                        }
                        Text("${metrics.peakOutputLevelDb.toInt()} dB", color = DjTextSecondary, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }

        // Platform Selector
        Text("BROADCAST DESTINATION", color = DjTextSecondary, fontSize = 8.sp, fontWeight = FontWeight.Bold)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            StreamPlatform.values().forEach { platform ->
                val isSelected = selectedPlatform == platform
                val brandColor = Color(platform.brandColorHex)

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isSelected) brandColor.copy(alpha = 0.25f) else Color(0xFF181B26))
                        .border(1.dp, if (isSelected) brandColor else DjBorder, RoundedCornerShape(6.dp))
                        .clickable(enabled = !isLive) { selectedPlatform = platform }
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = platform.shortName,
                        color = if (isSelected) Color.White else DjTextSecondary,
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold
                    )
                }
            }
        }

        // Tagline & Guide helper
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = selectedPlatform.tagline,
                color = Color(selectedPlatform.brandColorHex),
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = if (showSetupGuide) "Hide Guide" else "Setup Guide",
                color = DjTextSecondary,
                fontSize = 8.sp,
                modifier = Modifier.clickable { showSetupGuide = !showSetupGuide }
            )
        }

        AnimatedVisibility(visible = showSetupGuide) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF141620))
                    .padding(6.dp)
            ) {
                Text(
                    text = selectedPlatform.setupGuide,
                    color = Color(0xFFCCD0DF),
                    fontSize = 8.sp,
                    lineHeight = 12.sp
                )
            }
        }

        // Ingest URL field
        OutlinedTextField(
            value = ingestUrl,
            onValueChange = { ingestUrl = it },
            label = { Text("RTMP Ingest Server", fontSize = 9.sp) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLive,
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(selectedPlatform.brandColorHex),
                unfocusedBorderColor = DjBorder,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedContainerColor = DjSurfaceElevated,
                unfocusedContainerColor = DjSurfaceElevated
            ),
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        )

        // Stream Key field
        OutlinedTextField(
            value = streamKey,
            onValueChange = { streamKey = it },
            label = { Text("Stream Key", fontSize = 9.sp) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLive,
            singleLine = true,
            visualTransformation = if (showStreamKey) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { showStreamKey = !showStreamKey }) {
                        Icon(
                            imageVector = if (showStreamKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = null,
                            tint = DjTextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(
                        onClick = {
                            clipboardManager.getText()?.text?.let { streamKey = it.trim() }
                        },
                        enabled = !isLive
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentPaste,
                            contentDescription = "Paste",
                            tint = Color(selectedPlatform.brandColorHex),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(selectedPlatform.brandColorHex),
                unfocusedBorderColor = DjBorder,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedContainerColor = DjSurfaceElevated,
                unfocusedContainerColor = DjSurfaceElevated
            ),
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        )

        // Audio Bitrate Options
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            AVAILABLE_BITRATES.forEach { opt ->
                val isSel = selectedBitrate == opt.kbps
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isSel) DjGreenSync.copy(alpha = 0.25f) else DjSurfaceElevated)
                        .border(1.dp, if (isSel) DjGreenSync else DjBorder, RoundedCornerShape(4.dp))
                        .clickable(enabled = !isLive) { selectedBitrate = opt.kbps }
                        .padding(vertical = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = opt.label,
                        color = if (isSel) DjGreenSync else DjTextSecondary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Mic Talkover
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(DjSurfaceElevated)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = if (micTalkover) Icons.Default.Mic else Icons.Default.MicOff,
                    contentDescription = null,
                    tint = if (micTalkover) DjGreenSync else DjTextSecondary,
                    modifier = Modifier.size(16.dp)
                )
                Text("DJ Mic / Talkover Ducking (-12dB)", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
            Switch(
                checked = micTalkover,
                onCheckedChange = {
                    micTalkover = it
                    onToggleMicTalkover(it)
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = DjGreenSync,
                    checkedTrackColor = DjGreenSync.copy(alpha = 0.4f),
                    uncheckedThumbColor = DjTextSecondary,
                    uncheckedTrackColor = Color.DarkGray
                )
            )
        }

        // Start / Stop Broadcast Action
        if (!isLive) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(selectedPlatform.brandColorHex))
                    .clickable {
                        onStartStream(
                            LiveStreamConfig(
                                platform = selectedPlatform,
                                ingestUrl = ingestUrl.trim(),
                                streamKey = streamKey.trim(),
                                streamTitle = streamTitle.trim(),
                                bitrateKbps = selectedBitrate,
                                micTalkoverEnabled = micTalkover
                            )
                        )
                    }
                    .testTag("section_start_stream_btn"),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Sensors,
                        contentDescription = null,
                        tint = if (selectedPlatform == StreamPlatform.X_TWITTER) Color.Black else Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "GO LIVE ON ${selectedPlatform.displayName.uppercase()}",
                        color = if (selectedPlatform == StreamPlatform.X_TWITTER) Color.Black else Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(DjRedHot)
                    .clickable { onStopStream() }
                    .testTag("section_stop_stream_btn"),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SensorsOff,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Text("END LIVE BROADCAST", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}
