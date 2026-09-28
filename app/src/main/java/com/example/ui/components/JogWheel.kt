package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.model.DeckId
import com.example.audio.model.DeckUiState
import com.example.audio.model.PlayState
import com.example.ui.theme.DeckAPrimary
import com.example.ui.theme.DeckBPrimary
import com.example.ui.theme.DjBackground
import com.example.ui.theme.DjBorder
import com.example.ui.theme.DjGreenSync
import com.example.ui.theme.DjRedHot
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun JogWheel(
    deckState: DeckUiState,
    onStartScratch: () -> Unit,
    onUpdateScratch: (deltaAngle: Float, deltaT: Float) -> Unit,
    onEndScratch: () -> Unit,
    onPitchBend: (amount: Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val themeColor = if (deckState.deckId == DeckId.DECK_A) DeckAPrimary else DeckBPrimary
    var previousAngle by remember { mutableFloatStateOf(0f) }
    var previousTime by remember { mutableStateOf(System.currentTimeMillis()) }
    var isOuterRim by remember { mutableStateOf(false) }

    val isPlaying = deckState.playState == PlayState.PLAYING || deckState.playState == PlayState.CUE_AUDITION
    val currentAngle = deckState.jogAngleDegrees

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .shadow(12.dp, CircleShape)
            .clip(CircleShape)
            .background(DjBackground)
            .testTag("jog_wheel_${deckState.deckId.name.lowercase()}")
            .pointerInput(deckState.deckId) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val centerX = size.width / 2f
                        val centerY = size.height / 2f
                        val dx = offset.x - centerX
                        val dy = offset.y - centerY
                        val dist = kotlin.math.sqrt(dx * dx + dy * dy)
                        val radius = size.width / 2f

                        // Outer 25% is pitch bend rim, inner 75% is vinyl scratch platter
                        isOuterRim = dist > (radius * 0.72f)
                        previousAngle = (atan2(dy, dx) * 180f / PI.toFloat())
                        previousTime = System.currentTimeMillis()

                        if (!isOuterRim) {
                            onStartScratch()
                        }
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val centerX = size.width / 2f
                        val centerY = size.height / 2f
                        val dx = change.position.x - centerX
                        val dy = change.position.y - centerY
                        val currentTouchAngle = (atan2(dy, dx) * 180f / PI.toFloat())
                        val now = System.currentTimeMillis()
                        val deltaT = maxOf(0.001f, (now - previousTime) / 1000f)

                        var deltaAngle = currentTouchAngle - previousAngle
                        if (deltaAngle > 180f) deltaAngle -= 360f
                        if (deltaAngle < -180f) deltaAngle += 360f

                        previousAngle = currentTouchAngle
                        previousTime = now

                        if (isOuterRim) {
                            // Pitch bend nudging
                            val nudge = (deltaAngle / 30f).coerceIn(-0.35f, 0.35f)
                            onPitchBend(nudge)
                        } else {
                            onUpdateScratch(deltaAngle, deltaT)
                        }
                    },
                    onDragEnd = {
                        if (isOuterRim) {
                            onPitchBend(0f)
                        } else {
                            onEndScratch()
                        }
                    },
                    onDragCancel = {
                        if (isOuterRim) {
                            onPitchBend(0f)
                        } else {
                            onEndScratch()
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(4.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.width / 2f

            // 1. Outer Knurled Rim
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF2C3144), Color(0xFF161824)),
                    center = center,
                    radius = radius
                ),
                radius = radius,
                center = center
            )

            // Outer rim knurled notch marks
            val notches = 72
            for (i in 0 until notches) {
                val rad = (i * (360f / notches)) * (PI.toFloat() / 180f)
                val p1 = Offset(center.x + (radius - 12.dp.toPx()) * cos(rad), center.y + (radius - 12.dp.toPx()) * sin(rad))
                val p2 = Offset(center.x + (radius - 4.dp.toPx()) * cos(rad), center.y + (radius - 4.dp.toPx()) * sin(rad))
                drawLine(
                    color = if (i % 6 == 0) themeColor.copy(alpha = 0.8f) else Color(0x33FFFFFF),
                    start = p1,
                    end = p2,
                    strokeWidth = if (i % 6 == 0) 2.dp.toPx() else 1.dp.toPx()
                )
            }

            // 2. Vinyl Platter Base
            val platterRadius = radius * 0.75f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF1E212E), Color(0xFF0F1018)),
                    center = center,
                    radius = platterRadius
                ),
                radius = platterRadius,
                center = center
            )

            // Vinyl Record Grooves
            val grooveCount = 8
            for (g in 1..grooveCount) {
                val gRadius = platterRadius * (0.35f + g * (0.6f / grooveCount))
                drawCircle(
                    color = Color(0x1AFFFFFF),
                    radius = gRadius,
                    center = center,
                    style = Stroke(width = 1.dp.toPx())
                )
            }

            // 3. Glowing LED Ring around Platter
            val ledRingRadius = platterRadius + 2.dp.toPx()
            drawCircle(
                color = themeColor.copy(alpha = if (deckState.isScratching) 0.9f else 0.45f),
                radius = ledRingRadius,
                center = center,
                style = Stroke(width = 3.dp.toPx())
            )

            // 4. Center Display / Spindle
            val centerRadius = platterRadius * 0.42f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF12141D), Color(0xFF07080C)),
                    center = center,
                    radius = centerRadius
                ),
                radius = centerRadius,
                center = center
            )
            drawCircle(
                color = DjBorder,
                radius = centerRadius,
                center = center,
                style = Stroke(width = 2.dp.toPx())
            )

            // 5. Rotating Needle Position Indicator
            val needleAngleRad = (currentAngle - 90f) * (PI.toFloat() / 180f)
            val needleStart = Offset(
                center.x + centerRadius * cos(needleAngleRad),
                center.y + centerRadius * sin(needleAngleRad)
            )
            val needleEnd = Offset(
                center.x + (platterRadius - 6.dp.toPx()) * cos(needleAngleRad),
                center.y + (platterRadius - 6.dp.toPx()) * sin(needleAngleRad)
            )
            drawLine(
                color = themeColor,
                start = needleStart,
                end = needleEnd,
                strokeWidth = 4.dp.toPx(),
                cap = StrokeCap.Round
            )

            // Illuminated needle tip
            drawCircle(
                color = Color.White,
                radius = 3.dp.toPx(),
                center = needleEnd
            )

            // 6. Phase Indicator Arc (outer edge)
            val phaseDegrees = (deckState.phaseOffset * 360f)
            drawArc(
                color = if (kotlin.math.abs(deckState.phaseOffset) < 0.05f) DjGreenSync else DjRedHot,
                startAngle = -90f,
                sweepAngle = phaseDegrees,
                useCenter = false,
                topLeft = Offset(center.x - centerRadius + 4.dp.toPx(), center.y - centerRadius + 4.dp.toPx()),
                size = androidx.compose.ui.geometry.Size((centerRadius - 4.dp.toPx()) * 2f, (centerRadius - 4.dp.toPx()) * 2f),
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        // Center HUD Text (Vinyl / Scratch Status & Beat)
        Box(
            modifier = Modifier.padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            val statusText = when {
                deckState.isScratching -> "SCRATCH"
                deckState.isPitchBending -> "NUDGE"
                deckState.slipMode -> "SLIP"
                isPlaying -> "BEAT ${deckState.beatNumber}"
                else -> "CUE"
            }
            val statusColor = when {
                deckState.isScratching -> DjRedHot
                deckState.isPitchBending -> themeColor
                deckState.slipMode -> DjGreenSync
                else -> Color.White
            }
            Text(
                text = statusText,
                color = statusColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }
    }
}
