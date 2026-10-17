package com.omidgame.mench.feature.chat.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.omidgame.mench.R
import com.omidgame.mench.core.media.PlaybackProgress
import com.omidgame.mench.core.media.Waveform
import com.omidgame.mench.core.network.AttachmentUrls
import com.omidgame.mench.feature.chat.domain.MessageAttachment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Phase 2A — everything a voice bubble needs besides the (unchanged) play/pause callback:
 * the live playhead, the playback speed and the seek / speed actions. Provided once by
 * ChatScreen through [LocalVoiceUi] so the long MessageBubble parameter list does not grow.
 */
@Stable
class VoiceUi(
    val progress: StateFlow<PlaybackProgress>,
    val speed: StateFlow<Float>,
    val onSeek: (sourceId: String, localPath: String?, remoteUrl: String?, fraction: Float) -> Unit,
    val onCycleSpeed: () -> Unit,
)

val LocalVoiceUi = staticCompositionLocalOf<VoiceUi?> { null }

private val IdleProgress = PlaybackProgress()
private val NoProgressFlow: StateFlow<PlaybackProgress> = MutableStateFlow(IdleProgress)
private val NoSpeedFlow: StateFlow<Float> = MutableStateFlow(1f)

/** Play/pause + true waveform (scrubbable) + elapsed/total time + 1x/1.5x/2x chip while playing. */
@Composable
fun VoiceBubbleContent(
    clientMsgId: String,
    attachment: MessageAttachment,
    isPlayingThis: Boolean,
    onTogglePlayback: (sourceId: String, localPath: String?, remoteUrl: String?) -> Unit,
) {
    val voiceUi = LocalVoiceUi.current
    val remoteUrl = attachment.id?.let { AttachmentUrls.content(it) }
    val canPlay = attachment.localContentUri != null || remoteUrl != null
    val bars = remember(clientMsgId, attachment.waveform) {
        attachment.waveform ?: Waveform.fallback(clientMsgId)
    }

    // Only the bubble that is playing follows the 25 Hz playhead; every other bubble sees a constant.
    val progress by remember(voiceUi, clientMsgId) {
        (voiceUi?.progress ?: NoProgressFlow)
            .map { if (it.sourceId == clientMsgId) it else IdleProgress }
            .distinctUntilChanged()
    }.collectAsState(initial = IdleProgress)
    val speed by (voiceUi?.speed ?: NoSpeedFlow).collectAsState()

    val fraction = if (isPlayingThis) progress.fraction else 0f
    val shownMs = if (isPlayingThis && progress.durationMs > 0) {
        progress.positionMs.toLong()
    } else {
        attachment.durationMs ?: 0L
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = { onTogglePlayback(clientMsgId, attachment.localContentUri, remoteUrl) },
            enabled = canPlay,
        ) {
            Icon(
                imageVector = if (isPlayingThis) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = stringResource(
                    if (isPlayingThis) R.string.chat_pause_voice else R.string.chat_play_voice,
                ),
            )
        }
        Column {
            WaveformSeekBar(
                bars = bars,
                fraction = fraction,
                enabled = canPlay,
                onScrub = { f ->
                    if (voiceUi != null) {
                        voiceUi.onSeek(clientMsgId, attachment.localContentUri, remoteUrl, f)
                    } else if (!isPlayingThis) {
                        onTogglePlayback(clientMsgId, attachment.localContentUri, remoteUrl)
                    }
                },
                modifier = Modifier.width(150.dp).height(30.dp),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = formatVoiceTime(shownMs),
                    style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                    color = LocalContentColor.current.copy(alpha = 0.85f),
                )
                if (isPlayingThis && voiceUi != null) {
                    SpeedChip(speed = speed, onClick = voiceUi.onCycleSpeed)
                }
            }
        }
    }
}

@Composable
private fun SpeedChip(speed: Float, onClick: () -> Unit) {
    val label = when {
        speed >= 1.9f -> "2\u00D7"
        speed >= 1.4f -> "1.5\u00D7"
        else -> "1\u00D7"
    }
    val description = stringResource(R.string.voice_speed_description, label)
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(LocalContentColor.current.copy(alpha = 0.18f))
            .clickable(onClickLabel = description, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

/**
 * Bars (0..255 each) with the played part in full colour. Tap to jump, drag to scrub (the
 * seek is committed on release). Always drawn left-to-right, also in RTL, so time reads the
 * same way as the play head.
 */
@Composable
fun WaveformSeekBar(
    bars: List<Int>,
    fraction: Float,
    enabled: Boolean,
    onScrub: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val played = LocalContentColor.current
    val idle = played.copy(alpha = 0.38f)
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val shown = dragFraction ?: fraction
    val currentOnScrub by rememberUpdatedState(onScrub)

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Canvas(
            modifier = modifier
                .semantics { progressBarRangeInfo = ProgressBarRangeInfo(shown.coerceIn(0f, 1f), 0f..1f) }
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectTapGestures(onTap = { offset ->
                        currentOnScrub((offset.x / size.width).coerceIn(0f, 1f))
                    })
                }
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragStart = { offset -> dragFraction = (offset.x / size.width).coerceIn(0f, 1f) },
                        onDragEnd = {
                            dragFraction?.let { currentOnScrub(it) }
                            dragFraction = null
                        },
                        onDragCancel = { dragFraction = null },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                        },
                    )
                },
        ) {
            val count = bars.size
            if (count == 0) return@Canvas
            val step = size.width / count
            val stroke = step * 0.56f
            val minHeight = 3.dp.toPx()
            bars.forEachIndexed { i, value ->
                val h = (size.height * (value / 255f)).coerceIn(minHeight, size.height)
                val x = step * i + step / 2f
                val barPosition = (i + 0.5f) / count
                drawLine(
                    color = if (barPosition <= shown) played else idle,
                    start = Offset(x, (size.height - h) / 2f),
                    end = Offset(x, (size.height + h) / 2f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            }
            if (shown > 0f && shown < 1f) {
                drawCircle(color = played, radius = 4.dp.toPx(), center = Offset(shown * size.width, size.height / 2f))
            }
        }
    }
}

/** m:ss — same format the file already used for durations. */
internal fun formatVoiceTime(durationMs: Long): String {
    val totalSeconds = (durationMs.coerceAtLeast(0L)) / 1000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
