package com.omidgame.mench.core.webrtc

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.PeerConnectionFactory
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PeerConnectionFactory.initialize()/.builder() is expensive and must
 * happen exactly once per process — this holder owns that lifetime.
 * EglBase is shared the same way: local and remote SurfaceViewRenderers
 * (see the calls presentation layer) and the camera's hardware video
 * encoder all need to share one EGL context, or video frames simply don't
 * render.
 */
@Singleton
class WebRtcEglContextHolder @Inject constructor(
    @ApplicationContext context: Context,
) {
    val eglBase: EglBase = EglBase.create()

    val peerConnectionFactory: PeerConnectionFactory by lazy {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context)
                .setEnableInternalTracer(false)
                .createInitializationOptions(),
        )

        val encoderFactory = DefaultVideoEncoderFactory(eglBase.eglBaseContext, /* enableIntelVp8Encoder= */ true, /* enableH264HighProfile= */ true)
        val decoderFactory = DefaultVideoDecoderFactory(eglBase.eglBaseContext)

        PeerConnectionFactory.builder()
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .createPeerConnectionFactory()
    }
}
