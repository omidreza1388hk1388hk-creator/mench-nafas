# Phase 5 — Reliable notifications without Firebase (for Iran)

## What it does
- A foreground service (`RealtimeForegroundService`) keeps the app's WebSocket connected while the app is in the background. It starts when you open the conversation list after signing in, restarts after a reboot (if signed in) and stops on logout. It shows one silent, minimised notification «MENCH متصل است».
- While the app is in the background, the service itself posts the notifications: new messages, hugs, kisses, letters and "I miss you". It honours your notification-privacy mode (full / sender only / hidden) and per-chat mute.
- The backend skips FCM for a user it sees as connected, and this service is what makes you "connected". If the process/socket dies, the server sees you offline and falls back to FCM automatically — the two never both fire.
- Reconnects now pick up a fresh access token (before, an expired token made every reconnect fail forever).

## You must do on each phone
1. Allow notifications when asked (Android 13+).
2. Settings → «اتصال پایدار (تنظیم باتری)» → allow "ignore battery optimisation". On Xiaomi/Huawei/Oppo/Samsung also enable Autostart / "unrestricted" background in the system app settings, and lock the app in the recents list — otherwise the vendor may still kill it.

## Notes
- Foreground type is `specialUse` (no 6h cap like dataSync). Fine for sideloading; Play Store would require a justification form.
- This still needs the phone to reach your server (Railway etc.) — test that on your actual network.
- Not done: incoming-call full-screen UI when the app is closed still relies on the existing call flow.
