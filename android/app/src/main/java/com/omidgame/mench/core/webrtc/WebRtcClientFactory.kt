package com.omidgame.mench.core.webrtc

import android.content.Context
import com.omidgame.mench.feature.calls.domain.CallType
import com.omidgame.mench.feature.calls.domain.IceServerConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebRtcClientFactory @Inject constructor(
    @ApplicationContext private val context: Context,
    private val eglContextHolder: WebRtcEglContextHolder,
) {
    fun create(
        callType: CallType,
        iceServers: List<IceServerConfig>,
        listener: WebRtcClientListener,
    ): WebRtcClient = WebRtcClient(
        context = context,
        peerConnectionFactory = eglContextHolder.peerConnectionFactory,
        eglContext = eglContextHolder.eglBase.eglBaseContext,
        iceServers = iceServers,
        callType = callType,
        listener = listener,
    )

    /** The shared EGL context every SurfaceViewRenderer must init() with — see the doc comment on WebRtcEglContextHolder for why this must be one shared instance. */
    val eglBaseContext get() = eglContextHolder.eglBase.eglBaseContext
}
