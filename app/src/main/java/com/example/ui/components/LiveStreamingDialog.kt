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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.draw.alpha
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.audio.streaming.AVAILABLE_BITRATES
import com.example.audio.streaming.LiveStreamConfig
import com.example.audio.streaming.LiveStreamMetrics
import com.example.audio.streaming.StreamPlatform
import com.example.audio.streaming.StreamState
import com.example.ui.theme.DjBackground
import com.example.ui.theme.DjBorder
import com.example.ui.theme.DjGreenSync
import com.example.ui.theme.DjRedHot
import com.example.ui.theme.DjSurfaceCard
import com.example.ui.theme.DjSurfaceElevated
import com.example.ui.theme.DjTextPrimary
import com.example.ui.theme.DjTextSecondary

@Composable
fun LiveStreamingDialog(
    metrics: LiveStreamMetrics,
    currentConfig: LiveStreamConfig,
    onGetPlatformConfig: (StreamPlatform) -> LiveStreamConfig,
    onStartStream: (LiveStreamConfig) -> Unit,
    onStopStream: () -> Unit,
    onToggleMicTalkover: (Boolean) -> Unit,
    onDismiss: () -> Unit
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

    // Update form when platform changes
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

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(14.dp))
                .background(DjBackground)
                .border(1.dp, if (isLive) DjRedHot else DjBorder, RoundedCornerShape(14.dp))
                .padding(14.dp)
                .testTag("live_streaming_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Header with status indicator & close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                        Column {
                            Text(
                                text = "ORACLE DJ BROADCAST STUDIO",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = if (isLive) "LIVE ON-AIR TRANSMISSION ACTIVE" else "LIVE STREAM INGEST TO SOCIAL PLATFORMS",
                                color = if (isLive) DjRedHot else DjTextSecondary,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Broadcast Dialog",
                            tint = DjTextSecondary
                        )
                    }
                }

                // If currently live, show On-Air Monitor Card
                if (isLive) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(DjSurfaceElevated)
                            .border(1.dp, DjRedHot.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(DjRedHot)
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "● ON AIR",
                                            color = Color.White,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Black
                                        )
                                    }
                                    Text(
                                        text = metrics.platform.displayName.uppercase(),
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                val uptimeMin = metrics.durationSeconds / 60
                                val uptimeSec = metrics.durationSeconds % 60
                                Text(
                                    text = String.format("%02d:%02d", uptimeMin, uptimeSec),
                                    color = DjGreenSync,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }

                            // Live Transmission Metrics Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("AUDIO BITRATE", color = DjTextSecondary, fontSize = 8.sp)
                                    Text("${metrics.currentBitrateKbps} kbps", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                                Column {
                                    Text("LATENCY (RTT)", color = DjTextSecondary, fontSize = 8.sp)
                                    Text("${metrics.networkLatencyMs} ms", color = DjGreenSync, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                                Column {
                                    Text("FRAMES SENT", color = DjTextSecondary, fontSize = 8.sp)
                                    Text("${metrics.framesSent / 1000}k", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                                Column {
                                    Text("BUFFER HEALTH", color = DjTextSecondary, fontSize = 8.sp)
                                    Text("${(metrics.networkStability * 100).toInt()}%", color = DjGreenSync, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            // Live Audio Peak Output Bar
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("OUT VU", color = DjTextSecondary, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                val normLevel = ((metrics.peakOutputLevelDb + 48f) / 48f).coerceIn(0.05f, 1.0f)
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFF10121A))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(normLevel)
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(4.dp))
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

                // Platform Selector Tabs: YouTube, Facebook, X, Custom
                Text(
                    text = "SELECT BROADCAST DESTINATION",
                    color = DjTextSecondary,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )

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
                                .background(
                                    if (isSelected) brandColor.copy(alpha = 0.25f) else DjSurfaceCard
                                )
                                .border(
                                    1.dp,
                                    if (isSelected) brandColor else DjBorder,
                                    RoundedCornerShape(6.dp)
                                )
                                .clickable(enabled = !isLive) {
                                    selectedPlatform = platform
                                }
                                .padding(vertical = 8.dp, horizontal = 2.dp)
                                .testTag("platform_tab_${platform.id}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(brandColor)
                                )
                                Text(
                                    text = platform.shortName,
                                    color = if (isSelected) Color.White else DjTextSecondary,
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Platform Tagline & Setup Guide Helper Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = selectedPlatform.tagline,
                        color = Color(selectedPlatform.brandColorHex),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Row(
                        modifier = Modifier.clickable { showSetupGuide = !showSetupGuide },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Setup Guide",
                            tint = DjTextSecondary,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = if (showSetupGuide) "Hide Guide" else "Where's my key?",
                            color = DjTextSecondary,
                            fontSize = 9.sp
                        )
                    }
                }

                // Expandable setup instructions
                AnimatedVisibility(visible = showSetupGuide) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF151824))
                            .padding(8.dp)
                    ) {
                        Text(
                            text = selectedPlatform.setupGuide,
                            color = Color(0xFFCCD0DF),
                            fontSize = 9.sp,
                            lineHeight = 14.sp
                        )
                    }
                }

                // RTMP Ingest URL
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("RTMP / RTMPS INGEST SERVER", color = DjTextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = "Reset Default",
                            color = DjTextSecondary,
                            fontSize = 8.sp,
                            modifier = Modifier.clickable(enabled = !isLive) {
                                ingestUrl = selectedPlatform.defaultIngestUrl
                            }
                        )
                    }
                    OutlinedTextField(
                        value = ingestUrl,
                        onValueChange = { ingestUrl = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ingest_url_input"),
                        enabled = !isLive,
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(selectedPlatform.brandColorHex),
                            unfocusedBorderColor = DjBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = DjSurfaceCard,
                            unfocusedContainerColor = DjSurfaceCard
                        ),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    )
                }

                // Stream Key input with show/hide and paste button
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("STREAM KEY (PRIVATE)", color = DjTextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = streamKey,
                        onValueChange = { streamKey = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("stream_key_input"),
                        enabled = !isLive,
                        singleLine = true,
                        visualTransformation = if (showStreamKey) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { showStreamKey = !showStreamKey }) {
                                    Icon(
                                        imageVector = if (showStreamKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "Toggle Key Visibility",
                                        tint = DjTextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        clipboardManager.getText()?.text?.let { clipText ->
                                            streamKey = clipText.trim()
                                        }
                                    },
                                    enabled = !isLive
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentPaste,
                                        contentDescription = "Paste from Clipboard",
                                        tint = Color(selectedPlatform.brandColorHex),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        },
                        placeholder = {
                            Text("Paste stream key from ${selectedPlatform.shortName}", color = DjTextSecondary.copy(alpha = 0.6f), fontSize = 11.sp)
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(selectedPlatform.brandColorHex),
                            unfocusedBorderColor = DjBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = DjSurfaceCard,
                            unfocusedContainerColor = DjSurfaceCard
                        ),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    )
                }

                // Stream Title
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("STREAM TITLE / METADATA", color = DjTextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = streamTitle,
                        onValueChange = { streamTitle = it },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLive,
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = DjBorder,
                            unfocusedBorderColor = DjBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = DjSurfaceCard,
                            unfocusedContainerColor = DjSurfaceCard
                        ),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp)
                    )
                }

                // Broadcast Audio Bitrate Selector
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("AUDIO ENCODING BITRATE", color = DjTextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        AVAILABLE_BITRATES.forEach { opt ->
                            val isSel = selectedBitrate == opt.kbps
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSel) DjGreenSync.copy(alpha = 0.2f) else DjSurfaceCard)
                                    .border(1.dp, if (isSel) DjGreenSync else DjBorder, RoundedCornerShape(6.dp))
                                    .clickable(enabled = !isLive) {
                                        selectedBitrate = opt.kbps
                                    }
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = opt.label,
                                    color = if (isSel) DjGreenSync else DjTextSecondary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // DJ Mic / Talkover Ducking
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(DjSurfaceCard)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (micTalkover) Icons.Default.Mic else Icons.Default.MicOff,
                            contentDescription = null,
                            tint = if (micTalkover) DjGreenSync else DjTextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Column {
                            Text("DJ MIC TALKOVER DUCKING", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text("Ducks music by -12dB for voice commentary", color = DjTextSecondary, fontSize = 8.sp)
                        }
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

                // Error Message Display
                metrics.errorMessage?.let { err ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(DjRedHot.copy(alpha = 0.15f))
                            .border(1.dp, DjRedHot, RoundedCornerShape(6.dp))
                            .padding(8.dp)
                    ) {
                        Text(
                            text = "Stream Alert: $err",
                            color = DjRedHot,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Primary Action Button: GO LIVE / STOP STREAM
                if (!isLive) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(selectedPlatform.brandColorHex))
                            .clickable {
                                val config = LiveStreamConfig(
                                    platform = selectedPlatform,
                                    ingestUrl = ingestUrl.trim(),
                                    streamKey = streamKey.trim(),
                                    streamTitle = streamTitle.trim(),
                                    bitrateKbps = selectedBitrate,
                                    micTalkoverEnabled = micTalkover
                                )
                                onStartStream(config)
                            }
                            .testTag("start_stream_btn"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sensors,
                                contentDescription = null,
                                tint = if (selectedPlatform == StreamPlatform.X_TWITTER) Color.Black else Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "GO LIVE ON ${selectedPlatform.displayName.uppercase()}",
                                color = if (selectedPlatform == StreamPlatform.X_TWITTER) Color.Black else Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Minimize & keep streaming in background
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(DjSurfaceElevated)
                                .border(1.dp, DjBorder, RoundedCornerShape(8.dp))
                                .clickable { onDismiss() },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("MINIMIZE (KEEP LIVE)", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        // Stop stream
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(DjRedHot)
                                .clickable { onStopStream() }
                                .testTag("stop_stream_btn"),
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
                                    modifier = Modifier.size(18.dp)
                                )
                                Text("END BROADCAST", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                }
            }
        }
    }
}
