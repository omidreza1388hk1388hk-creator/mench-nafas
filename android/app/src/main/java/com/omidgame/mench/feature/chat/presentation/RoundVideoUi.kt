package com.omidgame.mench.feature.chat.presentation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.omidgame.mench.R
import com.omidgame.mench.core.network.AttachmentUrls
import com.omidgame.mench.feature.chat.domain.MessageAttachment
import kotlinx.coroutines.delay
import java.io.File

/** Gives the video player the headers (bearer token) a streamed attachment needs; provided by ChatScreen. */
val LocalMediaAuth = staticCompositionLocalOf<suspend () -> Map<String, String>> { { emptyMap() } }

private fun hasPermissions(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

/**
 * Full-screen round-video recorder: a circular live preview, tap the big button to start, tap again
 * to stop (auto-stops at [RoundVideo.MAX_DURATION_MS]); a ring fills as time passes. After a take of
 * at least one second, [onRecorded] receives the finished file; the caller sends it.
 */
@Composable
fun RoundVideoRecorderDialog(onRecorded: (File) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var permitted by remember { mutableStateOf(hasPermissions(context)) }
    var denied by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        permitted = grants.values.all { it }
        denied = !permitted
    }
    LaunchedEffect(Unit) {
        if (!permitted) permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.92f))) {
            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopStart).padding(16.dp)) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_cancel), tint = Color.White)
            }
            if (permitted) {
                RecorderBody(onRecorded = onRecorded, onDismiss = onDismiss)
            } else {
                Text(
                    text = stringResource(if (denied) R.string.round_video_permission_denied else R.string.round_video_permission_needed),
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                )
            }
        }
    }
}

@Composable
private fun BoxScope.RecorderBody(onRecorded: (File) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE } // TextureView: can be clipped round
    }
    var front by remember { mutableStateOf(true) }
    var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var recording by remember { mutableStateOf<Recording?>(null) }
    var elapsedMs by remember { mutableLongStateOf(0L) }
    var cameraError by remember { mutableStateOf(false) }
    var discard by remember { mutableStateOf(false) }
    val isRecording = recording != null
    val outputFile = remember { File(context.cacheDir, "round_${System.currentTimeMillis()}.mp4") }

    // (Re)bind the camera whenever the lens changes.
    DisposableEffect(front, lifecycleOwner) {
        val future = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        future.addListener({
            try {
                val cameraProvider = future.get()
                provider = cameraProvider
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                val recorder = Recorder.Builder()
                    .setQualitySelector(QualitySelector.from(Quality.SD, FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)))
                    .build()
                val capture = VideoCapture.withOutput(recorder)
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    capture,
                )
                videoCapture = capture
                cameraError = false
            } catch (e: Exception) {
                cameraError = true
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            // Leaving the screen mid-take (close button, back, rotation of the lens) throws the take away.
            discard = true
            recording?.stop()
            provider?.unbindAll()
            videoCapture = null
        }
    }

    // Timer + auto stop.
    LaunchedEffect(isRecording) {
        if (isRecording) {
            val start = System.currentTimeMillis()
            while (true) {
                elapsedMs = System.currentTimeMillis() - start
                if (elapsedMs >= RoundVideo.MAX_DURATION_MS) {
                    recording?.stop()
                    break
                }
                delay(100)
            }
        } else {
            elapsedMs = 0L
        }
    }

    fun startRecording() {
        val capture = videoCapture ?: return
        val options = FileOutputOptions.Builder(outputFile).build()
        discard = false
        recording = capture.output
            .prepareRecording(context, options)
            .withAudioEnabled()
            .start(ContextCompat.getMainExecutor(context)) { event ->
                if (event is VideoRecordEvent.Finalize) {
                    val durationMs = event.recordingStats.recordedDurationNanos / 1_000_000L
                    recording = null
                    when {
                        discard -> outputFile.delete()
                        event.hasError() && durationMs <= 0L -> {
                            outputFile.delete()
                            cameraError = true
                        }
                        !RoundVideo.isLongEnough(durationMs) -> outputFile.delete() // accidental tap
                        else -> onRecorded(outputFile)
                    }
                }
            }
    }

    Column(
        modifier = Modifier.align(Alignment.Center),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = Modifier.size(300.dp), contentAlignment = Alignment.Center) {
            AndroidView(
                factory = { previewView },
                modifier = Modifier.size(280.dp).clip(CircleShape),
            )
            val ringColor = MaterialTheme.colorScheme.primary
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = 6.dp.toPx()
                val inset = stroke / 2f + 2.dp.toPx()
                drawCircle(color = Color.White.copy(alpha = 0.25f), radius = size.minDimension / 2f - inset, style = Stroke(stroke))
                if (isRecording) {
                    drawArc(
                        color = ringColor,
                        startAngle = -90f,
                        sweepAngle = 360f * RoundVideo.progress(elapsedMs),
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = Size(size.width - inset * 2, size.height - inset * 2),
                        style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                }
            }
        }
        Text(
            text = if (cameraError) stringResource(R.string.round_video_camera_error)
            else if (isRecording) RoundVideo.formatTime(elapsedMs) else stringResource(R.string.round_video_hint),
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp, start = 24.dp, end = 24.dp),
        )
        Box(
            modifier = Modifier
                .padding(top = 24.dp)
                .size(76.dp)
                .clip(CircleShape)
                .background(if (isRecording) Color.White else MaterialTheme.colorScheme.error)
                .clickable(enabled = videoCapture != null) {
                    if (isRecording) recording?.stop() else startRecording()
                },
            contentAlignment = Alignment.Center,
        ) {
            if (isRecording) Icon(Icons.Filled.Stop, contentDescription = stringResource(R.string.chat_stop_recording), tint = Color.Black)
        }
    }
    if (!isRecording) {
        IconButton(
            onClick = { front = !front },
            modifier = Modifier.align(Alignment.BottomEnd).padding(32.dp),
        ) {
            Icon(Icons.Filled.Cameraswitch, contentDescription = stringResource(R.string.round_video_flip), tint = Color.White)
        }
    }
}

/**
 * A round video in the chat: circular thumbnail with a play button; tap plays it in place (with
 * sound) inside the circle with a progress ring, tap again to stop. The player streams with the
 * bearer token, or plays the sender's own local copy.
 */
@Composable
fun RoundVideoBubble(attachment: MessageAttachment, modifier: Modifier = Modifier) {
    val circle = 200.dp
    val authHeaders = LocalMediaAuth.current
    var playing by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var headers by remember { mutableStateOf<Map<String, String>?>(null) }
    val remoteUrl = attachment.id?.let { AttachmentUrls.content(it) }
    val local = attachment.localContentUri
    val canPlay = local != null || remoteUrl != null
    val thumbnail = attachment.localThumbnailUri ?: attachment.id?.let { AttachmentUrls.thumbnail(it) }
    val (coverW, coverH) = RoundVideo.coverSize(circle.value, attachment.widthPx, attachment.heightPx)

    LaunchedEffect(playing) {
        if (playing && local == null && headers == null) headers = authHeaders()
    }

    Box(
        modifier = modifier
            .size(circle)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(enabled = canPlay, onClickLabel = stringResource(R.string.chat_play_video)) {
                playing = !playing
                progress = 0f
            },
        contentAlignment = Alignment.Center,
    ) {
        if (!playing) {
            AsyncImage(
                model = thumbnail,
                contentDescription = stringResource(R.string.round_video_label),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier.size(52.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
            }
            attachment.durationMs?.let {
                Text(
                    text = RoundVideo.formatTime(it),
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp)
                        .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        } else {
            val ready = local != null || headers != null
            if (ready) {
                var videoView by remember { mutableStateOf<VideoView?>(null) }
                AndroidView(
                    modifier = Modifier.requiredSize(coverW.dp, coverH.dp),
                    factory = { ctx ->
                        VideoView(ctx).apply {
                            if (local != null) setVideoPath(local)
                            else setVideoURI(Uri.parse(remoteUrl), headers)
                            setOnPreparedListener { it.start() }
                            setOnCompletionListener { playing = false }
                            setOnErrorListener { _, _, _ ->
                                playing = false
                                true
                            }
                            videoView = this
                        }
                    },
                    onRelease = { it.stopPlayback() },
                )
                LaunchedEffect(videoView) {
                    while (true) {
                        val view = videoView
                        val duration = view?.duration ?: 0
                        progress = if (duration > 0) ((view?.currentPosition ?: 0).toFloat() / duration).coerceIn(0f, 1f) else 0f
                        delay(80)
                    }
                }
            }
        }
        val ringColor = Color.White
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (playing) {
                val stroke = 4.dp.toPx()
                val inset = stroke / 2f
                drawArc(
                    color = ringColor,
                    startAngle = -90f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - inset * 2, size.height - inset * 2),
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
        }
    }
}
