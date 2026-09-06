package com.omidgame.mench.feature.calls.presentation

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

    if (session.callType == CallType.VIDEO) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            InCallVideoRenderers(viewModel)
            InCallControls(state, viewModel, modifier = Modifier.align(Alignment.BottomCenter))
            CallHeader(session.otherDisplayName, session.otherPhoneE164, state.durationSeconds, modifier = Modifier.align(Alignment.TopStart))
        }
    } else {
        CallScaffold {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CallerAvatar(session.otherDisplayName, session.otherPhoneE164)
                Spacer(Modifier.height(16.dp))
                Text(text = formatDuration(state.durationSeconds), style = MaterialTheme.typography.bodyLarge, color = Color.White)
            }
            InCallControls(state, viewModel, modifier = Modifier.fillMaxWidth().padding(bottom = 48.dp))
        }
    }
}

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
                setMirror(true)
            }.also { localRenderer = it }
        },
    )
}

@Composable
private fun InCallControls(state: CallUiState.Active, viewModel: CallViewModel, modifier: Modifier = Modifier) {
    Row(modifier = modifier.padding(horizontal = 32.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
        SmallToggleButton(
            icon = if (state.isMuted) Icons.Default.MicOff else Icons.Default.Mic,
            active = state.isMuted,
            onClick = viewModel::toggleMute,
        )
        if (state.session.callType == CallType.VIDEO) {
            SmallToggleButton(
                icon = if (state.isLocalVideoEnabled) Icons.Default.Videocam else Icons.Default.VideocamOff,
                active = !state.isLocalVideoEnabled,
                onClick = viewModel::toggleLocalVideo,
            )
            SmallToggleButton(icon = Icons.Default.Cameraswitch, active = false, onClick = viewModel::switchCamera)
        } else {
            SmallToggleButton(icon = Icons.Default.VolumeUp, active = state.isSpeakerOn, onClick = viewModel::toggleSpeaker)
        }
        CallActionButton(icon = Icons.Default.CallEnd, background = MaterialTheme.colorScheme.error, onClick = viewModel::hangUp, size = 56.dp)
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
private fun CallScaffold(content: @Composable Column.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121214)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Spacer(Modifier.height(64.dp))
        content()
    }
}

@Composable
private fun CallerAvatar(displayName: String?, phoneE164: String) {
    Surface(shape = CircleShape, color = Color(0xFF3A3A3E), modifier = Modifier.size(120.dp)) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(
                text = (displayName?.firstOrNull() ?: phoneE164.lastOrNull() ?: '?').toString().uppercase(),
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White,
            )
        }
    }
    Spacer(Modifier.height(12.dp))
    Text(text = displayName ?: phoneE164, style = MaterialTheme.typography.titleLarge, color = Color.White)
}

@Composable
private fun CallActionButton(icon: androidx.compose.ui.graphics.vector.ImageVector, background: Color, onClick: () -> Unit, size: androidx.compose.ui.unit.Dp = 64.dp) {
    Surface(shape = CircleShape, color = background, modifier = Modifier.size(size)) {
        IconButton(onClick = onClick) {
            Icon(icon, contentDescription = null, tint = Color.White)
        }
    }
}

@Composable
private fun SmallToggleButton(icon: androidx.compose.ui.graphics.vector.ImageVector, active: Boolean, onClick: () -> Unit) {
    Surface(
        shape = CircleShape,
        color = if (active) Color.White else Color.White.copy(alpha = 0.2f),
        modifier = Modifier.size(52.dp),
    ) {
        IconButton(onClick = onClick) {
            Icon(icon, contentDescription = null, tint = if (active) Color.Black else Color.White)
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
