# Build & test checklist (do this before adding anything new)

Nothing from phase 1 onward has been compiled yet. Static review found no concrete errors, but only a real build can prove it.

## 1. Backend
    cd backend
    npm install          # new deps: @aws-sdk/client-s3
    npm test             # unit tests (new: auth allowlist spec)
    npm run build
    # then run migrations (012..018 are new) and start the server
If something fails, send the FIRST error block (not the whole log).

## 2. Android
    cd android
    ./gradlew assembleDebug
Send the first `e: file:///...` Kotlin error lines (and the matching file name). Kotlin reports many follow-on errors from one root cause — the first few are what matter.

Manual one-time steps: copy Vazirmatn TTFs into app/src/main/assets/fonts/ (see README there); set backend env vars (S3_*, ALLOWED_PHONE_NUMBERS, TURN_*).

## 3. Two-phone test (in this order)
1. Update the app over an older install (tests Room migrations 8→12 and the backend migrations).
2. Login, send text/photo/voice, check ✓ / ✓✓, online/last-seen, typing dots.
3. Heart button: days together, miss you, hug, kiss, mood, letters (try a letter with a future unlock date), memories with a photo.
4. Background: Home button, send from the other phone — notification should arrive; then swipe the app away and retry; toggle airplane mode and check it reconnects.
5. Swipe-to-reply, drafts, emoji panel, archive, scroll-to-bottom button, profile photo, widget.
6. Last: `./gradlew assembleRelease` and repeat steps 2–3 on the release APK (checks the ProGuard rules).

## Known limits
No "delivered" tick yet · partner's new profile photo shows after the next list refresh · widget day count can lag ~30 min after midnight · push still needs per-phone battery/autostart settings · no end-to-end encryption.
