package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import com.example.audio.model.DeckId
import com.example.audio.model.HotCue
import com.example.ui.theme.DjBorder
import com.example.ui.theme.DjSurfaceCard
import com.example.ui.theme.DjTextSecondary

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun HotCuePadView(
    deckId: DeckId,
    hotCues: List<HotCue>,
    onTriggerCue: (Int) -> Unit,
    onDeleteCue: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val deckName = deckId.name.lowercase()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(DjSurfaceCard)
            .border(1.dp, DjBorder, RoundedCornerShape(8.dp))
            .padding(8.dp)
            .testTag("${deckName}_hot_cues_section")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "HOT CUES (TAP: JUMP/SET • HOLD: DELETE)",
                color = DjTextSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 8 Pads arranged in 2 rows of 4
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for (i in 0 until 4) {
                    CuePadItem(
                        index = i,
                        cue = hotCues.getOrNull(i),
                        onTrigger = { onTriggerCue(i) },
                        onDelete = { onDeleteCue(i) },
                        testTag = "${deckName}_pad_${i + 1}",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for (i in 4 until 8) {
                    CuePadItem(
                        index = i,
                        cue = hotCues.getOrNull(i),
                        onTrigger = { onTriggerCue(i) },
                        onDelete = { onDeleteCue(i) },
                        testTag = "${deckName}_pad_${i + 1}",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun CuePadItem(
    index: Int,
    cue: HotCue?,
    onTrigger: () -> Unit,
    onDelete: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    val isSet = cue != null && cue.positionSeconds >= 0.0
    val padColor = if (cue != null) Color(cue.colorHex) else Color(0xFF00E5FF)

    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (isSet) padColor.copy(alpha = 0.28f) else Color(0xFF191C28))
            .border(2.dp, if (isSet) padColor else Color(0xFF282D40), RoundedCornerShape(6.dp))
            .combinedClickable(
                onClick = onTrigger,
                onLongClick = onDelete
            )
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "${index + 1}",
                color = if (isSet) padColor else DjTextSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                text = if (isSet) {
                    val sec = cue!!.positionSeconds.toInt()
                    String.format("%02d:%02d", sec / 60, sec % 60)
                } else "EMPTY",
                color = if (isSet) Color.White else DjTextSecondary.copy(alpha = 0.6f),
                fontSize = 8.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
