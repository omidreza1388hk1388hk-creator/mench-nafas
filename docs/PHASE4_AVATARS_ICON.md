# Phase 4 — Profile photos, app icon, splash

## Profile photos
- Settings → Account → «تغییر عکس پروفایل». The phone downsizes and compresses the picture first; the server crops it to a 256×256 JPEG (EXIF-rotated, metadata stripped) and stores it at `avatars/<userId>.jpg` in the configured storage (local / S3 / R2).
- Shown in the conversation list, in the chat top bar (with a heart/initial fallback circle when there is no photo) and on the account screen.
- Only you and people you share a conversation with can fetch a photo (`GET /users/:id/avatar`). The `?v=` in the URL is a cache-buster (`avatar_version`).
- Backend: migration `017_phase10_avatars.sql`; `POST /users/me/avatar`. Android: Room v10 → v11 (`MIGRATION_10_11`).
- Known limit: when your partner changes their photo, you see it after the next conversation-list refresh (no live event yet).

## Icon + splash
- Adaptive icon: white chat bubble with a rose heart on a rose background. Splash: rose gradient with a white heart. (Launcher PNGs aren't needed: min SDK 26 uses the adaptive XML.)
