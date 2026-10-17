package com.omidgame.mench.feature.chat.presentation

import android.net.Uri
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.omidgame.mench.R
import com.omidgame.mench.core.network.AttachmentUrls
import com.omidgame.mench.feature.chat.domain.DomainMessageKind
import com.omidgame.mench.feature.chat.domain.Message
import com.omidgame.mench.feature.chat.domain.MessageAttachment
import com.omidgame.mench.feature.chat.domain.TogetherSignal
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlin.math.abs

/**
 * Phase 3 — Together mode: two people watch/listen to the same video or audio from the chat, kept in
 * sync live. Play, pause and seeking by either side are mirrored to the other. The server only relays
 * these small events; the media itself is streamed from the normal attachment endpoint on each phone.
 */
@Composable
fun TogetherHost(
    messages: List<Message>,
    signals: Flow<TogetherSignal>,
    showPicker: Boolean,
    onPickerDismiss: () -> Unit,
    onSend: (kind: String, attachmentId: String?, positionMs: Long, playing: Boolean) -> Unit,
) {
    var session by remember { mutableStateOf<MessageAttachment?>(null) }
    var iAmHost by remember { mutableStateOf(false) }
    var invite by remember { mutableStateOf<TogetherSignal?>(null) }
    val media = remember(messages) {
        messages.filter { (it.kind == DomainMessageKind.VIDEO || it.kind == DomainMessageKind.AUDIO) && it.attachment?.id != null && it.deletedAtEpochMillis == null }
            .asReversed()
    }
    LaunchedEffect(signals, media) {
        signals.collect { s ->
            when (s.kind) {
                "start" -> if (session == null) invite = s
                "stop" -> { invite = null; if (session?.id == s.attachmentId || s.attachmentId == null) session = null }
            }
        }
    }

    if (showPicker) {
        com.omidgame.mench.core.ui.components.MenchDialog(
            onDismissRequest = onPickerDismiss,
            title = { Text(stringResource(R.string.together_pick_title)) },
            text = {
                if (media.isEmpty()) Text(stringResource(R.string.together_pick_empty))
                else Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    media.forEach { m ->
                        val a = m.attachment ?: return@forEach
                        Text(
                            (if (m.kind == DomainMessageKind.VIDEO) "🎬  " else "🎧  ") + a.originalFilename,
                            modifier = Modifier.fillMaxWidth().clickable {
                                onPickerDismiss()
                                iAmHost = true
                                session = a
                                onSend("start", a.id, 0L, false)
                            }.padding(vertical = 10.dp),
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = onPickerDismiss) { Text(stringResource(R.string.us_cancel)) } },
        )
    }
    invite?.let { inv ->
        val target = media.firstOrNull { it.attachment?.id == inv.attachmentId }?.attachment
        com.omidgame.mench.core.ui.components.MenchDialog(
            onDismissRequest = { invite = null },
            title = { Text(stringResource(R.string.together_invite_title)) },
            text = { Text(stringResource(if (target != null) R.string.together_invite_body else R.string.together_invite_missing)) },
            confirmButton = {
                TextButton(enabled = target != null, onClick = { iAmHost = false; session = target; invite = null }) { Text(stringResource(R.string.together_join)) }
            },
            dismissButton = { TextButton(onClick = { invite = null }) { Text(stringResource(R.string.together_not_now)) } },
        )
    }
    session?.let { a ->
        TogetherPlayer(
            attachment = a,
            isHost = iAmHost,
            signals = signals,
            onSend = onSend,
            onClose = { onSend("stop", a.id, 0L, false); session = null },
        )
    }
}

@Composable
private fun TogetherPlayer(
    attachment: MessageAttachment,
    isHost: Boolean,
    signals: Flow<TogetherSignal>,
    onSend: (kind: String, attachmentId: String?, positionMs: Long, playing: Boolean) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val mediaAuth = LocalMediaAuth.current
    var headers by remember { mutableStateOf<Map<String, String>?>(null) }
    var view by remember { mutableStateOf<VideoView?>(null) }
    var prepared by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(0L) }
    var pending by remember { mutableStateOf<TogetherSignal?>(null) }
    val isAudio = attachment.mimeType.startsWith("audio/")

    LaunchedEffect(attachment.id) { headers = if (attachment.localContentUri != null) emptyMap() else mediaAuth() }

    fun apply(s: TogetherSignal) {
        val v = view ?: return
        if (!prepared) { pending = s; return }
        // Compensate for network delay only when the clocks agree to within a few seconds.
        val age = System.currentTimeMillis() - s.sentAtEpochMillis
        val target = s.positionMs + if (s.playing && age in 0..4000) age else 0
        if (abs(v.currentPosition - target) > 1500) v.seekTo(target.toInt())
        if (s.playing && !v.isPlaying) v.start() else if (!s.playing && v.isPlaying) v.pause()
        playing = s.playing
    }

    LaunchedEffect(signals, view, prepared) {
        signals.collect { s -> if (s.kind == "sync" && s.attachmentId == attachment.id) apply(s) }
    }
    LaunchedEffect(prepared) { if (prepared) pending?.let { apply(it); pending = null } }
    LaunchedEffect(view, prepared, playing) {
        while (true) {
            val v = view
            if (v != null && prepared) {
                position = v.currentPosition.toLong()
                // The host re-announces its position every ~5 s so small drifts get corrected.
                if (isHost && playing && (position / 500) % 10L == 0L) onSend("sync", attachment.id, position, true)
            }
            delay(500)
        }
    }

    fun local(newPlaying: Boolean, newPosition: Long) {
        val v = view ?: return
        if (newPlaying) v.start() else v.pause()
        v.seekTo(newPosition.toInt())
        playing = newPlaying
        onSend("sync", attachment.id, newPosition, newPlaying)
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true)) {
        Column(Modifier.fillMaxSize().background(Color.Black).statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.us_cancel), tint = Color.White) }
                Text(stringResource(R.string.together_title), color = Color.White, style = MaterialTheme.typography.titleMedium)
            }
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (headers != null) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            VideoView(ctx).also { vv ->
                                val uri = attachment.localContentUri?.let(Uri::parse) ?: Uri.parse(AttachmentUrls.content(attachment.id.orEmpty()))
                                vv.setVideoURI(uri, headers)
                                vv.setOnPreparedListener { prepared = true }
                                vv.setOnCompletionListener { playing = false }
                                view = vv
                            }
                        },
                        onRelease = { it.stopPlayback() },
                    )
                }
                if (isAudio) Text("🎧", style = MaterialTheme.typography.displayLarge)
            }
            Text(
                formatMs(position) + if (attachment.durationMs != null) " / " + formatMs(attachment.durationMs) else "",
                color = Color.White, modifier = Modifier.fillMaxWidth().padding(8.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                IconButton(enabled = prepared, onClick = { local(playing, (position - 10_000).coerceAtLeast(0)) }) { Icon(Icons.Filled.Replay10, null, tint = Color.White) }
                IconButton(enabled = prepared, onClick = { local(!playing, position) }) {
                    Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(enabled = prepared, onClick = { local(playing, position + 10_000) }) { Icon(Icons.Filled.Forward10, null, tint = Color.White) }
            }
        }
    }
}

private fun formatMs(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}
