package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.model.DeckUiState
import com.example.ui.theme.DeckAPrimary
import com.example.ui.theme.DeckBPrimary
import com.example.ui.theme.DjBackground
import com.example.ui.theme.DjBorder
import com.example.ui.theme.DjGreenSync
import com.example.ui.theme.DjRedHot
import kotlin.math.abs

@Composable
fun DualWaveformView(
    deckA: DeckUiState,
    deckB: DeckUiState,
    modifier: Modifier = Modifier,
    isLandscape: Boolean = false
) {
    val waveH = if (isLandscape) 34.dp else 46.dp
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(DjBackground)
            .padding(4.dp)
            .testTag("dual_waveform_view")
    ) {
        // Deck A Scrolling Waveform
        DeckScrollingWaveform(
            state = deckA,
            themeColor = DeckAPrimary,
            modifier = Modifier
                .fillMaxWidth()
                .height(waveH)
        )

        Spacer(modifier = Modifier.height(2.dp))

        // Center Beat Phase Alignment Bar
        BeatPhaseAlignmentBar(
            phaseA = deckA.phaseOffset,
            phaseB = deckB.phaseOffset,
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
        )

        Spacer(modifier = Modifier.height(2.dp))

        // Deck B Scrolling Waveform
        DeckScrollingWaveform(
            state = deckB,
            themeColor = DeckBPrimary,
            modifier = Modifier
                .fillMaxWidth()
                .height(waveH)
        )
    }
}

@Composable
private fun BeatPhaseAlignmentBar(
    phaseA: Float,
    phaseB: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.background(Color(0xFF0F1118))) {
        val centerX = size.width / 2f
        val centerY = size.height / 2f

        // Center reference mark
        drawLine(
            color = Color.White.copy(alpha = 0.6f),
            start = Offset(centerX, 0f),
            end = Offset(centerX, size.height),
            strokeWidth = 2.dp.toPx()
        )

        // Deck A phase tick
        val xA = centerX + (phaseA * (size.width * 0.4f))
        drawLine(
            color = DeckAPrimary,
            start = Offset(xA, 1.dp.toPx()),
            end = Offset(xA, centerY),
            strokeWidth = 3.dp.toPx()
        )

        // Deck B phase tick
        val xB = centerX + (phaseB * (size.width * 0.4f))
        drawLine(
            color = DeckBPrimary,
            start = Offset(xB, centerY),
            end = Offset(xB, size.height - 1.dp.toPx()),
            strokeWidth = 3.dp.toPx()
        )

        // Sync indicator glow if aligned
        if (abs(phaseA - phaseB) < 0.04f) {
            drawCircle(
                color = DjGreenSync,
                radius = 3.dp.toPx(),
                center = Offset(centerX, centerY)
            )
        }
    }
}

@Composable
fun DeckScrollingWaveform(
    state: DeckUiState,
    themeColor: Color,
    modifier: Modifier = Modifier
) {
    val peaks = state.detailWaveformPeaks
    val posSec = state.currentPositionSeconds
    val bpm = state.currentBpm
    val beatIntervalSec = if (bpm > 0) 60.0 / bpm else 0.5

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF11131C))
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(48.dp)) {
            val width = size.width
            val height = size.height
            val centerY = height / 2f
            val centerX = width / 2f

            // Center needle
            drawLine(
                color = Color.White,
                start = Offset(centerX, 0f),
                end = Offset(centerX, height),
                strokeWidth = 2.dp.toPx()
            )

            if (peaks == null || peaks.isEmpty()) {
                // Empty grid line
                drawLine(
                    color = DjBorder,
                    start = Offset(0f, centerY),
                    end = Offset(width, centerY),
                    strokeWidth = 1.dp.toPx()
                )
                return@Canvas
            }

            val pps = 100f // points per second in detailPeaks
            val pixelsPerSecond = 140f // horizontal zoom scale
            val pixelsPerPoint = pixelsPerSecond / pps

            // Render waveform bars centered around posSec
            val visibleSecondsHalf = (width / 2f) / pixelsPerSecond
            val startSec = (posSec - visibleSecondsHalf).coerceAtLeast(0.0)
            val endSec = (posSec + visibleSecondsHalf).coerceAtMost(state.durationSeconds)

            val startPoint = (startSec * pps).toInt()
            val endPoint = (endSec * pps).toInt().coerceAtMost(peaks.size - 1)

            val barSpacing = 2.dp.toPx()
            val step = maxOf(1, (pps / (pixelsPerSecond / barSpacing)).toInt())

            var pt = startPoint
            while (pt <= endPoint) {
                val ptSec = pt / pps
                val screenX = centerX + (ptSec - posSec).toFloat() * pixelsPerSecond
                val amp = peaks[pt]
                val barHeight = (amp * (height * 0.9f)).coerceAtLeast(2.dp.toPx())

                val barColor = when {
                    amp > 0.75f -> Color(0xFFFF5252) // Bass transient peak
                    amp > 0.40f -> themeColor
                    else -> themeColor.copy(alpha = 0.65f)
                }

                drawLine(
                    color = barColor,
                    start = Offset(screenX, centerY - barHeight / 2f),
                    end = Offset(screenX, centerY + barHeight / 2f),
                    strokeWidth = 1.5.dp.toPx()
                )

                pt += step
            }

            // Beat Grid Lines
            if (bpm > 0.0 && beatIntervalSec > 0) {
                val firstBeatSec = (startSec - (startSec % beatIntervalSec))
                var bSec = firstBeatSec
                while (bSec <= endSec) {
                    val bX = centerX + (bSec - posSec).toFloat() * pixelsPerSecond
                    val beatIndex = Math.round(bSec / beatIntervalSec).toInt()
                    val isDownbeat = (beatIndex % 4 == 0)

                    drawLine(
                        color = if (isDownbeat) Color.White.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.35f),
                        start = Offset(bX, 0f),
                        end = Offset(bX, height),
                        strokeWidth = if (isDownbeat) 2.dp.toPx() else 1.dp.toPx()
                    )
                    bSec += beatIntervalSec
                }
            }

            // Loop region overlay
            if (state.loopState.active) {
                val loopStartX = centerX + (state.loopState.startSeconds - posSec).toFloat() * pixelsPerSecond
                val loopEndX = centerX + (state.loopState.endSeconds - posSec).toFloat() * pixelsPerSecond
                if (loopEndX > 0 && loopStartX < width) {
                    drawRect(
                        color = themeColor.copy(alpha = 0.25f),
                        topLeft = Offset(maxOf(0f, loopStartX), 0f),
                        size = Size((loopEndX - loopStartX).coerceAtLeast(4f), height)
                    )
                }
            }

            // Hot Cue markers
            for (cue in state.hotCues) {
                if (cue.positionSeconds >= 0) {
                    val cX = centerX + (cue.positionSeconds - posSec).toFloat() * pixelsPerSecond
                    if (cX in 0f..width) {
                        drawLine(
                            color = Color(cue.colorHex),
                            start = Offset(cX, 0f),
                            end = Offset(cX, height),
                            strokeWidth = 2.dp.toPx()
                        )
                        drawCircle(
                            color = Color(cue.colorHex),
                            radius = 4.dp.toPx(),
                            center = Offset(cX, 6.dp.toPx())
                        )
                    }
                }
            }
        }

        // Header labels: Track Title & Deck Label
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = state.deckId.name.replace("_", " "),
                color = themeColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = " • ${state.metadata?.title ?: "No Track"}",
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 10.sp,
                maxLines = 1
            )
        }
    }
}
