package com.omidgame.mench.feature.chat.presentation

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.omidgame.mench.R

/** Phase 3 — per-chat bubble skin. Local to this phone (same plain-SharedPreferences style as the chat wallpaper). */
enum class BubbleSkin(val key: String, val labelRes: Int) {
    CLASSIC("classic", R.string.skin_classic),
    GLASS("glass", R.string.skin_glass),
    NEON("neon", R.string.skin_neon),
    PAPER("paper", R.string.skin_paper);

    companion object {
        fun fromKey(key: String?): BubbleSkin = values().firstOrNull { it.key == key } ?: CLASSIC
    }
}

object BubbleSkins {
    const val PREFS = "chat_bubble_skins"
    fun get(context: Context, conversationId: String): BubbleSkin =
        BubbleSkin.fromKey(context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(conversationId, null))

    fun set(context: Context, conversationId: String, skin: BubbleSkin) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(conversationId, skin.key).apply()
    }
}

/** What a skin changes about one bubble: its shape, its background/border/glow, and its text colour. */
class SkinStyle(val shape: CornerBasedShape, val modifier: Modifier, val contentColor: Color)

@Composable
fun rememberBubbleSkin(conversationId: String): BubbleSkin {
    val context = LocalContext.current
    var skin by remember(conversationId) { mutableStateOf(BubbleSkins.get(context, conversationId)) }
    DisposableEffect(context, conversationId) {
        val prefs = context.applicationContext.getSharedPreferences(BubbleSkins.PREFS, Context.MODE_PRIVATE)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> skin = BubbleSkins.get(context, conversationId) }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return skin
}

/** Null for CLASSIC (the caller keeps its own drawing). */
@Composable
fun skinStyle(skin: BubbleSkin, isOwn: Boolean, showTail: Boolean, primary: Color, dark: Boolean): SkinStyle? {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val surface = MaterialTheme.colorScheme.surface
    return when (skin) {
        BubbleSkin.CLASSIC -> null
        BubbleSkin.GLASS -> {
            val tail = if (showTail) 6.dp else 22.dp
            val shape = roundedFor(isOwn, 22.dp, tail)
            val fill = if (isOwn) primary.copy(alpha = if (dark) 0.38f else 0.30f) else surface.copy(alpha = if (dark) 0.42f else 0.55f)
            val rim = Brush.linearGradient(listOf(Color.White.copy(alpha = 0.75f), Color.White.copy(alpha = 0.12f)))
            SkinStyle(shape, Modifier.background(fill, shape).border(1.dp, rim, shape), onSurface)
        }
        BubbleSkin.NEON -> {
            val tail = if (showTail) 4.dp else 16.dp
            val shape = roundedFor(isOwn, 16.dp, tail)
            val glow = if (isOwn) primary else Color(0xFF26C6DA)
            SkinStyle(
                shape,
                Modifier
                    .shadow(10.dp, shape, clip = false, ambientColor = glow.copy(alpha = 0.9f), spotColor = glow)
                    .background(Color(0xFF0C0A14), shape)
                    .border(1.5.dp, glow, shape),
                Color(0xFFF3EFFF),
            )
        }
        BubbleSkin.PAPER -> {
            val tail = if (showTail) 2.dp else 8.dp
            val shape = roundedFor(isOwn, 8.dp, tail)
            val paper = if (dark) (if (isOwn) Color(0xFF3A3328) else Color(0xFF2B2620)) else (if (isOwn) Color(0xFFFFF1CC) else Color(0xFFFFFAEB))
            SkinStyle(
                shape,
                Modifier
                    .shadow(1.dp, shape, clip = false)
                    .background(paper, shape)
                    .border(0.6.dp, if (dark) Color(0x33FFFFFF) else Color(0x33806040), shape),
                if (dark) Color(0xFFEADFC8) else Color(0xFF3B2F2F),
            )
        }
    }
}

private fun roundedFor(isOwn: Boolean, radius: androidx.compose.ui.unit.Dp, tail: androidx.compose.ui.unit.Dp): CornerBasedShape =
    if (isOwn) RoundedCornerShape(topStart = radius, topEnd = radius, bottomEnd = tail, bottomStart = radius)
    else RoundedCornerShape(topStart = radius, topEnd = radius, bottomEnd = radius, bottomStart = tail)

/** Skin picker rows with a tiny live preview, shown inside the per-chat theme dialog. */
@Composable
fun BubbleSkinPicker(conversationId: String) {
    val context = LocalContext.current
    var selected by remember(conversationId) { mutableStateOf(BubbleSkins.get(context, conversationId)) }
    val primary = MaterialTheme.colorScheme.primary
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.skin_title), style = MaterialTheme.typography.labelLarge)
        BubbleSkin.values().forEach { skin ->
            Row(
                modifier = Modifier.fillMaxWidth().clickable { selected = skin; BubbleSkins.set(context, conversationId, skin) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val style = skinStyle(skin, true, true, primary, dark)
                val previewShape = style?.shape ?: RoundedCornerShape(20.dp, 20.dp, 5.dp, 20.dp)
                val preview = style?.modifier ?: Modifier.background(Brush.linearGradient(listOf(primary, primary.copy(alpha = 0.75f))), previewShape)
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier.size(width = 72.dp, height = 36.dp).then(preview),
                    contentAlignment = Alignment.Center,
                ) { Text("♥", color = style?.contentColor ?: MaterialTheme.colorScheme.onPrimary) }
                Text((if (selected == skin) "✓  " else "") + stringResource(skin.labelRes))
            }
        }
    }
}
