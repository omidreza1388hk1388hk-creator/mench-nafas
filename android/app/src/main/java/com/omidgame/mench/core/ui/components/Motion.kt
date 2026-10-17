package com.omidgame.mench.core.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.omidgame.mench.R
import com.omidgame.mench.core.ui.theme.MenchBrand

/** Shared springs so every animation in the app has the same "feel". */
object MenchMotion {
    fun <T> bouncy() = spring<T>(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
    fun <T> soft() = spring<T>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
}

/** Shrinks slightly while pressed and springs back — makes any tappable feel physical. */
fun Modifier.pressScale(interactionSource: MutableInteractionSource, pressedScale: Float = 0.90f): Modifier =
    composed {
        val pressed by interactionSource.collectIsPressedAsState()
        val scale by animateFloatAsState(
            targetValue = if (pressed) pressedScale else 1f,
            animationSpec = MenchMotion.bouncy(),
            label = "pressScale",
        )
        Modifier.graphicsLayer { scaleX = scale; scaleY = scale }
    }

/**
 * Round gradient send button. Dim and small when there is nothing to send,
 * springs up when text appears, and the icon morphs between send and ✓ (edit mode).
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun AnimatedSendButton(
    canSend: Boolean,
    isEditing: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Phase 2A: long-press opens "send silently / schedule". Null keeps the plain tap-only button. */
    onLongClick: (() -> Unit)? = null,
    /** Phase 3A: long-press opens the message-effect picker. Null = no long-press action. */
    onLongClick: (() -> Unit)? = null,
) {
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val readyScale by animateFloatAsState(
        targetValue = if (canSend) 1f else 0.86f,
        animationSpec = MenchMotion.bouncy(),
        label = "sendReadyScale",
    )
    val fill: Brush = if (canSend) MenchBrand.heroBrush() else SolidColor(MaterialTheme.colorScheme.surfaceContainerHigh)
    val tint = if (canSend) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)

    Box(
        modifier = modifier
            .padding(horizontal = 4.dp)
            .size(44.dp)
            .pressScale(interaction)
            .graphicsLayer { scaleX = readyScale; scaleY = readyScale }
            .clip(CircleShape)
            .background(fill)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                enabled = canSend,
                role = Role.Button,
                onClickLabel = contentDescription,
                onLongClick = onLongClick?.let { longClick ->
                    {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        longClick()
                    }
                },
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = isEditing,
            transitionSpec = {
                (scaleIn(MenchMotion.bouncy(), initialScale = 0.4f) + fadeIn(tween(120))) togetherWith
                    (scaleOut(targetScale = 0.4f) + fadeOut(tween(90)))
            },
            label = "sendIcon",
        ) { editing ->
            Icon(
                imageVector = if (editing) Icons.Filled.Check else Icons.AutoMirrored.Filled.Send,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

/**
 * Animated launch screen: same gradient as the static window background (so the
 * hand-off is seamless), a double-beat heart, expanding halo rings and a fading title.
 */
@Composable
fun AnimatedSplash() {
    val infinite = rememberInfiniteTransition(label = "splash")
    val beat by infinite.animateFloat(
        initialValue = 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1400
                1f at 0
                1.18f at 140
                1f at 280
                1.12f at 420
                1f at 620
                1f at 1400
            },
            repeatMode = RepeatMode.Restart,
        ),
        label = "beat",
    )
    val ring by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Restart),
        label = "ring",
    )
    val titleAlpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) { titleAlpha.animateTo(1f, tween(800, delayMillis = 350)) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(Color(0xFFE23676), Color(0xFFA81D56), Color(0xFF3F0D2B))),
            ),
        contentAlignment = Alignment.Center,
    ) {
        // two staggered halo rings
        listOf(0f, 0.5f).forEach { offset ->
            val p = (ring + offset) % 1f
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .graphicsLayer {
                        val s = 1f + p * 1.6f
                        scaleX = s; scaleY = s
                        alpha = (1f - p) * 0.28f
                    }
                    .background(Color.White, CircleShape),
            )
        }
        Icon(
            imageVector = Icons.Filled.Favorite,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier
                .size(72.dp)
                .graphicsLayer { scaleX = beat; scaleY = beat },
        )
        Text(
            text = stringResource(R.string.app_name),
            color = Color.White,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 96.dp)
                .graphicsLayer { alpha = titleAlpha.value },
        )
    }
}

/**
 * One-shot confetti burst drawn on a Canvas (no assets): ~60 coloured rectangles fall
 * and spin for ~2.6s from the top edge, then the composable draws nothing.
 * Fill the parent with it (e.g. inside a Box) and it clips to that area.
 */
@Composable
fun ConfettiBurst(modifier: Modifier = Modifier, seed: Int = 7) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(2600)) }
    val palette = remember {
        listOf(Color(0xFFFFD166), Color(0xFFEF476F), Color(0xFF06D6A0), Color(0xFF118AB2), Color(0xFFFFFFFF), Color(0xFFFF8FB8))
    }
    val pieces = remember(seed) {
        val rnd = java.util.Random(seed.toLong())
        List(60) {
            floatArrayOf(
                rnd.nextFloat(),                  // x start (0..1)
                0.4f + rnd.nextFloat() * 0.6f,    // fall speed factor
                rnd.nextFloat() * 6.28f,          // rotation phase
                (rnd.nextFloat() - 0.5f) * 0.25f, // horizontal drift
                (rnd.nextInt(palette.size)).toFloat(),
            )
        }
    }
    if (progress.value < 1f) {
        androidx.compose.foundation.Canvas(modifier = modifier) {
            val t = progress.value
            pieces.forEach { p ->
                val x = (p[0] + p[3] * t) * size.width
                val y = -20f + t * p[1] * (size.height + 40f)
                val angle = p[2] + t * 12f
                androidx.compose.ui.graphics.drawscope.rotate(degrees = angle * 57.3f, pivot = androidx.compose.ui.geometry.Offset(x, y)) {
                    drawRect(
                        color = palette[p[4].toInt()].copy(alpha = (1f - t * 0.6f)),
                        topLeft = androidx.compose.ui.geometry.Offset(x - 5f, y - 3f),
                        size = androidx.compose.ui.geometry.Size(10f, 6f),
                    )
                }
            }
        }
    }
}
