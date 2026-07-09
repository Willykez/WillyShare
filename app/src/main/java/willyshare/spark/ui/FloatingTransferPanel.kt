package willyshare.spark.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import willyshare.spark.net.TransferProgress
import willyshare.spark.ui.theme.CyanBright
import willyshare.spark.ui.theme.SleekCard
import willyshare.spark.ui.theme.SleekOnSurface
import willyshare.spark.ui.theme.SleekOutline
import willyshare.spark.ui.theme.SleekPrimary
import willyshare.spark.ui.theme.VioletAccent

private val BUBBLE_SIZE = 72.dp
private val BADGE_SIZE = 26.dp

/**
 * Draggable bubble showing whichever transfer is running, regardless of which screen is on
 * top - same idea as Xender's floating progress indicator. Free-drag anywhere on screen,
 * stays wherever it's dropped. Tap opens the full progress screen. No dismiss control by
 * design - it only exists while a transfer is genuinely active and disappears on its own the
 * moment that transfer finishes, so there's nothing to "close" without also losing
 * visibility into a transfer that's still running.
 */
@Composable
fun FloatingTransferPanel(
    kind: ActiveTransferKind,
    progress: TransferProgress,
    onTap: () -> Unit,
) {
    if (kind == ActiveTransferKind.NONE) return

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val maxXPx = with(density) { (maxWidth - BUBBLE_SIZE).toPx() }.coerceAtLeast(0f)
        val maxYPx = with(density) { (maxHeight - BUBBLE_SIZE).toPx() }.coerceAtLeast(0f)
        // Default resting spot: bottom-right, clear of the bottom nav bar.
        var offsetX by remember { mutableStateOf(maxXPx - with(density) { 8.dp.toPx() }) }
        var offsetY by remember { mutableStateOf((maxYPx - with(density) { 100.dp.toPx() }).coerceAtLeast(0f)) }

        val fraction = if (progress.overallTotal > 0) {
            (progress.overallBytes.toFloat() / progress.overallTotal.toFloat()).coerceIn(0f, 1f)
        } else 0f

        // Subtle "this is live" breathing glow on the ring - purely a polish cue, not a
        // functional indicator, so it stays gentle rather than distracting.
        val infiniteTransition = rememberInfiniteTransition(label = "panel_pulse")
        val glowAlpha by infiniteTransition.animateFloat(
            initialValue = 0.35f,
            targetValue = 0.85f,
            animationSpec = infiniteRepeatable(tween(1100), repeatMode = RepeatMode.Reverse),
            label = "panel_glow"
        )

        AnimatedVisibility(
            visible = true,
            enter = scaleIn() + fadeIn(),
            exit = scaleOut() + fadeOut(),
            modifier = Modifier.offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
        ) {
            Box(
                modifier = Modifier
                    .size(BUBBLE_SIZE + 6.dp)
                    .pointerInput(Unit) {
                        detectDragGestures { change, drag ->
                            change.consume()
                            offsetX = (offsetX + drag.x).coerceIn(0f, maxXPx)
                            offsetY = (offsetY + drag.y).coerceIn(0f, maxYPx)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(BUBBLE_SIZE)
                        .shadow(12.dp, CircleShape)
                        .clip(CircleShape)
                        .background(SleekCard.copy(alpha = 0.97f))
                        .border(BorderStroke(1.dp, SleekOutline.copy(alpha = 0.3f)), CircleShape)
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = onTap
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    val outlineColor = SleekOutline
                    val primaryColor = SleekPrimary
                    Canvas(modifier = Modifier.size(BUBBLE_SIZE)) {
                        drawCircle(color = outlineColor.copy(alpha = 0.2f), style = Stroke(width = 5.dp.toPx()))
                        drawArc(
                            brush = Brush.sweepGradient(listOf(VioletAccent, primaryColor, CyanBright, VioletAccent)),
                            startAngle = -90f, sweepAngle = fraction * 360f, useCenter = false,
                            style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round),
                            alpha = glowAlpha
                        )
                    }
                    // Percentage is the one thing that matters at a glance - full focus,
                    // no competing icon crammed into the same small circle.
                    Text(
                        text = "${(fraction * 100).toInt()}%",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = SleekOnSurface
                    )
                }

                // Direction badge lives outside the ring as its own small chip, instead of
                // competing with the percentage for space inside the circle.
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(BADGE_SIZE)
                        .shadow(4.dp, CircleShape)
                        .clip(CircleShape)
                        .background(SleekPrimary)
                        .border(BorderStroke(1.5.dp, SleekCard), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (kind == ActiveTransferKind.SENDING) PulseIcons.Send else PulseIcons.Receive,
                        contentDescription = if (kind == ActiveTransferKind.SENDING) "Sending" else "Receiving",
                        tint = Color.White,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }
    }
}
