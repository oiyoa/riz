package com.riz.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

// Default step count for the progress stepper (Preparing → Processing → Saving).
const val DEFAULT_STEPPER_TOTAL = 3

private val StepperDotActive = 14.dp
private val StepperDotInactive = 10.dp
private val StepperConnectorPad = 4.dp

// 3-dot stepper for file processing. Reassures the user that work is moving:
// dots and segments fill left-to-right as LoadingStatus advances.
@Composable
fun ProgressStepper(
    currentStep: Int,
    modifier: Modifier = Modifier,
    totalSteps: Int = DEFAULT_STEPPER_TOTAL,
) {
    val activeColor = MaterialTheme.colorScheme.primary
    val inactiveColor = MaterialTheme.colorScheme.surfaceVariant

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (i in 1..totalSteps) {
            val dotActive = i <= currentStep
            val dotColor by animateColorAsState(
                targetValue = if (dotActive) activeColor else inactiveColor,
                label = "stepDot",
            )
            Box(
                modifier =
                    Modifier
                        .size(if (i == currentStep) StepperDotActive else StepperDotInactive)
                        .clip(CircleShape)
                        .background(dotColor),
            )
            if (i < totalSteps) {
                // Connector is active only when *both* surrounding dots are active.
                val connectorActive = i < currentStep
                val connectorColor by animateColorAsState(
                    targetValue = if (connectorActive) activeColor else inactiveColor,
                    label = "stepConnector",
                )
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .height(2.dp)
                            .padding(horizontal = StepperConnectorPad)
                            .background(connectorColor),
                )
            }
        }
    }
}

// Two-button row for the input/ready screen. Both actions stay tappable
// at all times. While [isDetecting] is true, both slots paint a moving
// two-color wave (primary ↔ secondaryContainer) — both possible outcomes
// are visible at once, so the UI doesn't claim a hierarchy yet. When
// detection settles, each slot's final solid color rises through the
// wave as an overlay: the suggested side ends on primary, the alternate
// on the tonal container. The wave keeps flowing under the overlay so
// the settle reads as the wave being "absorbed" into the chosen color,
// not as a sudden swap.
@Composable
fun SuggestedActionRow(
    isExtractSuggested: Boolean,
    isDetecting: Boolean,
    extractLabel: String,
    compressLabel: String,
    onExtract: () -> Unit,
    onCompress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "waveDrift")
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = WAVE_CYCLE_MS, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "wavePhase",
    )

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SuggestedActionSlot(
            isSuggested = !isExtractSuggested,
            isDetecting = isDetecting,
            wavePhase = wavePhase,
            label = compressLabel,
            icon = Icons.Outlined.Lock,
            onClick = onCompress,
            modifier = Modifier.weight(1f),
        )
        SuggestedActionSlot(
            isSuggested = isExtractSuggested,
            isDetecting = isDetecting,
            wavePhase = wavePhase,
            label = extractLabel,
            icon = Icons.Outlined.LockOpen,
            onClick = onExtract,
            modifier = Modifier.weight(1f),
        )
    }
}

// One full wave shift per cycle. Lower = livelier (risk: reads as loading);
// higher = sleepier.
private const val WAVE_CYCLE_MS = 3000

// Settle from wave → solid. Long enough that the alternate side's bigger
// color shift (toward secondaryContainer) doesn't snap.
private const val SETTLE_MS = 450

// One half-period of the wave in dp. With TileMode.Mirror, two of these =
// one full color cycle visible across the button. Wider = calmer.
private val WAVE_HALF_WAVELENGTH = 120.dp

// How far the wave's "alternate" stop is pulled from primary toward the
// tonal alternate. 0f = no visible wave (pure primary); 1f = full contrast
// (primary ↔ secondaryContainer). Low value keeps the motion present but
// subtle.
private const val WAVE_TONAL_MIX = 0.4f

@Composable
@Suppress("LongParameterList")
private fun SuggestedActionSlot(
    isSuggested: Boolean,
    isDetecting: Boolean,
    wavePhase: Float,
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val secondaryContainer = MaterialTheme.colorScheme.secondaryContainer
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val onSecondaryContainer = MaterialTheme.colorScheme.onSecondaryContainer

    val halfWavelengthPx =
        with(LocalDensity.current) { WAVE_HALF_WAVELENGTH.toPx() }
    val phaseOffsetPx = wavePhase * halfWavelengthPx * 2f
    val waveSoftEnd = lerp(primary, secondaryContainer, WAVE_TONAL_MIX)
    val waveBrush =
        Brush.linearGradient(
            colors = listOf(primary, waveSoftEnd),
            start = Offset(phaseOffsetPx, 0f),
            end = Offset(phaseOffsetPx + halfWavelengthPx, 0f),
            tileMode = TileMode.Mirror,
        )

    val settledContainer = if (isSuggested) primary else secondaryContainer
    val settledContent = if (isSuggested) onPrimary else onSecondaryContainer

    val settleProgress by animateFloatAsState(
        targetValue = if (isDetecting) 0f else 1f,
        animationSpec = tween(SETTLE_MS),
        label = "settle",
    )
    val contentColor by animateColorAsState(
        targetValue = if (isDetecting) onPrimary else settledContent,
        animationSpec = tween(SETTLE_MS),
        label = "content",
    )

    val buttonShape = ButtonDefaults.shape

    Button(
        onClick = onClick,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                contentColor = contentColor,
            ),
        modifier =
            modifier
                .fillMaxWidth()
                .background(brush = waveBrush, shape = buttonShape)
                .background(
                    color = settledContainer.copy(alpha = settleProgress),
                    shape = buttonShape,
                ),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private const val PRIMARY_ACTION_WEIGHT = 1.6f

// Two-button row for the DONE screen. Primary = filled and wider; secondary =
// tonal and narrower. The width delta (1.6:1) reinforces the fill/tonal delta
// so the primary action reads as primary even at a glance. Primary is on the
// trailing edge so RTL/LTR placement stays consistent with system gestures.
@Composable
fun PrimaryActionRow(
    primaryLabel: String,
    primaryIcon: ImageVector,
    onPrimary: () -> Unit,
    secondaryLabel: String,
    secondaryIcon: ImageVector,
    onSecondary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilledTonalButton(
            onClick = onSecondary,
            modifier = Modifier.weight(1f),
        ) {
            Icon(
                imageVector = secondaryIcon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(secondaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Button(
            onClick = onPrimary,
            modifier = Modifier.weight(PRIMARY_ACTION_WEIGHT),
        ) {
            Icon(
                imageVector = primaryIcon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(primaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
