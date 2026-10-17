# Phase 2A (part 1) — Chat experience: voice, emoji, reactions, selection (v13)

## Done in this drop
- **Voice messages**
  - Real amplitude waveform: `VoiceRecorder` samples `maxAmplitude` every 60 ms → 48 bars (0..255) → base64 → sent with the upload (`waveform` multipart field) → stored in `attachments.waveform` → returned in message DTOs / realtime events → cached in Room (`attachmentWaveform`) → drawn by `VoiceBubbleContent`. Old voice messages without a waveform get a stable fallback shape.
  - Scrubbable seek (tap or drag on the waveform), live playhead + m:ss, playback speed 1× / 1.5× / 2× (chip on the playing bubble, remembered for the next message).
  - Hold-to-record on the mic: release = send, slide toward the start edge (mirrors in RTL) = cancel, slide up or quick tap = lock hands-free (then Cancel / Stop). Live waveform + timer replace the text field while recording. TalkBack: activating the mic starts a locked recording.
- **Emoji:** new picker (search in English + Persian, recents, 8 categories, skin-tone selector for hand/person emoji, ~465 emoji) replaces the flat grid; **big-emoji bubbles** for messages that are only 1–3 emoji (no bubble, 64/52/42 sp).
- **Reactions:** double-tap a message = default reaction (❤️, stored in `ChatPrefs`), flying-emoji burst for any newly added reaction (hearts keep their existing effect), long-press a reaction chip (or "Who reacted" in the action sheet) → **reaction details sheet** (who reacted with what; tap your own row to remove it).
- **Selection mode:** "Select" in the action sheet (or long-press while already selecting) → contextual top bar with count, copy, pin (single), forward (text only), delete (own messages only, with confirm). Back closes selection. Added "Copy" to the single-message action sheet.
- **Backend:** migration `021_phase2a_voice_waveform.sql`, DTO validation (`waveform`: base64, ≤256 chars), re-validated in `AttachmentsService` (8..128 samples, audio only, a bad waveform is dropped instead of failing the voice message), `waveform` in `AttachmentDto`, `MessageAttachmentSummary` (REST + WebSocket events). 98 backend tests pass (also fixed three stale spec fixtures from earlier phases that were failing `npm test`).

## Files
Backend: `migrations/021_*.sql`, `attachments.{controller,service,repository}.ts`, `attachment.dto.ts`, `dto/upload-attachment.dto.ts`, `messages.{repository,service}.ts`, `realtime-events.ts`, specs (attachments, messages, conversations, users, notifications).
Android new: `core/media/Waveform.kt`, `feature/chat/presentation/{EmojiData,EmojiText,EmojiPicker,ChatPrefs,VoiceMessageUi,VoiceRecordUi,ReactionDetailsSheet}.kt`, tests (`WaveformTest`, `EmojiTextTest`, `EmojiDataTest`, `ReactorsAndPrefsTest`, extra case in `ChatMappersTest`).
Android modified: `MessageEntity` + `AppDatabase` (v13→14, `MIGRATION_13_14`) + `DatabaseModule`, `ChatModels`, `ChatMappers`, `ChatApi`, `RealtimeEvents`, `OutboxPayloads`, `OutboxSyncWorker`, `ChatRepository(+Impl)`, `VoiceRecorder`, `VoicePlaybackController`, `ChatViewModel`, `ChatScreen`, `MenchNavGraph`, strings (en + fa).

## Manual steps
1. `npm run migrate` (migration 021). No new env vars, no new Gradle dependencies, no manifest changes.
2. Room schema JSON for version 14 is generated on the next build (`exportSchema = true`) — commit it.

## Verified here / not verified
- Verified: backend `tsc` + 98 Jest tests; `Waveform`, `EmojiText`, `EmojiData` compiled with kotlinc 2.0.20 and run on the JVM (60 checks, all pass, incl. every dataset emoji being detected as emoji); all modified Kotlin files parse cleanly.
- **Not compiled against Android/Compose** (no Android SDK in this environment) — send me the first Gradle errors and I will fix them in the next drop.

## Risk list (most likely to fail first)
1. Compose API names: `combinedClickable(onDoubleClick=…)`, `awaitEachGesture` / `waitForUpOrCancellation` in `holdToRecord`, `pluralStringResource`, `Icons.Filled.PushPin` / `ContentCopy` (material-icons-extended, already a dependency).
2. `MediaPlayer.playbackParams` speed on some OEM builds throws for 2× — caught, playback stays at 1×.
3. The double-tap handler delays a single tap on a *failed* message (retry) by the double-tap timeout.
4. `maxAmplitude` first sample is always 0 (harmless, averaged into the first bar).

## Still missing in Phase 2A (next drops, in this order)
GIF search (+ backend proxy), sticker packs + tray, round video messages, photo multi-select / albums / editor, polls, locations (+live), contact cards, scheduled & silent messages, translation, rich text formatting / mentions / hashtags / clickable phone-URL-email, chat-list folders / swipe actions / draft preview / mark-as-unread, floating date chips / jump-to-reply flash / seen-by / grouped bubble corners, groups & channels (roles, permissions, slow mode, topics), long-press-and-slide reaction picker, voice-to-text, earpiece/proximity switching.
## Phase 2B (calls) — not started.
