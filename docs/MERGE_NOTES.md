# MENCH — all-phases merge (v4.0.0-all-phases)

This tree merges four branches that were built separately on the same v12 base:
phase 1 (1A–1D), phase 2 (2A/2B, `mench-phase2-complete`), phase 3 (`MENCH-phase3`, v3.0.0) and phase 4 parts 1–3.

## Decisions made while merging
- Backend migrations renumbered into one sequence (020 … 039). `migrate.ts` has a `FORMER_NAMES` map so a
  database that already ran a migration under its old name does not run it again.
- `020_phase12_account_safety` was dropped: `020_phase1c_account_ops` is a superset (same tables/columns).
- Room is version 15: 13→14 (blur hash, phase 1), 14→15 (voice waveform, phase 2). Phases 3–4 add no Room changes.
- Chat image viewer: phase 3's cinematic viewer replaces phase 2's grow-from-thumbnail viewer.
- `strings.xml` (en/fa) rebuilt as a union of all four branches; 3 (en) / 8 (fa) same-name keys differed and were resolved in favour of the newer/product text.
- Phase 4's tablet two-pane `ChatRoute` now hosts the full merged `ChatScreen` call.

## Added in this drop
- Message edit history: `message_edits` table (migration 029), `GET /conversations/:id/messages/:id/edits`, tap "edited" to see earlier versions.
- Group member profile photos available to the chat screen (`avatarVersion` on members).
- Remote crash reporting: crashes from earlier runs are sent once via the feedback endpoint (category `crash`, visible in the admin panel).
- Vazirmatn (Regular/Medium/SemiBold/Bold) bundled in assets/fonts.
- `.github/workflows/build-apk.yml`: builds an installable debug APK on GitHub (Actions → Build APK → artifact `mench-debug-apk`).

## Round 2
- Vazirmatn (Light → ExtraBold) is the app font, bundled offline. Estedad is not wired in (no font switch exists yet).
- UI sounds from the Kenney pack (CC0): message sent / received, with one switch in Appearance settings.
- Gallery picker (up to 10 photos) → full-screen composer: rotate, crop presets, 7 filters, draw, text, per-photo caption, "send at original quality".
- Albums: photos sent together are drawn as one grid bubble (client-side grouping: same sender, <= 60 s apart, only the first photo may have a caption). Long-press actions on an album apply to its first photo.

## Round 3
- Phase 2: the chat opens at the first unread message (the unread badge shows the count) and a floating date chip appears while scrolling.
- Gulzar (Nastaliq) is bundled and used for love letters (reading + writing) and surprise messages. Everything else stays Vazirmatn.
- Surprise messages: in the send-options sheet (long-press Send) switch on "surprise" before choosing a time. The receiver gets a gift to tap; opening plays a short reveal. Server: body marker `surprise:v1:...` (push shows "[surprise]", no text leak). Max 200 characters.
- Together mode (direct chats): attach menu -> "Together" -> pick a video/audio from the chat. The partner gets an invitation; play, pause and seek (±10 s) are mirrored live. Server only relays small events (`POST /conversations/:id/together`, nothing stored). Needs both phones to have the file in the chat.
- Bubble skins per chat: Classic / Glass / Neon / Paper, chosen in the chat's theme dialog (local to the phone).

## Round 4 — phase 4
- End-to-end encryption (1:1 chats, opt-in per chat from the lock icon in the chat bar). EC P-256 identity key per account, private key only on the phone (Keystore-backed); each message is AES-256-GCM encrypted once and the key is wrapped for the partner and for yourself. Server stores only `e2ee:v2:...` and the public keys (`PUT /users/me/e2ee-key`, `GET /users/:id/e2ee-key`, migration 040). Not covered: groups, media files, and live-location/poll bodies still work but are only hidden from the server as part of the encrypted text; forwarding a secure message re-sends it as fresh text. Texts are limited to roughly 1,200 Persian characters in secure chats.
- Per-chat lock: biometrics (or the phone's screen lock) every time the chat opens; the list preview shows a lock.
- View-once photos: switch in the photo composer. The server lets the recipient download the file exactly once (`consumed_at`, HTTP 410 afterwards) and gives them no thumbnail; the app shows it in a screenshot-blocked, uncached viewer.
- Encrypted backup/restore (Settings -> Data & storage): passphrase (PBKDF2 210k + AES-256-GCM). Contains the E2EE identity key, local settings and a readable message archive. Restoring puts back the key and settings; restart the app afterwards.
- Remote crash reports, `/metrics` (Prometheus text, only when METRICS_TOKEN is set), ktlint/detekt workflow (non-blocking).

## Not done
- Phase 2: voice-to-text, earpiece switching, server sticker packs, topics, text-formatting toolbar, https invite links, group calls, screen sharing, background blur, Telecom integration.
- Phase 4: nothing planned is left except what is listed under "Known limits" below.

## Known limits
- E2EE has no key-change warning / safety number screen yet, one identity key per account (moving phones = restore the backup), and groups are not encrypted.
- A screenshot of a view-once photo is blocked by the OS flag, but a second phone camera cannot be.
- Phase 3 leftovers still missing: live "I'm coming" location tracker, music stories, counter widget.
- Nothing has been compiled or run. Expect a first round of compile errors; send them over.
