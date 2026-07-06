package willyshare.spark.ui.modifiers

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import willyshare.spark.ui.theme.LocalBlurBars

/**
 * Dual-edge blur shader: content fades from sharp to blurred as it nears the top/bottom
 * band, instead of an abrupt clip. Mirrors the effect behind the reference app's floating
 * pill nav bar and top bar so lists feel like they're gliding under glass.
 */
private val dualEdgeBlurAgsl =
    """
    uniform shader content;
    uniform float blurRadius;
    uniform float topHeight;
    uniform float bottomHeight;
    uniform float contentHeight;

    half4 main(float2 fragCoord) {
        float topProgress = topHeight > 0.0
            ? 1.0 - clamp(fragCoord.y / topHeight, 0.0, 1.0)
            : 0.0;
        float bottomProgress = bottomHeight > 0.0
            ? 1.0 - clamp((contentHeight - fragCoord.y) / bottomHeight, 0.0, 1.0)
            : 0.0;

        float topBlur = pow(topProgress, 2.5);
        float bottomBlur = pow(bottomProgress, 2.5);
        float progress = max(topBlur, bottomBlur);
        float radius = progress * blurRadius;

        if (radius <= 0.0) {
            return content.eval(fragCoord);
        }

        half4 accum = half4(0.0);
        float weightSum = 0.0;
        float dither = fract(sin(dot(fragCoord, float2(12.9898, 78.233))) * 43758.5453);
        float2 jitter = float2(dither - 0.5, fract(dither * 1.618) - 0.5);

        const int SAMPLES = 5;
        float offsetScale = radius / float(SAMPLES);

        for (int x = -SAMPLES; x <= SAMPLES; x++) {
            for (int y = -SAMPLES; y <= SAMPLES; y++) {
                float2 offset = (float2(float(x), float(y)) + jitter) * offsetScale;
                float distSq = dot(offset, offset);
                float radiusSq = radius * radius;
                if (distSq <= radiusSq) {
                    float weight = exp(-3.0 * distSq / radiusSq);
                    accum += content.eval(fragCoord + offset) * weight;
                    weightSum += weight;
                }
            }
        }

        return accum / weightSum;
    }
    """.trimIndent()

/**
 * Applies a progressive blur + soft gradient scrim to the top and/or bottom edges of
 * whatever it's attached to (typically a LazyColumn). Below API 33, or when blur bars
 * are turned off in Settings, falls back to a plain gradient scrim so content still reads
 * cleanly behind the transparent nav pill.
 */
fun Modifier.progressiveEdgeBlur(
    blurRadius: Float,
    topHeight: Float = 0f,
    bottomHeight: Float = 0f,
    scrimColor: Color,
    scrimAlpha: Float = 0.32f,
): Modifier =
    composed {
        val finalAlpha = if (blurRadius <= 0f) 1f else scrimAlpha
        val scrim = remember(scrimColor, finalAlpha) { scrimColor.copy(alpha = finalAlpha) }

        val blurModifier =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && blurRadius > 0f) {
                val shader = remember { RuntimeShader(dualEdgeBlurAgsl) }
                Modifier.graphicsLayer {
                    shader.setFloatUniform("blurRadius", blurRadius)
                    shader.setFloatUniform("topHeight", topHeight)
                    shader.setFloatUniform("bottomHeight", bottomHeight)
                    shader.setFloatUniform("contentHeight", size.height)
                    renderEffect = RenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect()
                }
            } else {
                Modifier
            }

        val gradientModifier =
            Modifier.drawWithContent {
                drawContent()
                if (topHeight > 0f) {
                    drawRect(
                        brush = Brush.verticalGradient(listOf(scrim, Color.Transparent), endY = topHeight),
                    )
                }
                if (bottomHeight > 0f) {
                    drawRect(
                        brush =
                            Brush.verticalGradient(
                                listOf(Color.Transparent, scrim),
                                startY = size.height - bottomHeight,
                            ),
                    )
                }
            }

        this.then(blurModifier).then(gradientModifier)
    }

/** Height of [willyshare.spark.ui.SleekBottomNav]'s pill + its padding, for sizing the bottom blur band. */
val PillBottomBarHeight: Dp = 64.dp
val PillBottomScrimExtra: Dp = 24.dp

/**
 * Convenience for screens with the floating pill nav: computes blur band sizes from the
 * real system bar insets and returns ready-to-use params, honoring the Settings toggle
 * ([LocalBlurBars]) and falling back gracefully pre-API 33.
 */
@Composable
fun rememberBottomEdgeBlurModifier(
    scrimColor: Color,
    bottomExtra: Dp = PillBottomBarHeight + PillBottomScrimExtra,
    blurRadius: Float = 80f,
    scrimAlpha: Float = 0.32f,
): Modifier {
    val enabled = LocalBlurBars.current
    val density = LocalDensity.current
    val navBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomPx = with(density) { (navBarInset + bottomExtra).toPx() }
    val radius = if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) blurRadius else 0f
    val alpha = if (!enabled) (scrimAlpha + 0.15f).coerceAtMost(0.65f) else scrimAlpha
    return Modifier.progressiveEdgeBlur(
        blurRadius = radius,
        bottomHeight = bottomPx,
        scrimColor = scrimColor,
        scrimAlpha = alpha,
    )
}

/** Same idea for a top app bar / status bar band. */
@Composable
fun rememberTopEdgeBlurModifier(
    scrimColor: Color,
    topExtra: Dp = 56.dp,
    blurRadius: Float = 80f,
    scrimAlpha: Float = 0.32f,
): Modifier {
    val enabled = LocalBlurBars.current
    val density = LocalDensity.current
    val statusBarInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val topPx = with(density) { (statusBarInset + topExtra).toPx() }
    val radius = if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) blurRadius else 0f
    val alpha = if (!enabled) (scrimAlpha + 0.15f).coerceAtMost(0.65f) else scrimAlpha
    return Modifier.progressiveEdgeBlur(
        blurRadius = radius,
        topHeight = topPx,
        scrimColor = scrimColor,
        scrimAlpha = alpha,
    )
}
