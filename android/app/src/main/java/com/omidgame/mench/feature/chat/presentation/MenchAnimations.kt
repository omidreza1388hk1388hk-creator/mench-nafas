package com.omidgame.mench.feature.chat.presentation

import androidx.compose.runtime.setValue
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.omidgame.mench.core.ui.theme.rememberReduceMotion
import kotlin.math.cos
import kotlin.math.sin

/**
 * Phase 3B — MENCH's small animation catalog. These are drawn in code (no Lottie dependency, no animation files),
 * so they take the theme colours, stay crisp at any size and cost almost nothing. All of them hold still under
 * "remove animations".
 */

/** The two segments of a check mark in a 0..1 box: (0.20,0.55) → (0.42,0.75) → (0.80,0.30). */
private val checkPoints = listOf(0.20f to 0.55f, 0.42f to 0.75f, 0.80f to 0.30f)

/**
 * The end points to draw for a check mark that is [progress] (0..1) complete: a flat list of x,y pairs for the polyline
 * start → corner [→ tip]. The first 40 % of the progress draws the short stroke, the rest the long one.
 */
internal fun checkProgressPoints(progress: Float): List<Pair<Float, Float>> {
    val p = progress.coerceIn(0f, 1f)
    val (x0, y0) = checkPoints[0]
    val (x1, y1) = checkPoints[1]
    val (x2, y2) = checkPoints[2]
    return if (p <= 0.4f) {
        val t = p / 0.4f
        listOf(x0 to y0, (x0 + (x1 - x0) * t) to (y0 + (y1 - y0) * t))
    } else {
        val t = (p - 0.4f) / 0.6f
        listOf(x0 to y0, x1 to y1, (x1 + (x2 - x1) * t) to (y1 + (y2 - y1) * t))
    }
}

/** A check mark that draws itself once when it appears. */
@Composable
fun DrawnCheck(modifier: Modifier = Modifier, size: Dp = 24.dp, color: Color = MaterialTheme.colorScheme.primary) {
    val reduce = rememberReduceMotion()
    val progress = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(Unit) { if (!reduce) progress.animateTo(1f, tween(450, easing = FastOutSlowInEasing)) }
    Canvas(modifier = modifier.size(size)) {
        val pts = checkProgressPoints(progress.value)
        val stroke = Stroke(width = this.size.minDimension * 0.12f, cap = StrokeCap.Round)
        for (i in 0 until pts.size - 1) {
            drawLine(
                color,
                Offset(pts[i].first * this.size.width, pts[i].second * this.size.height),
                Offset(pts[i + 1].first * this.size.width, pts[i + 1].second * this.size.height),
                strokeWidth = stroke.width,
                cap = StrokeCap.Round,
            )
        }
    }
}

/** A heart that beats with the same lub-dub rhythm as the heartbeat feature. */
@Composable
fun PulsingHeart(modifier: Modifier = Modifier, size: Dp = 56.dp, color: Color = MaterialTheme.colorScheme.primary) {
    val reduce = rememberReduceMotion()
    val transition = rememberInfiniteTransition(label = "pulsingHeart")
    val cycle by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart), label = "pulsingHeartCycle")
    val heart = remember { unitHeartPath() }
    Canvas(modifier = modifier.size(size)) {
        val pulse = if (reduce) 0f else beatPulse(cycle)
        val s = this.size.minDimension * 0.5f * (1f + 0.14f * pulse)
        withTransform({
            translate(this.size.width / 2f, this.size.height / 2f)
            scale(s, s, Offset.Zero)
        }) { drawPath(heart, color) }
    }
}

/** Small stars flying outwards from the centre and fading — a "celebration" for milestones. Plays once per appearance. */
@Composable
fun SparkleBurst(modifier: Modifier = Modifier, size: Dp = 80.dp, color: Color = MaterialTheme.colorScheme.tertiary) {
    val reduce = rememberReduceMotion()
    val progress = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(Unit) { if (!reduce) progress.animateTo(1f, tween(1100, easing = FastOutSlowInEasing)) }
    Canvas(modifier = modifier.size(size)) {
        val p = progress.value
        if (p >= 1f) return@Canvas
        val c = Offset(this.size.width / 2f, this.size.height / 2f)
        val reach = this.size.minDimension / 2f
        val count = 10
        for (i in 0 until count) {
            val angle = i * (6.2831855f / count)
            val dist = reach * (0.25f + 0.75f * p)
            val alpha = (1f - p).coerceIn(0f, 1f)
            val r = (2f + 3f * (1f - p)) * density
            drawCircle(color.copy(alpha = alpha), r, Offset(c.x + cos(angle) * dist, c.y + sin(angle) * dist))
        }
    }
}

/** An empty-state block: a beating heart above a short centred message. */
@Composable
fun EmptyHeartState(text: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PulsingHeart()
        Text(text, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * A number whose digits roll like an odometer when the value changes (and roll up from 0 the first time it appears).
 * Each digit animates on its own, so 99 → 100 turns all three. Holds still under "remove animations".
 */
@Composable
fun RollingNumber(
    value: Long,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    style: androidx.compose.ui.text.TextStyle = androidx.compose.material3.LocalTextStyle.current,
    fontSize: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
    fontWeight: androidx.compose.ui.text.font.FontWeight? = null,
) {
    val reduce = rememberReduceMotion()
    var shown by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(if (reduce) value else 0L) }
    androidx.compose.runtime.LaunchedEffect(value) { shown = value }
    androidx.compose.foundation.layout.Row(modifier = modifier) {
        shown.toString().forEachIndexed { index, ch ->
            androidx.compose.animation.AnimatedContent(
                targetState = ch,
                transitionSpec = {
                    (androidx.compose.animation.slideInVertically { it } + androidx.compose.animation.fadeIn()) togetherWith
                        (androidx.compose.animation.slideOutVertically { -it } + androidx.compose.animation.fadeOut())
                },
                label = "digit$index",
            ) { digit ->
                Text(text = digit.toString(), color = color, style = style, fontSize = fontSize, fontWeight = fontWeight)
            }
        }
    }
}
