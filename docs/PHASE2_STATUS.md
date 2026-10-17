# Phase 2 — honest status after v20

## 2A — Chat experience
| Item | Status |
|---|---|
| Emoji picker (search, skin tones, recents), big-emoji bubbles | **done** (v13) |
| GIF search (provider-abstracted + backend proxy) | **done** (v15) — needs `GIPHY_API_KEY` |
| Stickers: built-in romantic set, recents, make-your-own | **done** (v15) — *server-hosted Lottie/animated packs: not built* |
| Reactions: burst, who-reacted sheet, double-tap default | **done** (v13) — *long-press-and-slide picker, configurable default (stored, no settings UI yet): not built* |
| Voice: true waveform, scrub, 1×/1.5×/2×, lock + slide-to-cancel, live timer | **done** (v13) — *proximity/earpiece switching, voice-to-text: not built* |
| Round video messages | **done** (v17) — record, send, in-place playback; received videos now play too |
| Photos: multi-select, albums, editor, viewer transitions, send-as-original | **not built** |
| Selection mode (delete/forward/copy/pin) | **done** (v13) |
| Polls, static location, attach grid sheet | **done** (v16) |
| Live location | **not built** |
| Contact cards, scheduled messages, silent messages | **done** (v19) |
| Text formatting/spoiler, hashtags, clickable phone/URL/email, mention styling | **done** (v19) — *formatting toolbar, mention autocomplete: not built* |
| Translation | **not built** |
| Chat list: folders, swipe actions, draft preview, mark unread | **done** (v14) — *typing indicator in row, Saved chat, pinned-chat limit, auto-unarchive: not built* |
| Chat screen polish (floating date chips, first-unread, jump-to-reply flash, per-chat wallpaper/themes, grouped corners, seen-by, edit history, IME) | **not built** (a few exist from phase 1) |
| Groups & channels: roles, permissions, invite links, slow mode, channels | **done** (v20) — *topics, ownership transfer, https deep-link invites: not built* |

## 2B — Calls
| Item | Status |
|---|---|
| Audio routing sheet (earpiece / speaker / Bluetooth / wired) | **done** (v18) |
| Call quality indicator, adaptive bitrate | **done** (v18) |
| Reconnect with ICE restart + “Reconnecting…” UI | **done** (v18) |
| Noise suppression / echo cancellation | **done** (v18) |
| Proximity screen-off | **done** (v18) |
| Picture-in-picture | **done** (v18) |
| Incoming call over the lock screen (+ existing full-screen intent) | **done** (v18) |
| In-call chat, emoji reactions | **done** (v18) |
| Call history: filters, call back, missed badge | **done** (v18) |
| Group calls | **not built** — needs a decision: P2P mesh on the current engine (fits) vs SFU (SDK clashes with current WebRTC lib) |
| Screen sharing | **not built** |
| Background blur / replacement | **not built** (needs a segmentation model) |
| System Telecom `ConnectionService` integration | **not built** |

## Rule of thumb for what is “done”
Backend: compiled and unit-tested here. Android: parse-checked and its pure logic executed on the JVM, but **never compiled against the Android SDK** — expect a first round of Gradle errors to fix.
