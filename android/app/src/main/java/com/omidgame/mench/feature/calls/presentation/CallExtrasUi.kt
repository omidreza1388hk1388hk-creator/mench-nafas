package com.omidgame.mench.feature.calls.presentation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omidgame.mench.R
import com.omidgame.mench.core.ui.components.MenchSheet
import com.omidgame.mench.feature.calls.domain.AudioRoute
import com.omidgame.mench.feature.calls.domain.CALL_REACTION_EMOJI
import com.omidgame.mench.feature.calls.domain.CallChatLine
import com.omidgame.mench.feature.calls.domain.CallQualityTier
import com.omidgame.mench.feature.calls.domain.CallReaction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.random.Random

/** Picture-in-picture flag, set by MainActivity; the in-call UI hides its controls while true. */
object CallPip {
    val inPip: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val flow: StateFlow<Boolean> get() = inPip
}

private fun routeLabel(route: AudioRoute): Int = when (route) {
    AudioRoute.EARPIECE -> R.string.call_route_earpiece
    AudioRoute.SPEAKER -> R.string.call_route_speaker
    AudioRoute.BLUETOOTH -> R.string.call_route_bluetooth
    AudioRoute.WIRED -> R.string.call_route_wired
}

fun routeIcon(route: AudioRoute) = when (route) {
    AudioRoute.EARPIECE -> Icons.Filled.Call
    AudioRoute.SPEAKER -> Icons.Filled.VolumeUp
    AudioRoute.BLUETOOTH -> Icons.Filled.Bluetooth
    AudioRoute.WIRED -> Icons.Filled.Headset
}

/** "Where should the sound come out?" — one row per available output, the active one ticked. */
@Composable
fun AudioRouteSheet(available: List<AudioRoute>, selected: AudioRoute, onSelect: (AudioRoute) -> Unit, onDismiss: () -> Unit) {
    MenchSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Text(
                stringResource(R.string.call_route_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            available.forEach { route ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onSelect(route)
                            onDismiss()
                        }
                        .padding(horizontal = 24.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Icon(routeIcon(route), contentDescription = null)
                    Text(stringResource(routeLabel(route)), modifier = Modifier.weight(1f))
                    if (route == selected) Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.call_route_selected), tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

/** Three little bars: green / amber / red. Hidden until the first measurement exists. */
@Composable
fun QualityBadge(tier: CallQualityTier, modifier: Modifier = Modifier) {
    if (tier == CallQualityTier.UNKNOWN) return
    val (bars, color, label) = when (tier) {
        CallQualityTier.GOOD -> Triple(3, Color(0xFF34C759), R.string.call_quality_good)
        CallQualityTier.FAIR -> Triple(2, Color(0xFFFFB020), R.string.call_quality_fair)
        else -> Triple(1, Color(0xFFFF453A), R.string.call_quality_poor)
    }
    val description = stringResource(label)
    Row(
        modifier = modifier.semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        for (i in 1..3) {
            Box(
                modifier = Modifier
                    .size(width = 4.dp, height = (4 + i * 4).dp)
                    .background(if (i <= bars) color else Color.White.copy(alpha = 0.25f), RoundedCornerShape(1.dp)),
            )
        }
    }
}

@Composable
fun ReconnectingBanner(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.call_reconnecting),
        color = Color.Black,
        style = MaterialTheme.typography.labelLarge,
        textAlign = TextAlign.Center,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0xFFFFB020))
            .padding(horizontal = 16.dp, vertical = 6.dp),
    )
}

/** Row of reaction emoji; tapping one sends it and closes the bar. */
@Composable
fun ReactionBar(onPick: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        CALL_REACTION_EMOJI.forEach { emoji ->
            Box(modifier = Modifier.size(40.dp).clip(CircleShape).clickable { onPick(emoji) }, contentAlignment = Alignment.Center) {
                Text(emoji, fontSize = 22.sp)
            }
        }
    }
}

/** Emoji drift upward and fade; mine start left of centre, the other person's right of it. */
@Composable
fun FloatingReactions(reactions: kotlinx.coroutines.flow.SharedFlow<CallReaction>, modifier: Modifier = Modifier) {
    val live = remember { mutableStateListOf<Pair<CallReaction, Float>>() }
    LaunchedEffect(reactions) {
        reactions.collect { r ->
            if (live.size > 24) live.removeAt(0)
            live.add(r to Random.nextFloat())
        }
    }
    Box(modifier = modifier.fillMaxSize()) {
        live.toList().forEach { (reaction, jitter) ->
            androidx.compose.runtime.key(reaction.id) {
                val progress = remember { Animatable(0f) }
                LaunchedEffect(Unit) {
                    progress.animateTo(1f, tween(2200, easing = LinearEasing))
                    live.removeAll { it.first.id == reaction.id }
                }
                val horizontal = (if (reaction.fromMe) -1 else 1) * (30 + jitter * 90)
                Text(
                    text = reaction.emoji,
                    fontSize = 44.sp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .offset(x = horizontal.dp, y = (-140).dp)
                        .graphicsLayer {
                            translationY = -progress.value * 520.dp.toPx()
                            alpha = 1f - progress.value
                            val s = 0.8f + progress.value * 0.4f
                            scaleX = s
                            scaleY = s
                        },
                )
            }
        }
    }
}

/** Text chat during the call. Lines live only in memory for the duration of the call. */
@Composable
fun CallChatPanel(lines: List<CallChatLine>, onSend: (String) -> Unit, modifier: Modifier = Modifier) {
    var text by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) listState.animateScrollToItem(lines.lastIndex)
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .imePadding()
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .background(Color(0xE61B0A24))
            .padding(12.dp),
    ) {
        if (lines.isEmpty()) {
            Text(
                stringResource(R.string.call_chat_empty),
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(8.dp),
            )
        } else {
            LazyColumn(state = listState, modifier = Modifier.heightIn(max = 220.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(lines) { line ->
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = if (line.fromMe) Alignment.CenterEnd else Alignment.CenterStart) {
                        Text(
                            text = line.text,
                            color = Color.White,
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (line.fromMe) MaterialTheme.colorScheme.primary.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.16f))
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(500) },
                singleLine = true,
                placeholder = { Text(stringResource(R.string.call_chat_hint), color = Color.White.copy(alpha = 0.6f)) },
                modifier = Modifier.weight(1f).height(52.dp),
            )
            IconButton(
                enabled = text.isNotBlank(),
                onClick = {
                    onSend(text)
                    text = ""
                },
            ) {
                Icon(Icons.Filled.Send, contentDescription = stringResource(R.string.chat_send), tint = Color.White)
            }
        }
    }
}
