package willyshare.spark.ui

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import willyshare.spark.net.TransferProgress
import willyshare.spark.ui.theme.CyanBright
import willyshare.spark.ui.theme.SleekCard
import willyshare.spark.ui.theme.SleekOnSurface
import willyshare.spark.ui.theme.SleekOnSurfaceVariant
import willyshare.spark.ui.theme.SleekOutline
import willyshare.spark.ui.theme.SleekPrimary
import willyshare.spark.ui.theme.VioletAccent

private val BUBBLE_SIZE = 64.dp

/**
 * Draggable bubble showing whichever transfer is running, regardless of which screen is on
 * top - same idea as Xender's floating progress indicator. Free-drag anywhere on screen,
 * stays wherever it's dropped. Tap opens the full progress screen; the small close button
 * only hides the bubble - it does NOT cancel the transfer, which keeps running either way.
 */
@Composable
fun FloatingTransferPanel(
    kind: ActiveTransferKind,
    progress: TransferProgress,
    onTap: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (kind == ActiveTransferKind.NONE) return

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val maxXPx = with(density) { (maxWidth - BUBBLE_SIZE).toPx() }.coerceAtLeast(0f)
        val maxYPx = with(density) { (maxHeight - BUBBLE_SIZE).toPx() }.coerceAtLeast(0f)
        // Default resting spot: bottom-right, clear of the bottom nav bar.
        var offsetX by remember { mutableStateOf(maxXPx - with(density) { 8.dp.toPx() }) }
        var offsetY by remember { mutableStateOf((maxYPx - with(density) { 96.dp.toPx() }).coerceAtLeast(0f)) }

        val fraction = if (progress.overallTotal > 0) {
            (progress.overallBytes.toFloat() / progress.overallTotal.toFloat()).coerceIn(0f, 1f)
        } else 0f

        AnimatedVisibility(
            visible = true,
            enter = scaleIn() + fadeIn(),
            exit = scaleOut() + fadeOut(),
            modifier = Modifier.offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
        ) {
            Box(
                modifier = Modifier
                    .size(BUBBLE_SIZE)
                    .pointerInput(Unit) {
                        detectDragGestures { change, drag ->
                            change.consume()
                            offsetX = (offsetX + drag.x).coerceIn(0f, maxXPx)
                            offsetY = (offsetY + drag.y).coerceIn(0f, maxYPx)
                        }
                    }
            ) {
                Box(
                    modifier = Modifier
                        .size(BUBBLE_SIZE)
                        .shadow(10.dp, CircleShape)
                        .clip(CircleShape)
                        .background(SleekCard.copy(alpha = 0.96f))
                        .border(BorderStroke(1.dp, SleekOutline.copy(alpha = 0.3f)), CircleShape)
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = onTap
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(BUBBLE_SIZE)) {
                        drawCircle(color = SleekOutline.copy(alpha = 0.2f), style = Stroke(width = 4.dp.toPx()))
                        drawArc(
                            brush = Brush.sweepGradient(listOf(VioletAccent, SleekPrimary, CyanBright, VioletAccent)),
                            startAngle = -90f, sweepAngle = fraction * 360f, useCenter = false,
                            style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                    Icon(
                        imageVector = if (kind == ActiveTransferKind.SENDING) PulseIcons.Send else PulseIcons.Receive,
                        contentDescription = if (kind == ActiveTransferKind.SENDING) "Sending" else "Receiving",
                        tint = SleekPrimary,
                        modifier = Modifier.size(16.dp).offset(y = (-10).dp)
                    )
                    Text(
                        text = "${(fraction * 100).toInt()}%",
                        fontSize = 12.sp,
                        color = SleekOnSurface,
                        modifier = Modifier.offset(y = 8.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 4.dp, y = (-4).dp)
                        .size(18.dp)
                        .shadow(2.dp, CircleShape)
                        .clip(CircleShape)
                        .background(SleekCard)
                        .border(BorderStroke(1.dp, SleekOutline.copy(alpha = 0.3f)), CircleShape)
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = onDismiss
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Hide",
                        tint = SleekOnSurfaceVariant,
                        modifier = Modifier.size(11.dp)
                    )
                }
            }
        }
    }
}
