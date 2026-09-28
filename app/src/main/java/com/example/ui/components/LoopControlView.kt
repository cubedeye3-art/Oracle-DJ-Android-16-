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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.model.DeckId
import com.example.audio.model.LoopState
import com.example.ui.theme.DeckAPrimary
import com.example.ui.theme.DeckBPrimary
import com.example.ui.theme.DjBorder
import com.example.ui.theme.DjGreenSync
import com.example.ui.theme.DjSurfaceCard
import com.example.ui.theme.DjTextSecondary

@Composable
fun LoopControlView(
    deckId: DeckId,
    loopState: LoopState,
    onToggleLoop: (Float) -> Unit,
    onExitLoop: () -> Unit,
    onHalveLoop: () -> Unit,
    onDoubleLoop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val themeColor = if (deckId == DeckId.DECK_A) DeckAPrimary else DeckBPrimary
    val deckName = deckId.name.lowercase()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(DjSurfaceCard)
            .border(1.dp, DjBorder, RoundedCornerShape(8.dp))
            .padding(8.dp)
            .testTag("${deckName}_loop_section")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "AUTO LOOP / ROLL",
                color = DjTextSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
            if (loopState.active) {
                Text(
                    text = "ACTIVE (${loopState.lengthBeats.toInt()} BEATS)",
                    color = DjGreenSync,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Loop Sizes Row
        val loopSizes = listOf(0.5f, 1f, 2f, 4f, 8f, 16f)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            loopSizes.forEach { size ->
                val isSelected = loopState.active && loopState.lengthBeats == size
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isSelected) themeColor.copy(alpha = 0.3f) else Color(0xFF1B1E2B))
                        .border(1.dp, if (isSelected) themeColor else DjBorder, RoundedCornerShape(4.dp))
                        .clickable { onToggleLoop(size) }
                        .testTag("${deckName}_loop_${size.toInt()}"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (size < 1f) "1/2" else "${size.toInt()}",
                        color = if (isSelected) themeColor else Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Loop Action Controls: 1/2, 2X, EXIT / RELOOP
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(32.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF232838))
                    .border(1.dp, DjBorder, RoundedCornerShape(4.dp))
                    .clickable { onHalveLoop() }
                    .testTag("${deckName}_loop_halve"),
                contentAlignment = Alignment.Center
            ) {
                Text("÷2 HALVE", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(32.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF232838))
                    .border(1.dp, DjBorder, RoundedCornerShape(4.dp))
                    .clickable { onDoubleLoop() }
                    .testTag("${deckName}_loop_double"),
                contentAlignment = Alignment.Center
            ) {
                Text("×2 DOUBLE", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }

            Box(
                modifier = Modifier
                    .weight(1.2f)
                    .height(32.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (loopState.active) DjGreenSync.copy(alpha = 0.25f) else Color(0xFF232838))
                    .border(1.dp, if (loopState.active) DjGreenSync else DjBorder, RoundedCornerShape(4.dp))
                    .clickable { onExitLoop() }
                    .testTag("${deckName}_loop_exit"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (loopState.active) "EXIT LOOP" else "RELOOP",
                    color = if (loopState.active) DjGreenSync else DjTextSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
