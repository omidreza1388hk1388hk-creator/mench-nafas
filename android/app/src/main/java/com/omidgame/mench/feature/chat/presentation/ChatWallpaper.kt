package com.omidgame.mench.feature.chat.presentation

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import com.omidgame.mench.core.ui.components.MenchDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.omidgame.mench.R

/** Chat backgrounds. DEFAULT follows the accent colour; the rest are fixed palettes with a light and a dark variant. */
enum class ChatWallpaper(
    val labelRes: Int,
    private val light: List<Color>?,
    private val dark: List<Color>?,
    /** Phase 3B: a moving layer drawn over the gradient, or null for a static wallpaper. */
    val living: LivingKind? = null,
) {
    DEFAULT(R.string.wallpaper_default, null, null),
    SUNSET(R.string.wallpaper_sunset, listOf(Color(0xFFFFE7D6), Color(0xFFFFB8C9)), listOf(Color(0xFF2B1620), Color(0xFF4A1E36))),
    NIGHT(R.string.wallpaper_night, listOf(Color(0xFFE3E8FF), Color(0xFFB9C4F5)), listOf(Color(0xFF0B1026), Color(0xFF1B2250))),
    LAVENDER(R.string.wallpaper_lavender, listOf(Color(0xFFF1E8FF), Color(0xFFD9C7FF)), listOf(Color(0xFF1C1430), Color(0xFF38275E))),
    MINT(R.string.wallpaper_mint, listOf(Color(0xFFE6FBF1), Color(0xFFBFEFD9)), listOf(Color(0xFF0F2420), Color(0xFF1A4A3E))),
    PLAIN(R.string.wallpaper_plain, null, null),
    AURORA(R.string.wallpaper_aurora, listOf(Color(0xFFEAF6F2), Color(0xFFD3E8F7)), listOf(Color(0xFF07131F), Color(0xFF0F2A3A)), LivingKind.AURORA),
    BUBBLES(R.string.wallpaper_bubbles, listOf(Color(0xFFFFF0F5), Color(0xFFE9F1FF)), listOf(Color(0xFF1A1224), Color(0xFF12203A)), LivingKind.BUBBLES),
    MESH(R.string.wallpaper_mesh, listOf(Color(0xFFFDEFF3), Color(0xFFE8EEFF)), listOf(Color(0xFF14101E), Color(0xFF0E1A2B)), LivingKind.MESH),
    STARRY(R.string.wallpaper_starry, listOf(Color(0xFFE6E9FF), Color(0xFFC9CEF5)), listOf(Color(0xFF050818), Color(0xFF141B45)), LivingKind.STARRY),
    ;

    fun brush(background: Color, accent: Color): Brush {
        val dark = background.luminance() < 0.5f
        val colors = (if (dark) this.dark else this.light)
        return when {
            colors != null -> Brush.verticalGradient(colors)
            this == PLAIN -> Brush.verticalGradient(listOf(background, background))
            else -> Brush.verticalGradient(listOf(background, lerp(background, accent, 0.09f)))
        }
    }

    companion object {
        private const val PREFS = "chat_wallpaper"
        private const val KEY = "wallpaper"

        private fun prefs(context: Context): SharedPreferences =
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        fun get(context: Context): ChatWallpaper =
            runCatching { valueOf(prefs(context).getString(KEY, DEFAULT.name).orEmpty()) }.getOrDefault(DEFAULT)

        fun set(context: Context, wallpaper: ChatWallpaper) {
            prefs(context).edit().putString(KEY, wallpaper.name).apply()
        }

        /** Phase 2A per-chat theme: a wallpaper chosen for one conversation, or null to follow the app-wide setting. */
        fun getFor(context: Context, conversationId: String): ChatWallpaper? =
            prefs(context).getString("wp_$conversationId", null)?.let { runCatching { valueOf(it) }.getOrNull() }

        fun setFor(context: Context, conversationId: String, wallpaper: ChatWallpaper?) {
            val e = prefs(context).edit()
            if (wallpaper == null) e.remove("wp_$conversationId") else e.putString("wp_$conversationId", wallpaper.name)
            e.apply()
        }

        /** Accent colour (ARGB int) chosen for one conversation, or null. */
        fun getAccentFor(context: Context, conversationId: String): Int? =
            prefs(context).takeIf { it.contains("ac_$conversationId") }?.getInt("ac_$conversationId", 0)

        fun setAccentFor(context: Context, conversationId: String, argb: Int?) {
            val e = prefs(context).edit()
            if (argb == null) e.remove("ac_$conversationId") else e.putInt("ac_$conversationId", argb)
            e.apply()
        }
    }
}

/** The saved wallpaper as Compose state; updates live when it is changed from Settings. */
@Composable
fun rememberChatWallpaper(): ChatWallpaper {
    val context = LocalContext.current
    var value by remember { mutableStateOf(ChatWallpaper.get(context)) }
    DisposableEffect(context) {
        val prefs = context.applicationContext.getSharedPreferences("chat_wallpaper", Context.MODE_PRIVATE)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> value = ChatWallpaper.get(context) }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return value
}

@Composable
fun WallpaperDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf(ChatWallpaper.get(context)) }
    val background = MaterialTheme.colorScheme.background
    val accent = MaterialTheme.colorScheme.primary
    MenchDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.us_close)) } },
        title = { Text(stringResource(R.string.wallpaper_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ChatWallpaper.entries.forEach { wallpaper ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selected = wallpaper
                                ChatWallpaper.set(context, wallpaper)
                            },
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(wallpaper.brush(background, accent)),
                        )
                        Text(
                            text = (if (selected == wallpaper) "✓  " else "") + stringResource(wallpaper.labelRes),
                            modifier = Modifier.padding(top = 10.dp),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        },
    )
}

/** Per-chat theme state (Phase 2A): wallpaper + accent for [conversationId], live-updating, falling back to the global wallpaper. */
@Composable
fun rememberChatTheme(conversationId: String): Pair<ChatWallpaper, Color?> {
    val context = LocalContext.current
    val global = rememberChatWallpaper()
    fun read() = ChatWallpaper.getFor(context, conversationId) to ChatWallpaper.getAccentFor(context, conversationId)?.let { Color(it) }
    var value by remember(conversationId) { mutableStateOf(read()) }
    DisposableEffect(context, conversationId) {
        val prefs = context.applicationContext.getSharedPreferences("chat_wallpaper", Context.MODE_PRIVATE)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> value = read() }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return (value.first ?: global) to value.second
}

private val CHAT_ACCENTS = listOf(0xFFB5395B, 0xFF7E57C2, 0xFF1E88E5, 0xFF00897B, 0xFFF4511E, 0xFFFFB300)

/** Theme picker for ONE conversation: wallpaper (or "same as app") and an accent colour for the backdrop wash. */
@Composable
fun ChatThemeDialog(conversationId: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var wallpaper by remember { mutableStateOf(ChatWallpaper.getFor(context, conversationId)) }
    var accent by remember { mutableStateOf(ChatWallpaper.getAccentFor(context, conversationId)) }
    val background = MaterialTheme.colorScheme.background
    val appAccent = MaterialTheme.colorScheme.primary
    MenchDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.chat_theme_title)) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.us_close)) } },
        dismissButton = {
            TextButton(onClick = {
                wallpaper = null; accent = null
                ChatWallpaper.setFor(context, conversationId, null)
                ChatWallpaper.setAccentFor(context, conversationId, null)
            }) { Text(stringResource(R.string.chat_theme_reset)) }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    (if (wallpaper == null) "✓  " else "") + stringResource(R.string.chat_theme_use_global),
                    modifier = Modifier.fillMaxWidth().clickable { wallpaper = null; ChatWallpaper.setFor(context, conversationId, null) }.padding(vertical = 6.dp),
                )
                ChatWallpaper.entries.forEach { option ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { wallpaper = option; ChatWallpaper.setFor(context, conversationId, option) },
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))
                                .background(option.brush(background, accent?.let { Color(it) } ?: appAccent)),
                        )
                        Text(
                            (if (wallpaper == option) "✓  " else "") + stringResource(option.labelRes),
                            modifier = Modifier.padding(top = 10.dp),
                        )
                    }
                }
                BubbleSkinPicker(conversationId)
                Text(stringResource(R.string.chat_theme_accent), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CHAT_ACCENTS.forEach { argb ->
                        val color = Color(argb.toInt())
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier.size(34.dp).clip(androidx.compose.foundation.shape.CircleShape).background(color)
                                .clickable { accent = color.toArgbInt(); ChatWallpaper.setAccentFor(context, conversationId, color.toArgbInt()) },
                            contentAlignment = androidx.compose.ui.Alignment.Center,
                        ) { if (accent == color.toArgbInt()) Text("✓", color = Color.White) }
                    }
                }
            }
        },
    )
}

private fun Color.toArgbInt(): Int = this.toArgb()
