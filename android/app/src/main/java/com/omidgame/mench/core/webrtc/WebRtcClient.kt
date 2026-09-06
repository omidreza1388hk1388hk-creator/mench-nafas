package com.omidgame.mench.core.webrtc

import android.content.Context
import android.media.AudioManager
import com.omidgame.mench.feature.calls.domain.CallType
import com.omidgame.mench.feature.calls.domain.IceServerConfig
import com.omidgame.mench.feature.calls.domain.LocalIceCandidate
import kotlinx.coroutines.suspendCancellableCoroutine
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnection.IceConnectionState
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoCapturer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

interface WebRtcClientListener {
    fun onLocalIceCandidate(candidate: LocalIceCandidate)
    fun onConnectionStateChanged(state: IceConnectionState)
    /** Fired once, when the remote party's video track first arrives — never for voice calls. */
    fun onRemoteVideoTrackAvailable(track: VideoTrack)
}

/**
 * One instance per call, created fresh by WebRtcClientFactory and torn
 * down with dispose() when the call ends — a PeerConnection can't be
 * reused across calls, and re-creating one is cheap next to the
 * correctness risk of carrying stale ICE/SDP state into a new call.
 *
 * Every method that used to be callback-based in the upstream WebRTC API
 * (createOffer, createAnswer, setLocalDescription, setRemoteDescription)
 * is wrapped here as `suspend fun` via suspendCancellableCoroutine, so
 * CallSessionManager can just `await` them in a straight-line coroutine
 * instead of nesting SdpObserver callbacks.
 */
class WebRtcClient(
    private val context: Context,
    private val peerConnectionFactory: PeerConnectionFactory,
    private val eglContext: org.webrtc.EglBase.Context,
    private val iceServers: List<IceServerConfig>,
    private val callType: CallType,
    private val listener: WebRtcClientListener,
) {
    private var peerConnection: PeerConnection? = null
    private var videoCapturer: VideoCapturer? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var localAudioTrack: AudioTrack? = null
    private var localVideoTrack: VideoTrack? = null
    private var localVideoSource: VideoSource? = null
    private var localAudioSource: AudioSource? = null
    private var isFrontCamera = true

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    fun start(localRenderer: SurfaceViewRenderer?, remoteRenderer: SurfaceViewRenderer?) {
        val rtcIceServers = iceServers.map { server ->
            val builder = PeerConnection.IceServer.builder(server.urls)
            if (server.username != null) builder.setUsername(server.username)
            if (server.credential != null) builder.setPassword(server.credential)
            builder.createIceServer()
        }

        val rtcConfig = PeerConnection.RTCConfiguration(rtcIceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }

        peerConnection = peerConnectionFactory.createPeerConnection(rtcConfig, peerConnectionObserver)
        remoteRenderer?.init(eglContext, null)
        localRenderer?.init(eglContext, null)

        addLocalTracks(localRenderer)
    }

    private fun addLocalTracks(localRenderer: SurfaceViewRenderer?) {
        val pc = peerConnection ?: return

        val audioConstraints = MediaConstraints()
        localAudioSource = peerConnectionFactory.createAudioSource(audioConstraints)
        localAudioTrack = peerConnectionFactory.createAudioTrack("audio0", localAudioSource)
        pc.addTrack(localAudioTrack)

        if (callType == CallType.VIDEO) {
            val capturer = createCameraCapturer() ?: return
            videoCapturer = capturer
            surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", eglContext)
            localVideoSource = peerConnectionFactory.createVideoSource(capturer.isScreencast)
            capturer.initialize(surfaceTextureHelper, context, localVideoSource!!.capturerObserver)
            capturer.startCapture(1280, 720, 30)

            localVideoTrack = peerConnectionFactory.createVideoTrack("video0", localVideoSource)
            localRenderer?.let { localVideoTrack?.addSink(it) }
            pc.addTrack(localVideoTrack)
        }
    }

    private fun createCameraCapturer(): VideoCapturer? {
        val enumerator = Camera2Enumerator(context)
        val deviceNames = enumerator.deviceNames

        // Prefer front camera for the initial preview — matches every
        // other video-calling app's default and is what
        // spec section 28's "camera switching" then toggles away from.
        deviceNames.firstOrNull { enumerator.isFrontFacing(it) }?.let { name ->
            enumerator.createCapturer(name, null)?.let { return it }
        }
        deviceNames.firstOrNull { enumerator.isBackFacing(it) }?.let { name ->
            enumerator.createCapturer(name, null)?.let { return it }
        }
        return null
    }

    suspend fun createOffer(): String = suspendCancellableCoroutine { cont ->
        val pc = peerConnection ?: return@suspendCancellableCoroutine cont.resumeWithException(
            IllegalStateException("start() must be called before createOffer()"),
        )
        val constraints = MediaConstraints()
        pc.createOffer(object : SdpObserverAdapter() {
            override fun onCreateSuccess(desc: SessionDescription) {
                pc.setLocalDescription(SdpObserverAdapter(), desc)
                cont.resume(desc.description)
            }
            override fun onCreateFailure(error: String) {
                cont.resumeWithException(IllegalStateException("createOffer failed: $error"))
            }
        }, constraints)
    }

    suspend fun createAnswer(): String = suspendCancellableCoroutine { cont ->
        val pc = peerConnection ?: return@suspendCancellableCoroutine cont.resumeWithException(
            IllegalStateException("start() must be called before createAnswer()"),
        )
        val constraints = MediaConstraints()
        pc.createAnswer(object : SdpObserverAdapter() {
            override fun onCreateSuccess(desc: SessionDescription) {
                pc.setLocalDescription(SdpObserverAdapter(), desc)
                cont.resume(desc.description)
            }
            override fun onCreateFailure(error: String) {
                cont.resumeWithException(IllegalStateException("createAnswer failed: $error"))
            }
        }, constraints)
    }

    suspend fun setRemoteOffer(sdp: String) = setRemoteDescription(SessionDescription.Type.OFFER, sdp)
    suspend fun setRemoteAnswer(sdp: String) = setRemoteDescription(SessionDescription.Type.ANSWER, sdp)

    private suspend fun setRemoteDescription(type: SessionDescription.Type, sdp: String) =
        suspendCancellableCoroutine<Unit> { cont ->
            val pc = peerConnection ?: return@suspendCancellableCoroutine cont.resumeWithException(
                IllegalStateException("start() must be called before setRemoteDescription()"),
            )
            pc.setRemoteDescription(object : SdpObserverAdapter() {
                override fun onSetSuccess() = cont.resume(Unit)
                override fun onSetFailure(error: String) {
                    cont.resumeWithException(IllegalStateException("setRemoteDescription failed: $error"))
                }
            }, SessionDescription(type, sdp))
        }

    fun addRemoteIceCandidate(candidate: LocalIceCandidate) {
        peerConnection?.addIceCandidate(
            IceCandidate(candidate.sdpMid, candidate.sdpMLineIndex, candidate.candidate),
        )
    }

    fun setMuted(muted: Boolean) {
        localAudioTrack?.setEnabled(!muted)
    }

    /** true = route audio to the loudspeaker; false = earpiece (or a connected headset/Bluetooth device, which Android's audio routing keeps priority regardless of this flag). */
    fun setSpeakerphoneOn(on: Boolean) {
        audioManager.isSpeakerphoneOn = on
    }

    fun switchCamera() {
        val capturer = videoCapturer as? CameraVideoCapturer ?: return
        capturer.switchCamera(null)
        isFrontCamera = !isFrontCamera
    }

    fun setLocalVideoEnabled(enabled: Boolean) {
        localVideoTrack?.setEnabled(enabled)
    }

    fun dispose() {
        try {
            videoCapturer?.stopCapture()
        } catch (_: InterruptedException) {
            // stopCapture() is documented to potentially throw this; the
            // capturer is being torn down either way, so there's nothing
            // useful to do beyond not crashing the call-teardown path.
        }
        videoCapturer?.dispose()
        surfaceTextureHelper?.dispose()
        localVideoSource?.dispose()
        localAudioSource?.dispose()
        peerConnection?.close()
        peerConnection?.dispose()
    }

    private val peerConnectionObserver = object : PeerConnection.Observer {
        override fun onIceCandidate(candidate: IceCandidate) {
            listener.onLocalIceCandidate(
                LocalIceCandidate(candidate.sdpMid, candidate.sdpMLineIndex, candidate.sdp),
            )
        }

        override fun onIceConnectionChange(state: IceConnectionState) {
            listener.onConnectionStateChanged(state)
        }

        override fun onAddStream(stream: MediaStream) {
            stream.videoTracks.firstOrNull()?.let { listener.onRemoteVideoTrackAvailable(it) }
        }

        override fun onTrack(transceiver: org.webrtc.RtpTransceiver) {
            val track = transceiver.receiver.track()
            if (track is VideoTrack) {
                listener.onRemoteVideoTrackAvailable(track)
            }
        }

        override fun onSignalingChange(state: PeerConnection.SignalingState) {}
        override fun onIceConnectionReceivingChange(receiving: Boolean) {}
        override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {}
        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) {}
        override fun onRemoveStream(stream: MediaStream) {}
        override fun onDataChannel(channel: DataChannel) {}
        override fun onRenegotiationNeeded() {}
        override fun onAddTrack(receiver: RtpReceiver, streams: Array<out MediaStream>) {}
    }
}

/** Every SdpObserver callback defaults to a no-op override — most call sites above only care about one or two of the four. */
private open class SdpObserverAdapter : SdpObserver {
    override fun onCreateSuccess(desc: SessionDescription) {}
    override fun onSetSuccess() {}
    override fun onCreateFailure(error: String) {}
    override fun onSetFailure(error: String) {}
}
