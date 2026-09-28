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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.model.EngineTelemetry
import com.example.audio.model.LatencyProfile
import com.example.audio.model.ThermalPerformanceMode
import com.example.ui.theme.DjBorder
import com.example.ui.theme.DjGreenSync
import com.example.ui.theme.DjRedHot
import com.example.ui.theme.DjSurfaceCard
import com.example.ui.theme.DjTextSecondary
import com.example.ui.theme.DjYellowWarn

@Composable
fun HardwareStatusSection(
    telemetry: EngineTelemetry,
    onSelectLatency: (LatencyProfile) -> Unit,
    onSelectPerformanceMode: (ThermalPerformanceMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(DjSurfaceCard)
            .border(1.dp, DjBorder, RoundedCornerShape(8.dp))
            .padding(10.dp)
            .testTag("hardware_status_section")
    ) {
        // Section Header & Active Audio Device Route
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "ORACLE DJ HARDWARE & DSP TELEMETRY",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                text = telemetry.thermalThrottlingStatus,
                color = DjGreenSync,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Active Output: ${telemetry.outputDeviceName}",
            color = DjYellowWarn,
            fontSize = 10.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Three-way Sample Rate Display (Source vs DSP vs Hardware Output)
        // Engineering compliance: Distinctly reports source from hardware output
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF0F1118))
                .border(1.dp, DjBorder, RoundedCornerShape(6.dp))
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("SOURCE DECK A", color = DjTextSecondary, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = if (telemetry.sourceSampleRateA > 0) "${telemetry.sourceSampleRateA / 1000} kHz / ${telemetry.sourceBitDepthA}-bit" else "No Source",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(telemetry.sourceFormatNameA, color = DjTextSecondary, fontSize = 8.sp)
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("ENGINE DSP", color = DjTextSecondary, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = "${telemetry.dspSampleRate / 1000} kHz / 32-bit Float",
                    color = DjGreenSync,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text("Lock-Free Polyphase", color = DjTextSecondary, fontSize = 8.sp)
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("HARDWARE OUTPUT", color = DjTextSecondary, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = "${telemetry.outputDeviceSampleRate / 1000} kHz / Low Latency",
                    color = DjYellowWarn,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text("AudioTrack Direct", color = DjTextSecondary, fontSize = 8.sp)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Real-time CPU, Latency & Underrun Metrics
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            MetricTile(
                label = "DSP CPU LOAD",
                value = String.format("%.1f%%", telemetry.dspCpuLoadPercent),
                color = if (telemetry.dspCpuLoadPercent > 70f) DjRedHot else if (telemetry.dspCpuLoadPercent > 40f) DjYellowWarn else DjGreenSync
            )
            MetricTile(
                label = "BUFFER SIZE",
                value = "${telemetry.bufferSizeFrames} frames",
                color = Color.White
            )
            MetricTile(
                label = "LATENCY",
                value = String.format("%.1f ms", telemetry.actualLatencyMs),
                color = DjGreenSync
            )
            MetricTile(
                label = "UNDERRUNS",
                value = "${telemetry.underrunCount}",
                color = if (telemetry.underrunCount > 0) DjYellowWarn else DjGreenSync
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Latency Profiles Selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("BUFFER LATENCY PROFILE", color = DjTextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                LatencyProfile.values().forEach { profile ->
                    val isSelected = telemetry.latencyProfile == profile
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isSelected) DjGreenSync.copy(alpha = 0.25f) else Color(0xFF1E2232))
                            .border(1.dp, if (isSelected) DjGreenSync else DjBorder, RoundedCornerShape(4.dp))
                            .clickable { onSelectLatency(profile) }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                            .testTag("latency_${profile.name.lowercase()}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = profile.label.substringBefore(" "),
                            color = if (isSelected) DjGreenSync else DjTextSecondary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Thermal Performance Mode (Quality, Balanced, Battery)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("PERFORMANCE MODE", color = DjTextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                ThermalPerformanceMode.values().forEach { mode ->
                    val isSelected = telemetry.performanceMode == mode
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isSelected) Color(0xFF2979FF).copy(alpha = 0.25f) else Color(0xFF1E2232))
                            .border(1.dp, if (isSelected) Color(0xFF2979FF) else DjBorder, RoundedCornerShape(4.dp))
                            .clickable { onSelectPerformanceMode(mode) }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                            .testTag("perf_${mode.name.lowercase()}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = mode.name,
                            color = if (isSelected) Color(0xFF2979FF) else DjTextSecondary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricTile(
    label: String,
    value: String,
    color: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, color = DjTextSecondary, fontSize = 8.sp, fontWeight = FontWeight.Bold)
        Text(
            text = value,
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace
        )
    }
}
