# Phase 1 completion (v15)

## Added
- Per-chat notification sound/vibration/silent dialog (NotificationPolicy.chatSoundUri, feature/product/ChatSoundDialog, chat-row menu).
- Contact privacy: Settings > Contact privacy. On-device sync switch (ContactSyncPrefs) + server flag `discoverable_by_phone` (GET/PATCH users/me/contact-privacy, migration 023).
- Data usage per chat (UsageTracker.byChat, DataUsageScreen). Attachment downloads are not attributed (their URLs carry no conversation id).
- Auto-download rules enforced in bubbles for photos/videos (chat/presentation/MediaGate.kt).
- :benchmark module (StartupBenchmark, ScrollBenchmark, BaselineProfileGenerator), app build type `benchmark`, `<profileable>`.
- Glance widgets: CoupleCounterWidget, PartnerQuickWidget (never show message text).
- Jalali date/time picker (core/ui/components/JalaliDateTimePickerDialog) used by reminders/events.
- Event participants (server + app + event card) and an editable/toggleable checklist for tasks.
- Cross-device draft sync (opt-in, Settings > Sync): backend modules/sync, table sync_state; app DraftSync.
- Video transcoding before upload (core/media/VideoTranscoder, Media3 Transformer, in OutboxSyncWorker): longer edge capped at 1280, H.264/AAC, progress ring + cancel; falls back to the original file if it fails or is not smaller.
- Appearance sync across devices (feature/appearance/data/AppearanceSync, Settings > Sync; key `setting:appearance`).
- Resumable chunked uploads for files >= 8 MB: backend attachments/chunked-upload.{service,controller}.ts (+spec) with endpoints POST/GET conversations/:id/uploads, PUT .../chunks/:index, PUT .../thumbnail, POST .../complete; Android core/media/ChunkedUploader used by OutboxSyncWorker (resumes from chunks the server already has, progress + cancel). Staging is on the server's temp disk (per instance; use sticky routing or a shared volume when scaled out; stale folders purged after 24 h).
- BlurHash placeholders end to end: migration adds attachments.blur_hash; server computes it (npm `blurhash` + sharp) for images and video thumbnails; exposed in AttachmentDto and message attachment summaries; Android Room v14 (MIGRATION_13_14), mappers, core/media/BlurHashDecoder, blurred preview in the image bubble until the thumbnail loads.
- Windowed chat history (instead of full Paging 3 + RemoteMediator): ChatViewModel loads the newest 300 messages and 300 more each time you scroll to the top (viewport stays anchored); search loads everything. DAO query MessageDao.observeWindow. Every list consumer keeps the same List<Message> API.

## Manual steps
1. Run migration `023_phase1_completion.sql` and `npm install` in backend (new dep: blurhash).
2. Gradle sync (new deps: glance-appwidget 1.1.0, media3-transformer/effect/common 1.4.1; new module :benchmark).
3. Baseline profile: run BaselineProfileGenerator on a signed-in device, copy output over app/src/main/baseline-prof.txt.

## Risks (not compiled)
- Glance imports (`androidx.glance.appwidget.action.actionStartActivity/actionRunCallback`).
- ICU `PersianCalendar` calls in the Jalali picker.
- Media3 Transformer API names (Presentation.createForHeight/Width, ProgressHolder) at 1.4.1.
- Room: DB version 14; verify upgrade from a v13 install.
- ChatScreen windowed-history anchor logic: test scrolling up through >300 messages.
- Hilt: ContactsRepositoryImpl gained two constructor params.
- Benchmark tap-by-coordinate opens the first chat; adjust if the list layout differs.

## Still missing from Phase 1
Full Paging 3 + RemoteMediator (replaced by windowed history above), notification bubbles (needs a dedicated bubble activity hosting the chat),
Android Auto/Wear, web WebSocket + attachments, native desktop client, real billing, realtime WS event for sync_state,
sync of settings beyond drafts and appearance.
