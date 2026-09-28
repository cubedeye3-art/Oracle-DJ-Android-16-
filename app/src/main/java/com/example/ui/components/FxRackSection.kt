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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.model.BeatDivision
import com.example.audio.model.DeckId
import com.example.audio.model.FxType
import com.example.audio.model.FxUnitState
import com.example.ui.theme.DeckAPrimary
import com.example.ui.theme.DeckBPrimary
import com.example.ui.theme.DjBorder
import com.example.ui.theme.DjPurpleFx
import com.example.ui.theme.DjSurfaceCard
import com.example.ui.theme.DjTextSecondary

@Composable
fun FxRackSection(
    fxStateA: FxUnitState,
    fxStateB: FxUnitState,
    onToggleFx: (DeckId, Boolean) -> Unit,
    onSelectFxType: (DeckId, FxType) -> Unit,
    onDryWetChange: (DeckId, Float) -> Unit,
    onParam1Change: (DeckId, Float) -> Unit,
    onParam2Change: (DeckId, Float) -> Unit,
    onParam3Change: (DeckId, Float) -> Unit,
    onBeatDivisionChange: (DeckId, BeatDivision) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedDeck by remember { mutableStateOf(DeckId.DECK_A) }
    val currentFx = if (selectedDeck == DeckId.DECK_A) fxStateA else fxStateB
    val currentAccent = if (selectedDeck == DeckId.DECK_A) DeckAPrimary else DeckBPrimary

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(DjSurfaceCard)
            .border(1.dp, DjBorder, RoundedCornerShape(8.dp))
            .padding(10.dp)
            .testTag("fx_rack_section")
    ) {
        // Deck Selection Tabs & FX Master ON/OFF Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (selectedDeck == DeckId.DECK_A) DeckAPrimary.copy(alpha = 0.25f) else Color(0xFF1E2232))
                        .border(1.dp, if (selectedDeck == DeckId.DECK_A) DeckAPrimary else DjBorder, RoundedCornerShape(4.dp))
                        .clickable { selectedDeck = DeckId.DECK_A }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .testTag("fx_tab_deck_a"),
                    contentAlignment = Alignment.Center
                ) {
                    Text("DECK A FX", color = if (selectedDeck == DeckId.DECK_A) DeckAPrimary else DjTextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (selectedDeck == DeckId.DECK_B) DeckBPrimary.copy(alpha = 0.25f) else Color(0xFF1E2232))
                        .border(1.dp, if (selectedDeck == DeckId.DECK_B) DeckBPrimary else DjBorder, RoundedCornerShape(4.dp))
                        .clickable { selectedDeck = DeckId.DECK_B }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .testTag("fx_tab_deck_b"),
                    contentAlignment = Alignment.Center
                ) {
                    Text("DECK B FX", color = if (selectedDeck == DeckId.DECK_B) DeckBPrimary else DjTextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Power button
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (currentFx.enabled) DjPurpleFx else Color(0xFF232838))
                    .border(1.dp, if (currentFx.enabled) Color.White else DjBorder, RoundedCornerShape(6.dp))
                    .clickable { onToggleFx(selectedDeck, !currentFx.enabled) }
                    .padding(horizontal = 14.dp, vertical = 6.dp)
                    .testTag("fx_on_off_btn"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (currentFx.enabled) "FX ACTIVE" else "FX BYPASS",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 20 FX Selectable Types (Horizontal Scrollable Strip)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FxType.values().forEach { type ->
                val isSelected = currentFx.type == type
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isSelected) currentAccent.copy(alpha = 0.3f) else Color(0xFF1A1D2B))
                        .border(1.dp, if (isSelected) currentAccent else DjBorder, RoundedCornerShape(4.dp))
                        .clickable { onSelectFxType(selectedDeck, type) }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                        .testTag("fx_select_${type.name.lowercase()}"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = type.displayName,
                        color = if (isSelected) currentAccent else Color.White,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Beat Division Sync (1/32 to 4 Bars)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("TEMPO SYNC", color = DjTextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                BeatDivision.values().forEach { div ->
                    val isSelected = currentFx.beatDivision == div
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (isSelected) DjPurpleFx.copy(alpha = 0.35f) else Color(0xFF1E2232))
                            .border(1.dp, if (isSelected) DjPurpleFx else DjBorder, RoundedCornerShape(3.dp))
                            .clickable { onBeatDivisionChange(selectedDeck, div) }
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = div.label,
                            color = if (isSelected) Color.White else DjTextSecondary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // FX Controls: Dry/Wet & 3 Dynamic Parameter Sliders
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            FxSliderRow(
                label = "DRY/WET",
                value = currentFx.dryWet,
                onValueChange = { onDryWetChange(selectedDeck, it) },
                color = DjPurpleFx,
                testTag = "fx_dry_wet_slider"
            )
            FxSliderRow(
                label = currentFx.type.param1Name.uppercase(),
                value = currentFx.param1,
                onValueChange = { onParam1Change(selectedDeck, it) },
                color = currentAccent,
                testTag = "fx_param_1"
            )
            FxSliderRow(
                label = currentFx.type.param2Name.uppercase(),
                value = currentFx.param2,
                onValueChange = { onParam2Change(selectedDeck, it) },
                color = currentAccent,
                testTag = "fx_param_2"
            )
            FxSliderRow(
                label = currentFx.type.param3Name.uppercase(),
                value = currentFx.param3,
                onValueChange = { onParam3Change(selectedDeck, it) },
                color = currentAccent,
                testTag = "fx_param_3"
            )
        }
    }
}

@Composable
private fun FxSliderRow(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    color: Color,
    testTag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(28.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(80.dp)
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0f..1f,
            modifier = Modifier.weight(1f).testTag(testTag),
            colors = SliderDefaults.colors(
                thumbColor = color,
                activeTrackColor = color,
                inactiveTrackColor = DjBorder
            )
        )
        Text(
            text = "${(value * 100).toInt()}%",
            color = DjTextSecondary,
            fontSize = 9.sp,
            modifier = Modifier.width(36.dp)
        )
    }
}
