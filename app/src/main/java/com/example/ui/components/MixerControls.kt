package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.model.CrossfaderAssign
import com.example.audio.model.CrossfaderCurve
import com.example.audio.model.DeckId
import com.example.audio.model.MixerState
import com.example.ui.theme.DeckAPrimary
import com.example.ui.theme.DeckBPrimary
import com.example.ui.theme.DjBorder
import com.example.ui.theme.DjGreenSync
import com.example.ui.theme.DjRedHot
import com.example.ui.theme.DjSurfaceCard
import com.example.ui.theme.DjTextSecondary
import com.example.ui.theme.DjYellowWarn

@Composable
fun MixerControls(
    mixerState: MixerState,
    onGainChange: (DeckId, Float) -> Unit,
    onEqChange: (DeckId, low: Float, mid: Float, high: Float) -> Unit,
    onFilterChange: (DeckId, Float) -> Unit,
    onFaderChange: (DeckId, Float) -> Unit,
    onCrossfaderChange: (Float) -> Unit,
    onCrossfaderCurveChange: (CrossfaderCurve) -> Unit,
    onCrossfaderAssignChange: (DeckId, CrossfaderAssign) -> Unit,
    onMasterGainChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(DjSurfaceCard)
            .border(1.dp, DjBorder, RoundedCornerShape(8.dp))
            .padding(8.dp)
            .testTag("mixer_section")
    ) {
        // Channel EQ & Filter Section (Two Channels Side by Side)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Channel 1 (Deck A)
            ChannelStrip(
                title = "CH 1",
                accentColor = DeckAPrimary,
                gain = mixerState.channelA.gain,
                high = mixerState.channelA.high,
                mid = mixerState.channelA.mid,
                low = mixerState.channelA.low,
                filter = mixerState.channelA.filter,
                fader = mixerState.channelA.fader,
                vuLevel = maxOf(mixerState.channelA.vuLevelL, mixerState.channelA.vuLevelR),
                assign = mixerState.channelA.crossfaderAssign,
                onGainChange = { onGainChange(DeckId.DECK_A, it) },
                onEqHighChange = { onEqChange(DeckId.DECK_A, mixerState.channelA.low, mixerState.channelA.mid, it) },
                onEqMidChange = { onEqChange(DeckId.DECK_A, mixerState.channelA.low, it, mixerState.channelA.high) },
                onEqLowChange = { onEqChange(DeckId.DECK_A, it, mixerState.channelA.mid, mixerState.channelA.high) },
                onFilterChange = { onFilterChange(DeckId.DECK_A, it) },
                onFaderChange = { onFaderChange(DeckId.DECK_A, it) },
                onAssignChange = { onCrossfaderAssignChange(DeckId.DECK_A, it) },
                testTagPrefix = "ch1",
                modifier = Modifier.weight(1f)
            )

            // Center Master & Meter Strip
            MasterCenterStrip(
                masterGain = mixerState.masterGain,
                preMasterVu = maxOf(mixerState.preMasterVuL, mixerState.preMasterVuR),
                masterVu = maxOf(mixerState.masterVuL, mixerState.masterVuR),
                onMasterGainChange = onMasterGainChange,
                modifier = Modifier.width(60.dp)
            )

            // Channel 2 (Deck B)
            ChannelStrip(
                title = "CH 2",
                accentColor = DeckBPrimary,
                gain = mixerState.channelB.gain,
                high = mixerState.channelB.high,
                mid = mixerState.channelB.mid,
                low = mixerState.channelB.low,
                filter = mixerState.channelB.filter,
                fader = mixerState.channelB.fader,
                vuLevel = maxOf(mixerState.channelB.vuLevelL, mixerState.channelB.vuLevelR),
                assign = mixerState.channelB.crossfaderAssign,
                onGainChange = { onGainChange(DeckId.DECK_B, it) },
                onEqHighChange = { onEqChange(DeckId.DECK_B, mixerState.channelB.low, mixerState.channelB.mid, it) },
                onEqMidChange = { onEqChange(DeckId.DECK_B, mixerState.channelB.low, it, mixerState.channelB.high) },
                onEqLowChange = { onEqChange(DeckId.DECK_B, it, mixerState.channelB.mid, mixerState.channelB.high) },
                onFilterChange = { onFilterChange(DeckId.DECK_B, it) },
                onFaderChange = { onFaderChange(DeckId.DECK_B, it) },
                onAssignChange = { onCrossfaderAssignChange(DeckId.DECK_B, it) },
                testTagPrefix = "ch2",
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Crossfader Section
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF161925))
                .padding(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "CROSSFADER",
                    color = DjTextSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )

                // Curve selector buttons
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    CrossfaderCurve.values().forEach { curve ->
                        val isSelected = mixerState.crossfaderCurve == curve
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (isSelected) DjGreenSync.copy(alpha = 0.2f) else Color(0xFF222638))
                                .border(1.dp, if (isSelected) DjGreenSync else DjBorder, RoundedCornerShape(4.dp))
                                .clickable { onCrossfaderCurveChange(curve) }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                .testTag("crossfader_curve_${curve.name.lowercase()}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = curve.label,
                                color = if (isSelected) DjGreenSync else DjTextSecondary,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Slider(
                value = mixerState.crossfaderPosition,
                onValueChange = onCrossfaderChange,
                valueRange = 0f..1f,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("crossfader_slider"),
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = DeckBPrimary,
                    inactiveTrackColor = DeckAPrimary
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("A", color = DeckAPrimary, fontSize = 11.sp, fontWeight = FontWeight.Black)
                Text("THRU", color = DjTextSecondary, fontSize = 10.sp)
                Text("B", color = DeckBPrimary, fontSize = 11.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun ChannelStrip(
    title: String,
    accentColor: Color,
    gain: Float,
    high: Float,
    mid: Float,
    low: Float,
    filter: Float,
    fader: Float,
    vuLevel: Float,
    assign: CrossfaderAssign,
    onGainChange: (Float) -> Unit,
    onEqHighChange: (Float) -> Unit,
    onEqMidChange: (Float) -> Unit,
    onEqLowChange: (Float) -> Unit,
    onFilterChange: (Float) -> Unit,
    onFaderChange: (Float) -> Unit,
    onAssignChange: (CrossfaderAssign) -> Unit,
    testTagPrefix: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = title, color = accentColor, fontSize = 12.sp, fontWeight = FontWeight.Black)

        Spacer(modifier = Modifier.height(4.dp))

        // Mini Knobs Row (Gain, High, Mid, Low)
        KnobRowItem(label = "TRIM", value = gain, range = 0f..2f, onValueChange = onGainChange, testTag = "${testTagPrefix}_gain")
        KnobRowItem(label = "HI", value = high, range = 0f..2f, onValueChange = onEqHighChange, testTag = "${testTagPrefix}_hi")
        KnobRowItem(label = "MID", value = mid, range = 0f..2f, onValueChange = onEqMidChange, testTag = "${testTagPrefix}_mid")
        KnobRowItem(label = "LOW", value = low, range = 0f..2f, onValueChange = onEqLowChange, testTag = "${testTagPrefix}_low")
        KnobRowItem(label = "FILTER", value = filter, range = -1f..1f, isBipolar = true, onValueChange = onFilterChange, testTag = "${testTagPrefix}_filter")

        Spacer(modifier = Modifier.height(4.dp))

        // Crossfader Assign Toggle
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF1E2232))
                .padding(2.dp)
        ) {
            listOf(CrossfaderAssign.DECK_A, CrossfaderAssign.THRU, CrossfaderAssign.DECK_B).forEach { a ->
                val selected = assign == a
                Text(
                    text = when (a) {
                        CrossfaderAssign.DECK_A -> "A"
                        CrossfaderAssign.THRU -> "T"
                        CrossfaderAssign.DECK_B -> "B"
                    },
                    color = if (selected) Color.White else DjTextSecondary,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (selected) accentColor else Color.Transparent)
                        .clickable { onAssignChange(a) }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Channel Fader & VU Meter
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            // Volume Slider
            Slider(
                value = fader,
                onValueChange = onFaderChange,
                valueRange = 0f..1f,
                modifier = Modifier
                    .weight(1f)
                    .testTag("${testTagPrefix}_fader"),
                colors = SliderDefaults.colors(
                    thumbColor = accentColor,
                    activeTrackColor = accentColor,
                    inactiveTrackColor = DjBorder
                )
            )

            Spacer(modifier = Modifier.width(4.dp))

            // LED VU Meter Bar
            LedVuMeter(level = vuLevel, modifier = Modifier.width(8.dp).fillMaxHeight())
        }
    }
}

@Composable
private fun MasterCenterStrip(
    masterGain: Float,
    preMasterVu: Float,
    masterVu: Float,
    onMasterGainChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("MST", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black)

        KnobRowItem(label = "VOL", value = masterGain, range = 0f..2f, onValueChange = onMasterGainChange, testTag = "master_gain")

        Spacer(modifier = Modifier.height(8.dp))

        Text("VU", color = DjTextSecondary, fontSize = 9.sp)
        Row(
            modifier = Modifier.height(100.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            LedVuMeter(level = preMasterVu, modifier = Modifier.width(6.dp).fillMaxHeight())
            LedVuMeter(level = masterVu, modifier = Modifier.width(6.dp).fillMaxHeight())
        }
    }
}

@Composable
private fun KnobRowItem(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    isBipolar: Boolean = false,
    onValueChange: (Float) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(26.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = DjTextSecondary,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(34.dp)
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier.weight(1f).testTag(testTag),
            colors = SliderDefaults.colors(
                thumbColor = if (isBipolar && kotlin.math.abs(value) < 0.05f) Color.White else DjGreenSync,
                activeTrackColor = DjGreenSync,
                inactiveTrackColor = DjBorder
            )
        )
    }
}

@Composable
fun LedVuMeter(
    level: Float,
    modifier: Modifier = Modifier
) {
    val segments = 12
    val activeSegments = (level.coerceIn(0f, 1.2f) * segments).toInt()

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(2.dp))
            .background(Color(0xFF0D0F16))
            .padding(1.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        for (i in (segments - 1) downTo 0) {
            val isActive = i <= activeSegments
            val color = when {
                i >= 10 -> DjRedHot      // Peak clip
                i >= 7 -> DjYellowWarn   // Warn +3dB
                else -> DjGreenSync      // Safe level
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 0.5.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(if (isActive) color else color.copy(alpha = 0.15f))
            )
        }
    }
}
