package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import com.example.audio.model.MasteringPreset
import com.example.audio.model.MasteringState
import com.example.ui.theme.DjBorder
import com.example.ui.theme.DjGreenSync
import com.example.ui.theme.DjRedHot
import com.example.ui.theme.DjSurfaceCard
import com.example.ui.theme.DjTextSecondary
import com.example.ui.theme.DjYellowWarn

@Composable
fun MasteringSection(
    masteringState: MasteringState,
    onSelectPreset: (MasteringPreset) -> Unit,
    onToggleBypass: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val meter = masteringState.meter
    val bypass = masteringState.bypass

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(DjSurfaceCard)
            .border(1.dp, DjBorder, RoundedCornerShape(8.dp))
            .padding(10.dp)
            .testTag("mastering_section")
    ) {
        // Section Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "11-STAGE MASTERING SUITE",
                color = DjGreenSync,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                text = "PRESET: ${masteringState.preset.label}",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Professional Loudness & Dynamics Metering Readouts (LUFS-I, LUFS-S, True Peak, RMS, DR)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF0F1118))
                .border(1.dp, DjBorder, RoundedCornerShape(6.dp))
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            MeterValueItem(label = "LUFS-I", value = String.format("%.1f", meter.lufsIntegrated), unit = "LUFS", color = DjGreenSync)
            MeterValueItem(label = "LUFS-S", value = String.format("%.1f", meter.lufsShortTerm), unit = "LUFS", color = DjGreenSync)
            MeterValueItem(label = "TRUE PEAK", value = String.format("%.2f", meter.truePeakDb), unit = "dBFS", color = if (meter.truePeakDb > -0.1f) DjRedHot else DjYellowWarn)
            MeterValueItem(label = "RMS", value = String.format("%.1f", meter.rmsDb), unit = "dB", color = Color.White)
            MeterValueItem(label = "DR SCORE", value = String.format("%.1f", meter.dynamicRange), unit = "DR", color = Color(0xFF00E5FF))
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Mastering Presets Row
        Text("MASTERING PRESET", color = DjTextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            MasteringPreset.values().forEach { preset ->
                val isSelected = masteringState.preset == preset
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isSelected) DjGreenSync.copy(alpha = 0.25f) else Color(0xFF1F2332))
                        .border(1.dp, if (isSelected) DjGreenSync else DjBorder, RoundedCornerShape(4.dp))
                        .clickable { onSelectPreset(preset) }
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                        .testTag("preset_${preset.name.lowercase()}"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = preset.label,
                        color = if (isSelected) DjGreenSync else Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 11-Stage Bypass Matrix
        Text("PROCESSOR RACK (TAP TO BYPASS)", color = DjTextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))

        val stages = listOf(
            Triple("trim", "1. Trim", bypass.inputTrimBypass),
            Triple("dc", "2. DC/Sub", bypass.dcProtectionBypass),
            Triple("eq", "3. Param EQ", bypass.parametricEqBypass),
            Triple("dyneq", "4. Dyn EQ", bypass.dynamicEqBypass),
            Triple("comp", "5. Bus Comp", bypass.busCompBypass),
            Triple("sat", "6. Saturation", bypass.saturationBypass),
            Triple("stereo", "7. Stereo Img", bypass.stereoProcessorBypass),
            Triple("clip", "8. Soft Clip", bypass.clipperBypass),
            Triple("limiter", "9. Brick Limiter", bypass.truePeakLimiterBypass)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            stages.forEach { (id, name, isBypassed) ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (!isBypassed) Color(0xFF1E3A2E) else Color(0xFF261E22))
                        .border(1.dp, if (!isBypassed) DjGreenSync else DjRedHot, RoundedCornerShape(4.dp))
                        .clickable { onToggleBypass(id) }
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                        .testTag("bypass_stage_$id"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = name,
                        color = if (!isBypassed) DjGreenSync else DjRedHot,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun MeterValueItem(
    label: String,
    value: String,
    unit: String,
    color: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, color = DjTextSecondary, fontSize = 8.sp, fontWeight = FontWeight.Bold)
        Text(
            text = value,
            color = color,
            fontSize = 13.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace
        )
        Text(text = unit, color = DjTextSecondary.copy(alpha = 0.7f), fontSize = 7.sp)
    }
}
