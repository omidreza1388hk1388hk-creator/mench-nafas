package com.omidgame.mench.feature.chat.presentation

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omidgame.mench.R
import com.omidgame.mench.core.ui.theme.rememberGulzarFamily

/** Remembers, on this phone only, which surprise messages were already opened. */
private object OpenedSurprises {
    private const val PREFS = "mench_opened_surprises"
    fun isOpen(c: Context, id: String) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet("ids", emptySet())!!.contains(id)
    fun markOpen(c: Context, id: String) {
        val p = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        p.edit().putStringSet("ids", p.getStringSet("ids", emptySet())!!.toMutableSet().apply { add(id) }).apply()
    }
}

/**
 * Phase 3 — a surprise message (usually scheduled for later). The receiver sees a gift until they tap
 * it; opening plays a small reveal and the text is then kept readable in the bubble. The sender sees
 * the text with a small gift label.
 */
@Composable
fun SurpriseBubbleContent(messageId: String, text: String, isOwn: Boolean) {
    val context = LocalContext.current
    val gulzar = rememberGulzarFamily()
    var opened by remember(messageId) { mutableStateOf(isOwn || OpenedSurprises.isOpen(context, messageId)) }
    var revealing by remember { mutableStateOf(false) }
    if (!opened) {
        Column(
            modifier = Modifier.clickable { revealing = true }.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("🎁", fontSize = 44.sp)
            Text(stringResource(R.string.surprise_tap_to_open), style = MaterialTheme.typography.labelLarge)
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("🎁 " + stringResource(R.string.surprise_label), style = MaterialTheme.typography.labelSmall)
            Text(text, fontFamily = gulzar, fontSize = 18.sp, lineHeight = 34.sp)
        }
    }
    if (revealing) {
        SurpriseRevealDialog(text = text, onDone = {
            OpenedSurprises.markOpen(context, messageId)
            opened = true
            revealing = false
        })
    }
}

@Composable
private fun SurpriseRevealDialog(text: String, onDone: () -> Unit) {
    val gulzar = rememberGulzarFamily()
    val giftScale = remember { Animatable(0f) }
    val tilt = remember { Animatable(0f) }
    val textAlpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        giftScale.animateTo(1.4f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
        tilt.animateTo(12f, tween(120)); tilt.animateTo(-12f, tween(160)); tilt.animateTo(0f, tween(120))
        textAlpha.animateTo(1f, tween(700))
    }
    com.omidgame.mench.core.ui.components.MenchDialog(
        onDismissRequest = onDone,
        title = null,
        text = {
            Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("🎁", fontSize = 64.sp, modifier = Modifier.scale(giftScale.value).rotate(tilt.value))
                Text(text, fontFamily = gulzar, fontSize = 22.sp, lineHeight = 40.sp, textAlign = TextAlign.Center, modifier = Modifier.alpha(textAlpha.value))
            }
        },
        confirmButton = { TextButton(onClick = onDone) { Text("♥") } },
    )
}
