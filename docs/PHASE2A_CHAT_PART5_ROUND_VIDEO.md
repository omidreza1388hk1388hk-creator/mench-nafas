# Phase 2A (part 5) — Round video messages (v17)

## Done
- **Record**: new camera button in the composer (shown when the text box is empty) opens a full-screen recorder: circular live preview (CameraX), tap the big button to start, tap again to stop, ring fills up to **60 s** and auto-stops, flip front/back camera, close = throw the take away. A take shorter than 1 s is treated as an accidental tap and discarded. Front camera by default, SD quality (small files on weak networks).
- **Send**: a round video is an ordinary video attachment (same upload, thumbnail, outbox and delivery path), identified only by its file name `round-video-<ts>.mp4` — **no backend change, no migration**; old app versions simply show a normal video.
- **Show**: circular bubble without a message bubble (when not a reply/forward), thumbnail + play button + duration chip; tap plays it **in place** with sound, a ring shows progress, tap again stops.
- **Also fixed**: received videos could never be played in-app (only the sender's own copy). The video viewer now streams with the bearer token (`VideoView.setVideoURI(uri, headers)`), so *all* received videos play.
- Strings en + fa.

## You must do
Gradle sync — new dependencies: `androidx.camera:camera-core/camera2/lifecycle/video/view:1.3.4`. Manifest already had `CAMERA` / `RECORD_AUDIO`.

## Verified / not verified
- Verified: `RoundVideo` logic run with kotlinc on the JVM; all touched Kotlin parses; backend untouched (130 tests still pass).
- **Not compiled against Android/CameraX and never run on a device.** Highest-risk spots: CameraX API names in `RoundVideoRecorderDialog` (`Recorder`, `FileOutputOptions`, `VideoRecordEvent.Finalize`, `QualitySelector`), `LocalLifecycleOwner` import, and the cover-scaling of `VideoView` inside the circle.

## Known limits
- Recording is tap-to-start / tap-to-stop (not hold-to-record), no pause, no filters.
- Playback starts on tap (no muted autoplay loop in the list), one player per bubble.
- The thumbnail of a *received* round video is the server-generated one (rectangular, center-cropped into the circle).
