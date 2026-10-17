package com.omidgame.mench.feature.calls.presentation

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.omidgame.mench.R
import com.omidgame.mench.feature.calls.domain.CallStatus
import com.omidgame.mench.feature.calls.domain.CallType
import com.omidgame.mench.feature.calls.domain.CallUiState
import androidx.core.content.ContextCompat
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer

/**
 * Rendered once, at the top of MenchNavGraph, on top of whatever screen
 * is currently showing — a call can start or arrive regardless of where
 * the user is in the app, and always takes over the full screen the same
 * way a phone's native dialer does.
 */
@Composable
fun CallOverlay(state: CallUiState, viewModel: CallViewModel) {
    when (state) {
        is CallUiState.Idle -> Unit
        is CallUiState.Incoming -> IncomingCallScreen(state, onAccept = viewModel::accept, onDecline = viewModel::decline)
        is CallUiState.Outgoing -> DialingScreen(
            displayName = state.session.otherDisplayName,
            phoneE164 = state.session.otherPhoneE164,
            statusText = stringResource(R.string.call_outgoing_voice).takeIf { state.session.callType == CallType.VOICE }
                ?: stringResource(R.string.call_outgoing_video),
            onHangUp = viewModel::hangUp,
        )
        is CallUiState.Connecting -> DialingScreen(
            displayName = state.session.otherDisplayName,
            phoneE164 = state.session.otherPhoneE164,
            statusText = stringResource(R.string.call_connecting),
            onHangUp = viewModel::hangUp,
        )
        is CallUiState.Active -> InCallScreen(state, viewModel)
        is CallUiState.Ended -> EndedScreen(state)
    }
}

@Composable
private fun IncomingCallScreen(state: CallUiState.Incoming, onAccept: () -> Unit, onDecline: () -> Unit) {
    val context = LocalContext.current
    var pendingAccept by remember { mutableStateOf(false) }

    val permissionsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        if (results.values.all { it }) onAccept()
    }

    fun requestAndAccept() {
        val needed = requiredPermissions(state.callType).filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isEmpty()) onAccept() else permissionsLauncher.launch(needed.toTypedArray())
    }

    CallScaffold {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CallerAvatar(state.callerDisplayName, state.callerPhoneE164)
            Spacer(Modifier.height(16.dp))
            Text(
                text = if (state.callType == CallType.VIDEO) stringResource(R.string.call_incoming_video) else stringResource(R.string.call_incoming_voice),
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 48.dp, start = 32.dp, end = 32.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            CallActionButton(icon = Icons.Default.CallEnd, background = MaterialTheme.colorScheme.error, onClick = onDecline)
            CallActionButton(icon = Icons.Default.Call, background = Color(0xFF34C759), onClick = ::requestAndAccept)
        }
    }
}

@Composable
private fun DialingScreen(
    displayName: String?,
    phoneE164: String,
    statusText: String,
    onHangUp: () -> Unit,
) {
    CallScaffold {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CallerAvatar(displayName, phoneE164)
            Spacer(Modifier.height(16.dp))
            Text(text = statusText, style = MaterialTheme.typography.bodyLarge, color = Color.White)
        }
        Box(modifier = Modifier.fillMaxWidth().padding(bottom = 48.dp), contentAlignment = Alignment.Center) {
            CallActionButton(icon = Icons.Default.CallEnd, background = MaterialTheme.colorScheme.error, onClick = onHangUp)
        }
    }
}

@Composable
private fun InCallScreen(state: CallUiState.Active, viewModel: CallViewModel) {
    val session = state.session
    val inPip by CallPip.flow.collectAsState()
    var showRoutes by remember { mutableStateOf(false) }
    var showChat by remember { mutableStateOf(false) }
    var showReactions by remember { mutableStateOf(false) }
    val chat by viewModel.chat.collectAsState()
    val unread by viewModel.unreadChat.collectAsState()
    LaunchedEffect(showChat) { viewModel.setChatPanelOpen(showChat) }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { viewModel.setChatPanelOpen(false) } }

    val extras = InCallExtras(
        onRouteClick = { showRoutes = true },
        onChatClick = { showChat = !showChat; showReactions = false },
        onReactClick = { showReactions = !showReactions; showChat = false },
        unreadChat = unread,
    )

    Box(modifier = Modifier.fillMaxSize()) {
        if (session.callType == CallType.VIDEO) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                InCallVideoRenderers(viewModel)
                if (!inPip) {
                    InCallControls(state, viewModel, extras, modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
                    CallHeader(session.otherDisplayName, session.otherPhoneE164, state.durationSeconds, modifier = Modifier.align(Alignment.TopStart).statusBarsPadding())
                }
            }
        } else {
            CallScaffold {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CallerAvatar(session.otherDisplayName, session.otherPhoneE164)
                    Spacer(Modifier.height(16.dp))
                    Text(text = formatDuration(state.durationSeconds), style = MaterialTheme.typography.bodyLarge, color = Color.White)
                }
                if (!inPip) {
                    InCallControls(state, viewModel, extras, modifier = Modifier.fillMaxWidth().padding(bottom = 48.dp))
                }
            }
        }

        if (!inPip) {
            QualityBadge(
                tier = state.quality,
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = if (session.callType == CallType.VIDEO) 176.dp else 20.dp, end = 24.dp),
            )
            if (state.reconnecting) {
                ReconnectingBanner(modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 12.dp))
            }
            FloatingReactions(viewModel.reactions)
            if (showReactions) {
                ReactionBar(
                    onPick = {
                        viewModel.sendReaction(it)
                        showReactions = false
                    },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 190.dp),
                )
            }
            if (showChat) {
                CallChatPanel(chat, onSend = viewModel::sendChat, modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 150.dp))
            }
        }
    }

    if (showRoutes) {
        AudioRouteSheet(
            available = state.availableRoutes,
            selected = state.route,
            onSelect = viewModel::selectAudioRoute,
            onDismiss = { showRoutes = false },
        )
    }
}

/** Callbacks + state for the secondary in-call buttons (output, chat, reactions). */
private class InCallExtras(
    val onRouteClick: () -> Unit,
    val onChatClick: () -> Unit,
    val onReactClick: () -> Unit,
    val unreadChat: Int,
)

/**
 * Two independent SurfaceViewRenderers — full-screen remote, small
 * picture-in-picture local — each created by its own AndroidView factory
 * (which Compose guarantees runs exactly once per renderer). Both refs
 * are only handed to CallSessionManager once *both* exist, via the
 * LaunchedEffect below, since WebRtcClient.start() needs them together to
 * init() them against the same EGL context.
 */
@Composable
private fun BoxScope.InCallVideoRenderers(viewModel: CallViewModel) {
    var localRenderer by remember { mutableStateOf<SurfaceViewRenderer?>(null) }
    var remoteRenderer by remember { mutableStateOf<SurfaceViewRenderer?>(null) }

    LaunchedEffect(localRenderer, remoteRenderer) {
        val local = localRenderer
        val remote = remoteRenderer
        if (local != null && remote != null) viewModel.attachRenderers(local, remote)
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            SurfaceViewRenderer(ctx).apply {
                setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                setMirror(false)
            }.also { remoteRenderer = it }
        },
    )
    AndroidView(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(16.dp)
            .size(width = 110.dp, height = 150.dp),
        factory = { ctx ->
            SurfaceViewRenderer(ctx).apply {
                setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                setMirror(com.omidgame.mench.core.webrtc.CallSettingsStore.current.mirrorFrontCamera)
            }.also { localRenderer = it }
        },
    )
}

@Composable
private fun InCallControls(state: CallUiState.Active, viewModel: CallViewModel, extras: InCallExtras, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            SmallToggleButton(icon = routeIcon(state.route), active = false, onClick = extras.onRouteClick, description = stringResource(R.string.call_route_title))
            Box {
                SmallToggleButton(icon = Icons.Default.Chat, active = false, onClick = extras.onChatClick, description = stringResource(R.string.call_chat))
                if (extras.unreadChat > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(14.dp)
                            .background(MaterialTheme.colorScheme.error, CircleShape),
                    )
                }
            }
            SmallToggleButton(icon = Icons.Default.EmojiEmotions, active = false, onClick = extras.onReactClick, description = stringResource(R.string.call_react))
        }
        Row(modifier = Modifier.padding(horizontal = 32.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            SmallToggleButton(
                icon = if (state.isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                active = state.isMuted,
                onClick = viewModel::toggleMute,
                description = stringResource(if (state.isMuted) R.string.call_unmute else R.string.call_mute),
            )
            if (state.session.callType == CallType.VIDEO) {
                SmallToggleButton(
                    icon = if (state.isLocalVideoEnabled) Icons.Default.Videocam else Icons.Default.VideocamOff,
                    active = !state.isLocalVideoEnabled,
                    onClick = viewModel::toggleLocalVideo,
                    description = stringResource(R.string.call_toggle_camera),
                )
                SmallToggleButton(icon = Icons.Default.Cameraswitch, active = false, onClick = viewModel::switchCamera, description = stringResource(R.string.round_video_flip))
            } else {
                SmallToggleButton(icon = Icons.Default.VolumeUp, active = state.isSpeakerOn, onClick = viewModel::toggleSpeaker, description = stringResource(R.string.call_route_speaker))
            }
            CallActionButton(icon = Icons.Default.CallEnd, background = MaterialTheme.colorScheme.error, onClick = viewModel::hangUp, size = 64.dp)
        }
    }
}

@Composable
private fun CallHeader(displayName: String?, phoneE164: String, durationSeconds: Int, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(16.dp)) {
        Text(text = displayName ?: phoneE164, color = Color.White, style = MaterialTheme.typography.titleMedium)
        Text(text = formatDuration(durationSeconds), color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun EndedScreen(state: CallUiState.Ended) {
    val statusText = when (state.status) {
        CallStatus.DECLINED -> stringResource(R.string.call_declined_status)
        CallStatus.MISSED -> stringResource(R.string.call_missed_status)
        else -> stringResource(R.string.call_ended_status)
    }
    CallScaffold {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CallerAvatar(state.otherDisplayName, state.otherPhoneE164)
            Spacer(Modifier.height(16.dp))
            Text(text = statusText, style = MaterialTheme.typography.bodyLarge, color = Color.White)
        }
    }
}

@Composable
private fun CallScaffold(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF3F0D2B), Color(0xFF1B0A24), Color(0xFF0B0B14))))
            .statusBarsPadding()
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Spacer(Modifier.height(56.dp))
        content()
    }
}

/** Large avatar with two expanding halo rings and a soft glow, then the caller's name. */
@Composable
private fun CallerAvatar(displayName: String?, phoneE164: String) {
    val infinite = rememberInfiniteTransition(label = "callHalo")
    val ring by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200), RepeatMode.Restart),
        label = "ring",
    )
    Box(modifier = Modifier.size(240.dp), contentAlignment = Alignment.Center) {
        listOf(0f, 0.5f).forEach { offset ->
            val p = (ring + offset) % 1f
            Box(
                modifier = Modifier
                    .size(130.dp)
                    .graphicsLayer {
                        val sc = 1f + p * 0.85f
                        scaleX = sc; scaleY = sc
                        alpha = (1f - p) * 0.35f
                    }
                    .background(Brush.radialGradient(listOf(Color(0xFFFF6FA3), Color.Transparent)), CircleShape),
            )
        }
        Box(
            modifier = Modifier
                .size(128.dp)
                .background(Brush.linearGradient(listOf(Color(0xFFE23676), Color(0xFF7A1A4D))), CircleShape)
                .border(3.dp, Color.White.copy(alpha = 0.35f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = (displayName?.firstOrNull() ?: phoneE164.lastOrNull() ?: '?').toString().uppercase(),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }
    }
    Text(text = displayName ?: phoneE164, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.White)
}

@Composable
private fun CallActionButton(icon: androidx.compose.ui.graphics.vector.ImageVector, background: Color, onClick: () -> Unit, size: androidx.compose.ui.unit.Dp = 72.dp) {
    Surface(shape = CircleShape, color = background, shadowElevation = 10.dp, modifier = Modifier.size(size)) {
        IconButton(onClick = onClick) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(size / 2.4f))
        }
    }
}

@Composable
private fun SmallToggleButton(icon: androidx.compose.ui.graphics.vector.ImageVector, active: Boolean, onClick: () -> Unit, description: String? = null) {
    Surface(
        shape = CircleShape,
        color = if (active) Color.White else Color.White.copy(alpha = 0.16f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.28f)),
        modifier = Modifier.size(60.dp),
    ) {
        IconButton(onClick = onClick) {
            Icon(icon, contentDescription = description, tint = if (active) Color(0xFF3F0D2B) else Color.White)
        }
    }
}

private fun requiredPermissions(callType: CallType): List<String> =
    if (callType == CallType.VIDEO) listOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
    else listOf(Manifest.permission.RECORD_AUDIO)

private fun formatDuration(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
