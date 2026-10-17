# Phase 2B (part 1) — Calls: routing, quality, reconnect, PiP, in-call chat/reactions, history (v18)

Builds on the existing 1:1 WebRTC engine (nothing was rewritten). **Group calls, screen sharing and background blur are NOT in this drop — see "Not built" below for the real reasons.**

## Done
- **Audio output sheet**: earpiece / speaker / Bluetooth / wired headset (Android 12+ `setCommunicationDevice`, older `speakerphone` + SCO). Headset plugged in → call moves to it; unplugged → sensible fallback. Voice default earpiece, video default speaker, a headset always wins. Bluetooth needs the `BLUETOOTH_CONNECT` permission (already declared); without it Bluetooth is simply not offered.
- **Proximity**: screen turns off at the ear (earpiece + voice only), on again when moved away.
- **Call quality indicator**: 3 bars (green/amber/red) from RTT, packet loss and jitter, sampled every 3 s and debounced (reacts fast to trouble, recovers cautiously).
- **Adaptive bitrate**: the video sender's bitrate ceiling follows the quality tier (1.5 Mbps → 0.6 → 0.22).
- **Noise suppression / echo**: hardware AEC + NS enabled when the phone has them (JavaAudioDeviceModule), plus WebRTC's software echo cancellation, noise suppression, high-pass and gain control on the mic.
- **Reconnect**: on disconnect the call shows “Reconnecting…”; the caller restarts ICE (new offer with `IceRestart`) every 8 s after a 4 s grace, up to 3 times; the call ends cleanly after ~28 s (`connection_lost`). The callee just answers the new offer (the existing offer handler already supports it).
- **Picture-in-picture**: leaving the app during a video call shrinks it to a floating window (controls hidden in PiP).
- **Over the lock screen**: while a call rings or runs, the call screen may show over the lock screen and wake the display; reverts when the call ends. (The ringing notification already used a full-screen intent.)
- **In-call chat** and **emoji reactions** (8 emoji, floating animation, both sides see them). Relayed over the WebSocket to the other participant only, validated server-side (500-char cap, control characters stripped, reaction whitelist), **never stored**; chat lines vanish when the call ends.
- **Call history**: filter chips (All / Missed / Voice / Video), tap a row to **call back**, and a **missed-calls badge** on the calls icon (counts missed incoming calls since you last opened the log).
- Strings in English + Persian.

## You must do
Nothing on the server (no migration, no env). Android: Gradle sync is not needed for this part (no new dependencies); `MODIFY_AUDIO_SETTINGS` was added to the manifest, and the activity now declares `supportsPictureInPicture`.

## Verified / not verified
- Verified: backend `tsc` + 132 Jest tests (new: call chat/reaction validation); `CallQuality`, `QualityTracker`, `ReconnectPolicy`, `AudioRoutePolicy`, `CallHistoryLogic`, `CallStatsParser` compiled and run with kotlinc on the JVM (all checks pass); every touched Kotlin file parses.
- **Not compiled against Android/WebRTC and never run on a device or in a real call.** Highest-risk spots: stream-webrtc API names (`JavaAudioDeviceModule.builder`, `RtpSender.getParameters/setParameters`, `getStats` report types), `AudioManager.setCommunicationDevice` behaviour on OEM builds, PiP + SurfaceView renderers, and the ICE-restart renegotiation (only the caller restarts; a simultaneous restart from both sides is not handled).

## Not built (and why)
- **Group voice/video calls**: the call model is strictly 1:1 (`caller_id`/`callee_id`). Two ways to do it: an SFU (LiveKit etc.) — but its Android SDK bundles its *own* libwebrtc and would clash with the `stream-webrtc-android` already in the app (duplicate `org.webrtc.*` classes) — or a peer-to-peer mesh on the current engine, which needs `WebRtcClient` refactored to share local tracks across several peer connections, a participants table and a grid UI (works up to ~4 people). This is a design decision for you; the mesh is the one that fits what is already in the app.
- **Screen sharing**: needs MediaProjection + a `mediaProjection` foreground service type + mid-call renegotiation (adding a video track to a voice call).
- **Background blur / replacement**: needs an on-device segmentation model (ML Kit selfie segmentation or similar) — a new dependency and a custom video-frame processor.
- **Self-managed Telecom `ConnectionService`** (system call UI integration, car/Bluetooth call buttons): not built; the existing foreground-service notification path is unchanged.
- Network-type switch detection (Wi-Fi ↔ data) to *trigger* the ICE restart immediately: restarts currently start from the disconnect itself.
