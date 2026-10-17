package com.omidgame.mench.feature.chat.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omidgame.mench.R
import com.omidgame.mench.core.ui.theme.rememberReduceMotion
import kotlinx.coroutines.delay

/** Phase 3A — time-capsule letters: wax-sealed envelopes, a live countdown and an opening animation. */

/** Whole days, hours and minutes left until [unlockAtMillis]; all zero once it has passed. */
internal fun splitCountdown(unlockAtMillis: Long, nowMillis: Long): Triple<Long, Long, Long> {
    val totalMinutes = ((unlockAtMillis - nowMillis).coerceAtLeast(0L)) / 60_000L
    return Triple(totalMinutes / (24 * 60), (totalMinutes / 60) % 24, totalMinutes % 60)
}

/** A countdown that re-reads the clock every 30 s so a waiting letter visibly ticks down while the dialog is open. */
@Composable
fun LetterCountdownText(unlockAtMillis: Long, modifier: Modifier = Modifier) {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(unlockAtMillis) {
        while (true) {
            now = System.currentTimeMillis()
            delay(30_000L)
        }
    }
    val (d, h, m) = splitCountdown(unlockAtMillis, now)
    Text(
        text = stringResource(R.string.letters_countdown, d, h, m),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier,
    )
}

private fun heartPath(): Path = unitHeartPath()

/** The wax seal: a red-ish disc with a heart. [broken] splits it into two drifting halves. */
@Composable
fun WaxSeal(modifier: Modifier = Modifier, size: Dp = 44.dp, broken: Float = 0f) {
    val scheme = MaterialTheme.colorScheme
    val wax = remember(scheme.primary) { lerp(scheme.primary, Color(0xFF8E1B2B), 0.45f) }
    val heart = remember { heartPath() }
    Canvas(modifier = modifier.size(size)) {
        val r = this.size.minDimension / 2f * 0.86f
        val c = Offset(this.size.width / 2f, this.size.height / 2f)
        val gap = r * 0.35f * broken
        val alpha = 1f - 0.6f * broken
        withTransform({ translate(-gap, gap * 0.3f); rotate(-12f * broken, c) }) { drawSealHalf(c, r, wax.copy(alpha = alpha), heart, left = true) }
        withTransform({ translate(gap, gap * 0.3f); rotate(12f * broken, c) }) { drawSealHalf(c, r, wax.copy(alpha = alpha), heart, left = false) }
    }
}

private fun DrawScope.drawSealHalf(c: Offset, r: Float, wax: Color, heart: Path, left: Boolean) {
    // Clip to one half of the seal so the two halves can drift apart.
    val half = Path().apply {
        addRect(androidx.compose.ui.geometry.Rect(if (left) c.x - r * 1.4f else c.x, c.y - r * 1.4f, if (left) c.x else c.x + r * 1.4f, c.y + r * 1.4f))
    }
    androidx.compose.ui.graphics.drawscope.clipPath(half) {
        drawCircle(wax, radius = r, center = c)
        drawCircle(lerp(wax, Color.White, 0.18f), radius = r * 0.78f, center = c, style = androidx.compose.ui.graphics.drawscope.Stroke(width = r * 0.08f))
        withTransform({ translate(c.x, c.y); scale(r * 0.42f, r * 0.42f, Offset.Zero) }) { drawPath(heart, lerp(wax, Color.White, 0.55f)) }
    }
}

/**
 * A closed envelope with its seal (progress 0) that opens to reveal a paper (progress 1):
 * the seal breaks first, then the flap folds back.
 */
@Composable
fun EnvelopeGraphic(progress: Float, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val paper = remember(scheme.surface) { lerp(scheme.surface, Color.White, 0.6f) }
    val body = remember(scheme.primaryContainer) { scheme.primaryContainer }
    val flapColor = remember(scheme.primaryContainer) { lerp(scheme.primaryContainer, Color.Black, 0.08f) }
    val sealBreak = (progress / 0.35f).coerceIn(0f, 1f)
    val flapOpen = ((progress - 0.25f) / 0.6f).coerceIn(0f, 1f)
    val paperRise = ((progress - 0.6f) / 0.4f).coerceIn(0f, 1f)
    Box(modifier = modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxWidth().height(150.dp)) {
            val w = size.width * 0.82f
            val h = size.height * 0.72f
            val left = (size.width - w) / 2f
            val top = size.height * 0.2f
            // Paper slides out of the envelope.
            drawRoundRect(paper, Offset(left + w * 0.08f, top - h * 0.5f * paperRise), Size(w * 0.84f, h * 0.8f), CornerRadius(6.dp.toPx()))
            // Back and front of the envelope.
            drawRoundRect(body, Offset(left, top), Size(w, h), CornerRadius(8.dp.toPx()))
            val front = Path().apply {
                moveTo(left, top + h); lineTo(left + w / 2f, top + h * 0.52f); lineTo(left + w, top + h); close()
            }
            drawPath(front, flapColor)
            // The flap folds from the top edge: scaleY goes 1 -> -1 around the top edge.
            val flapScale = 1f - 2f * flapOpen
            withTransform({ scale(1f, flapScale, Offset(left, top)) }) {
                val flap = Path().apply { moveTo(left, top); lineTo(left + w / 2f, top + h * 0.56f); lineTo(left + w, top); close() }
                drawPath(flap, if (flapScale >= 0f) flapColor else lerp(flapColor, Color.White, 0.2f))
            }
        }
        if (flapOpen < 0.5f) {
            WaxSeal(size = 40.dp, broken = sealBreak, modifier = Modifier.padding(top = 18.dp))
        }
    }
}

/** The reading view of a letter. When [animate] is true the envelope opens first, then the text fades in. */
@Composable
fun LetterReveal(title: String, body: String, animate: Boolean, attachmentId: String? = null) {
    val reduce = rememberReduceMotion()
    val play = animate && !reduce
    val progress = remember(title, body) { Animatable(if (play) 0f else 1f) }
    var showText by remember(title, body) { mutableStateOf(!play) }
    LaunchedEffect(title, body, play) {
        if (play) {
            progress.animateTo(1f, tween(1100, easing = FastOutSlowInEasing))
            showText = true
        }
    }
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        if (play && !showText) EnvelopeGraphic(progress.value)
        AnimatedVisibility(visible = showText, enter = fadeIn(tween(500))) {
            Column {
                Text(
                    text = body,
                    // The cursive family reads as handwriting for Latin text; Persian falls back to the system font.
                    fontFamily = com.omidgame.mench.core.ui.theme.rememberGulzarFamily(),
                    fontSize = 19.sp,
                    lineHeight = 30.sp,
                )
                if (attachmentId != null) {
                    coil.compose.AsyncImage(
                        model = com.omidgame.mench.core.network.AttachmentUrls.content(attachmentId),
                        contentDescription = null,
                        contentScale = androidx.compose.ui.layout.ContentScale.FillWidth,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp)),
                    )
                }
            }
        }
    }
}
