package com.omidgame.mench.feature.chat.presentation

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import android.app.ActivityManager
import android.content.Context
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import com.omidgame.mench.core.ui.theme.rememberReduceMotion
import kotlin.math.cos
import kotlin.math.sin

/**
 * Phase 3B — living chat backgrounds. Drawn behind the chat (below the bubbles), never intercepts touches.
 * AURORA uses an AGSL shader on Android 13+ and soft drifting colour blobs below that.
 * Everything is static under "remove animations" and thinned out on low-RAM devices.
 */
enum class LivingKind { AURORA, BUBBLES, STARRY, HEARTS, MESH }

/** One full loop of the shared clock, in ms. Long and slow on purpose: calm motion, low battery cost. */
private const val LOOP_MS = 60_000

/** Adds a living layer behind this node's content. Pass null for a normal static wallpaper. */
fun Modifier.livingLayer(kind: LivingKind?): Modifier = if (kind == null) this else composed {
    val reduce = rememberReduceMotion()
    val context = LocalContext.current
    val quality = rememberGraphicsQuality()
    val lowRam = remember(quality) { quality.particleFactor(GraphicsQuality.isLowRam(context)) < 0.75f }
    val scheme = MaterialTheme.colorScheme
    val palette = remember(scheme.primary, scheme.secondary, scheme.tertiary) {
        listOf(scheme.primary, scheme.secondary, scheme.tertiary)
    }
    val clock = rememberInfiniteTransition(label = "living")
    // 0..1 loops forever. Read only inside drawBehind, so it redraws without recomposing.
    val t by clock.animateFloat(0f, 1f, infiniteRepeatable(tween(LOOP_MS, easing = LinearEasing), RepeatMode.Restart), label = "livingT")
    val heart = remember { unitHeartPath() }
    // Frame-time guardrail: in Auto quality, if the phone cannot keep up the animation pauses itself until it can.
    var degraded by remember { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.runtime.DisposableEffect(quality, reduce) {
        if (quality != GraphicsQuality.AUTO || reduce) return@DisposableEffect onDispose {}
        val choreographer = android.view.Choreographer.getInstance()
        var lastFrame = 0L
        var sum = 0L
        var count = 0
        val callback = object : android.view.Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (lastFrame != 0L) {
                    sum += frameTimeNanos - lastFrame
                    count++
                    if (count >= 90) {
                        degraded = shouldDegrade(sum / count / 1_000_000.0, degraded)
                        sum = 0L
                        count = 0
                    }
                }
                lastFrame = frameTimeNanos
                choreographer.postFrameCallback(this)
            }
        }
        choreographer.postFrameCallback(callback)
        onDispose { choreographer.removeFrameCallback(callback) }
    }
    val shader = remember(kind) {
        if (kind == LivingKind.AURORA && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) AuroraShader() else null
    }
    val meshShader = remember(kind) {
        if (kind == LivingKind.MESH && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) MeshShader() else null
    }
    drawBehind {
        if (!quality.allowsLivingBackground || degraded) return@drawBehind
        val time = if (reduce) 0.3f else t
        when (kind) {
            LivingKind.AURORA -> if (shader != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                drawAuroraShader(shader, time, palette)
            } else {
                drawAuroraBlobs(time, palette)
            }
            LivingKind.BUBBLES -> drawBubbles(time, palette, if (lowRam) 7 else 14)
            LivingKind.STARRY -> drawStarry(time, palette, if (lowRam) 18 else 36)
            LivingKind.HEARTS -> drawFloatingHearts(time, heart, if (lowRam) 6 else 12)
            LivingKind.MESH -> if (meshShader != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                drawMeshShader(meshShader, time, palette)
            } else {
                drawAuroraBlobs(time, palette)
            }
        }
    }
}

private const val TWO_PI = 6.2831855f

/** Soft radial colour blobs that drift in slow Lissajous loops. Looks like a gentle aurora without a shader. */
private fun DrawScope.drawAuroraBlobs(t: Float, palette: List<Color>) {
    val a = t * TWO_PI
    val blobs = listOf(
        Triple(0.25f + 0.18f * sin(a), 0.22f + 0.10f * cos(a * 2f), 0.65f),
        Triple(0.75f + 0.15f * cos(a), 0.45f + 0.12f * sin(a * 2f), 0.6f),
        Triple(0.5f + 0.2f * sin(a * 3f), 0.8f + 0.08f * cos(a), 0.7f),
    )
    blobs.forEachIndexed { i, (bx, by, radius) ->
        val c = palette[i % palette.size]
        val center = Offset(bx * size.width, by * size.height)
        val r = radius * size.maxDimension * 0.5f
        drawCircle(
            brush = Brush.radialGradient(listOf(c.copy(alpha = 0.22f), Color.Transparent), center = center, radius = r),
            radius = r,
            center = center,
        )
    }
}

/** Translucent circles rising slowly, each with its own speed, size and sideways sway. Positions are pure functions of time. */
private fun DrawScope.drawBubbles(t: Float, palette: List<Color>, count: Int) {
    for (i in 0 until count) {
        val seed = i * 0.6180339f
        val speedLoops = 1 + (i % 3) // whole number of loops so the 60 s clock wraps seamlessly
        val phase = (seed + t * speedLoops) % 1f
        val r = (6f + 22f * ((seed * 7f) % 1f)) * density
        val x = ((seed * 13f) % 1f) * size.width + sin((phase + seed) * TWO_PI) * 14f * density
        val y = size.height + r - phase * (size.height + r * 2f)
        val c = palette[i % palette.size]
        val alpha = 0.16f * (1f - kotlin.math.abs(phase - 0.5f) * 1.2f).coerceIn(0f, 1f)
        drawCircle(c.copy(alpha = alpha), radius = r, center = Offset(x, y))
        drawCircle(Color.White.copy(alpha = alpha * 0.9f), radius = r * 0.28f, center = Offset(x - r * 0.3f, y - r * 0.3f))
    }
}

/** Twinkling stars at fixed positions. */
private fun DrawScope.drawStarry(t: Float, palette: List<Color>, count: Int) {
    val star = lerp(palette[2], Color.White, 0.7f)
    for (i in 0 until count) {
        val seed = i * 0.6180339f
        val x = ((seed * 17f) % 1f) * size.width
        val y = ((seed * 29f) % 1f) * size.height
        val twinkle = 0.5f + 0.5f * sin((t * (2 + i % 3) + seed) * TWO_PI)
        drawCircle(star.copy(alpha = 0.10f + 0.35f * twinkle), radius = (0.8f + 1.4f * ((seed * 5f) % 1f)) * density, center = Offset(x, y))
    }
}

/** Soft white hearts rising slowly with a gentle sway; meant for dark/brand backgrounds such as the onboarding. */
private fun DrawScope.drawFloatingHearts(t: Float, heart: androidx.compose.ui.graphics.Path, count: Int) {
    for (i in 0 until count) {
        val seed = i * 0.6180339f
        val speedLoops = 1 + (i % 2)
        val phase = (seed + t * speedLoops) % 1f
        val s = (9f + 16f * ((seed * 11f) % 1f)) * density
        val x = ((seed * 19f) % 1f) * size.width + sin((phase + seed) * TWO_PI) * 16f * density
        val y = size.height + s - phase * (size.height + s * 2f)
        val alpha = 0.22f * (1f - kotlin.math.abs(phase - 0.5f) * 1.6f).coerceIn(0f, 1f)
        withTransform({
            translate(x, y)
            rotate(sin((phase + seed) * TWO_PI) * 18f, Offset.Zero)
            scale(s, s, Offset.Zero)
        }) { drawPath(heart, Color.White.copy(alpha = alpha)) }
    }
}

// ---- AGSL aurora (Android 13+) ----

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal class AuroraShader {
    val shader = RuntimeShader(
        """
        uniform float2 iResolution;
        uniform float iTime;
        uniform half4 c1;
        uniform half4 c2;
        uniform half4 c3;
        half4 main(float2 fragCoord) {
            float2 uv = fragCoord / iResolution;
            float w1 = sin(uv.x * 3.0 + iTime * 6.2831) * 0.5 + 0.5;
            float w2 = sin(uv.x * 5.0 - iTime * 6.2831 * 2.0 + uv.y * 2.0) * 0.5 + 0.5;
            float band1 = smoothstep(0.0, 1.0, 1.0 - abs(uv.y - (0.32 + 0.12 * w1)) * 2.6);
            float band2 = smoothstep(0.0, 1.0, 1.0 - abs(uv.y - (0.62 + 0.10 * w2)) * 3.2);
            float glow = w1 * w2;
            half3 col = c1.rgb * band1 * 0.55 + c2.rgb * band2 * 0.45 + c3.rgb * glow * 0.12;
            float a = clamp(band1 * 0.32 + band2 * 0.26 + glow * 0.06, 0.0, 0.45);
            return half4(col * a, a);
        }
        """.trimIndent(),
    )
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun DrawScope.drawAuroraShader(aurora: AuroraShader, t: Float, palette: List<Color>) {
    val s = aurora.shader
    s.setFloatUniform("iResolution", size.width, size.height)
    s.setFloatUniform("iTime", t)
    fun set(name: String, c: Color) = s.setFloatUniform(name, c.red, c.green, c.blue, 1f)
    set("c1", palette[0]); set("c2", palette[1]); set("c3", palette[2])
    drawRect(brush = ShaderBrush(s))
}

/** A soft four-colour "mesh gradient": four colour points drift on slow loops and blend smoothly across the screen. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal class MeshShader {
    val shader = RuntimeShader(
        """
        uniform float2 iResolution;
        uniform float iTime;
        uniform half4 c1;
        uniform half4 c2;
        uniform half4 c3;
        uniform half4 c4;
        half4 main(float2 fragCoord) {
            float2 uv = fragCoord / iResolution;
            float a = iTime * 6.2831;
            float2 p1 = float2(0.20 + 0.15 * sin(a), 0.22 + 0.10 * cos(a * 2.0));
            float2 p2 = float2(0.80 + 0.12 * cos(a), 0.30 + 0.12 * sin(a * 2.0));
            float2 p3 = float2(0.30 + 0.12 * sin(a * 2.0), 0.80 + 0.10 * cos(a));
            float2 p4 = float2(0.78 + 0.14 * cos(a * 2.0), 0.82 + 0.08 * sin(a));
            float w1 = 1.0 / (0.03 + dot(uv - p1, uv - p1) * 9.0);
            float w2 = 1.0 / (0.03 + dot(uv - p2, uv - p2) * 9.0);
            float w3 = 1.0 / (0.03 + dot(uv - p3, uv - p3) * 9.0);
            float w4 = 1.0 / (0.03 + dot(uv - p4, uv - p4) * 9.0);
            half3 col = (c1.rgb * w1 + c2.rgb * w2 + c3.rgb * w3 + c4.rgb * w4) / (w1 + w2 + w3 + w4);
            float alpha = 0.42;
            return half4(col * alpha, alpha);
        }
        """.trimIndent(),
    )
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun DrawScope.drawMeshShader(mesh: MeshShader, t: Float, palette: List<Color>) {
    val s = mesh.shader
    s.setFloatUniform("iResolution", size.width, size.height)
    s.setFloatUniform("iTime", t)
    fun set(name: String, c: Color) = s.setFloatUniform(name, c.red, c.green, c.blue, 1f)
    set("c1", palette[0]); set("c2", palette[1]); set("c3", palette[2]); set("c4", lerp(palette[0], palette[2], 0.5f))
    drawRect(brush = ShaderBrush(s))
}
