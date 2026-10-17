# Phase 2 — what is in this zip

This archive is the **whole project** (Phase 1 base v12 + everything built for Phase 2, v13 → v20), because Phase 2 changes many existing files and cannot be separated from them. Below is exactly what Phase 2 added or changed relative to the v12 base you uploaded.

## Read first
- `docs/PHASE2_STATUS.md` — honest done / not-built table for 2A and 2B.
- One doc per drop: `PHASE2A_CHAT_PART1..7`, `PHASE2B_CALLS_PART1` (files, manual steps, risks of each).

## Everything you must run / set

1. **Migrations** (in order, `npm run migrate`): `021_phase2a_voice_waveform`, `022_phase2a_polls`, `023_phase2a_scheduled_messages`, `024_phase2a_group_governance`.
2. **Backend `.env`** (all optional): `GIPHY_API_KEY`, `GIPHY_RATING`, `MAP_TILE_URL_TEMPLATE`, `MAP_TILE_USER_AGENT` — see `backend/.env.example`.
3. **Android**: Gradle sync (new dependencies: `coil-gif:2.7.0`, `camera-*:1.3.4`); Room database version 14 (migration `MIGRATION_13_14` included; commit the generated schema JSON).
4. **Build both** and send me the first Gradle errors — the Android code was never compiled against the Android SDK (see PHASE2_STATUS.md).

## Features included

**2A chat:** voice waveform / scrub / speed / hold-to-record · emoji picker + big emoji · reaction details, bursts, double-tap · selection mode · chat-list folders, filters, swipe actions, mark unread · GIF search (Giphy via server proxy) · 34 romantic stickers + make-your-own · polls · location + map preview · animated attach menu · round video messages (+ received videos now play) · silent + scheduled messages · text formatting, clickable links/phones/emails/hashtags · contact cards · group roles, permissions, channels, slow mode, invite codes.

**2B calls:** audio route sheet · proximity · quality indicator + adaptive bitrate · noise suppression · reconnect with ICE restart · PiP · over-lock-screen · in-call chat + reactions · call history filters, call-back, missed badge.

## Not built (see PHASE2_STATUS.md for reasons)

2A: photo albums/editor, live location, translation, Saved messages, typing indicator in list row, pinned-chat limit, auto-unarchive, floating date chips / first-unread / reply flash / per-chat theme / seen-by / edit history, slide-to-pick reactions, voice earpiece switching + voice-to-text, server-hosted sticker packs, topics, formatting toolbar, mention autocomplete.  2B: group calls, screen sharing, background blur/replacement, system ConnectionService.

## Files added and changed since v12

### Added (105)

**Backend (src)** (36)

- `backend/package-lock.json`
- `backend/src/modules/calls/call-extras.spec.ts`
- `backend/src/modules/calls/call-extras.ts`
- `backend/src/modules/conversations/group-policy.spec.ts`
- `backend/src/modules/conversations/group-policy.ts`
- `backend/src/modules/gifs/gifs.controller.ts`
- `backend/src/modules/gifs/gifs.module.ts`
- `backend/src/modules/gifs/gifs.service.spec.ts`
- `backend/src/modules/gifs/gifs.service.ts`
- `backend/src/modules/group-admin/dto/group-admin.dto.ts`
- `backend/src/modules/group-admin/group-admin.controller.ts`
- `backend/src/modules/group-admin/group-admin.module.ts`
- `backend/src/modules/group-admin/group-admin.repository.ts`
- `backend/src/modules/group-admin/group-admin.service.spec.ts`
- `backend/src/modules/group-admin/group-admin.service.ts`
- `backend/src/modules/maps/maps.controller.ts`
- `backend/src/modules/maps/maps.module.ts`
- `backend/src/modules/maps/maps.service.spec.ts`
- `backend/src/modules/maps/maps.service.ts`
- `backend/src/modules/messages/rich-body.spec.ts`
- `backend/src/modules/messages/rich-body.ts`
- `backend/src/modules/polls/dto/create-poll.dto.ts`
- `backend/src/modules/polls/dto/vote-poll.dto.ts`
- `backend/src/modules/polls/poll.dto.ts`
- `backend/src/modules/polls/polls.controller.ts`
- `backend/src/modules/polls/polls.module.ts`
- `backend/src/modules/polls/polls.repository.ts`
- `backend/src/modules/polls/polls.service.spec.ts`
- `backend/src/modules/polls/polls.service.ts`
- `backend/src/modules/scheduled-messages/dto/create-scheduled-message.dto.ts`
- `backend/src/modules/scheduled-messages/scheduled-message.dto.ts`
- `backend/src/modules/scheduled-messages/scheduled-messages.controller.ts`
- `backend/src/modules/scheduled-messages/scheduled-messages.module.ts`
- `backend/src/modules/scheduled-messages/scheduled-messages.repository.ts`
- `backend/src/modules/scheduled-messages/scheduled-messages.service.spec.ts`
- `backend/src/modules/scheduled-messages/scheduled-messages.service.ts`

**Backend (migrations)** (4)

- `backend/src/database/migrations/021_phase2a_voice_waveform.sql`
- `backend/src/database/migrations/022_phase2a_polls.sql`
- `backend/src/database/migrations/023_phase2a_scheduled_messages.sql`
- `backend/src/database/migrations/024_phase2a_group_governance.sql`

**Android (main)** (43)

- `android/app/src/main/java/com/omidgame/mench/core/media/Waveform.kt`
- `android/app/src/main/java/com/omidgame/mench/core/network/GifApi.kt`
- `android/app/src/main/java/com/omidgame/mench/core/network/MapUrls.kt`
- `android/app/src/main/java/com/omidgame/mench/core/webrtc/CallAudioRouter.kt`
- `android/app/src/main/java/com/omidgame/mench/core/webrtc/ProximityScreenOff.kt`
- `android/app/src/main/java/com/omidgame/mench/feature/calls/domain/AudioRoute.kt`
- `android/app/src/main/java/com/omidgame/mench/feature/calls/domain/CallHistoryLogic.kt`
- `android/app/src/main/java/com/omidgame/mench/feature/calls/domain/CallQuality.kt`
- `android/app/src/main/java/com/omidgame/mench/feature/calls/domain/CallStatsParser.kt`
- `android/app/src/main/java/com/omidgame/mench/feature/calls/domain/ReconnectPolicy.kt`
- `android/app/src/main/java/com/omidgame/mench/feature/calls/presentation/CallExtrasUi.kt`
- `android/app/src/main/java/com/omidgame/mench/feature/calls/presentation/CallPrefs.kt`
- `android/app/src/main/java/com/omidgame/mench/feature/chat/domain/GroupRules.kt`
- `android/app/src/main/java/com/omidgame/mench/feature/chat/presentation/AttachMenuSheet.kt`
- `android/app/src/main/java/com/omidgame/mench/feature/chat/presentation/ChatFolders.kt`
- `android/app/src/main/java/com/omidgame/mench/feature/chat/presentation/ChatListFolderUi.kt`
- `android/app/src/main/java/com/omidgame/mench/feature/chat/presentation/ChatListPrefs.kt`
- `android/app/src/main/java/com/omidgame/mench/feature/chat/presentation/ChatPrefs.kt`
- `android/app/src/main/java/com/omidgame/mench/feature/chat/presentation/ContactUi.kt`
- `android/app/src/main/java/com/omidgame/mench/feature/chat/presentation/EmojiData.kt`
- `android/app/src/main/java/com/omidgame/mench/feature/chat/presentation/EmojiPicker.kt`
- `android/app/src/main/java/com/omidgame/mench/feature/chat/presentation/EmojiText.kt`
- `android/app/src/main/java/com/omidgame/mench/feature/chat/presentation/GifQuery.kt`
- `android/app/src/main/java/com/omidgame/mench/feature/chat/presentation/GifUi.kt`
- `android/app/src/main/java/com/omidgame/mench/feature/chat/presentation/GifViewModel.kt`
- `android/app/src/main/java/com/omidgame/mench/feature/chat/presentation/GroupGovernanceUi.kt`
- `android/app/src/main/java/com/omidgame/mench/feature/chat/presentation/JoinGroupDialog.kt`
- `android/app/src/main/java/com/omidgame/mench/feature/chat/presentation/LocationUi.kt`
- `android/app/src/main/java/com/omidgame/mench/feature/chat/presentation/MapTiles.kt`
- `android/app/src/main/java/com/omidgame/mench/feature/chat/presentation/PollUi.kt`
- `android/app/src/main/java/com/omidgame/mench/feature/chat/presentation/ReactionDetailsSheet.kt`
- `android/app/src/main/java/com/omidgame/mench/feature/chat/presentation/RichBody.kt`
- `android/app/src/main/java/com/omidgame/mench/feature/chat/presentation/RichBodyLabels.kt`
- `android/app/src/main/java/com/omidgame/mench/feature/chat/presentation/RichText.kt`
- `android/app/src/main/java/com/omidgame/mench/feature/chat/presentation/RichTextUi.kt`
- `android/app/src/main/java/com/omidgame/mench/feature/chat/presentation/RoundVideo.kt`
- `android/app/src/main/java/com/omidgame/mench/feature/chat/presentation/RoundVideoUi.kt`
- `android/app/src/main/java/com/omidgame/mench/feature/chat/presentation/SchedulePolicy.kt`
- `android/app/src/main/java/com/omidgame/mench/feature/chat/presentation/ScheduleUi.kt`
- `android/app/src/main/java/com/omidgame/mench/feature/chat/presentation/StickerCatalog.kt`
- `android/app/src/main/java/com/omidgame/mench/feature/chat/presentation/StickerUi.kt`
- `android/app/src/main/java/com/omidgame/mench/feature/chat/presentation/VoiceMessageUi.kt`
- `android/app/src/main/java/com/omidgame/mench/feature/chat/presentation/VoiceRecordUi.kt`

**Android (tests)** (13)

- `android/app/src/test/java/com/omidgame/mench/core/media/WaveformTest.kt`
- `android/app/src/test/java/com/omidgame/mench/feature/calls/domain/CallLogicTest.kt`
- `android/app/src/test/java/com/omidgame/mench/feature/chat/domain/GroupRulesTest.kt`
- `android/app/src/test/java/com/omidgame/mench/feature/chat/presentation/ChatFoldersTest.kt`
- `android/app/src/test/java/com/omidgame/mench/feature/chat/presentation/EmojiDataTest.kt`
- `android/app/src/test/java/com/omidgame/mench/feature/chat/presentation/EmojiTextTest.kt`
- `android/app/src/test/java/com/omidgame/mench/feature/chat/presentation/GifQueryTest.kt`
- `android/app/src/test/java/com/omidgame/mench/feature/chat/presentation/PollAndMapTest.kt`
- `android/app/src/test/java/com/omidgame/mench/feature/chat/presentation/ReactorsAndPrefsTest.kt`
- `android/app/src/test/java/com/omidgame/mench/feature/chat/presentation/RichBodyTest.kt`
- `android/app/src/test/java/com/omidgame/mench/feature/chat/presentation/RichTextTest.kt`
- `android/app/src/test/java/com/omidgame/mench/feature/chat/presentation/RoundVideoTest.kt`
- `android/app/src/test/java/com/omidgame/mench/feature/chat/presentation/SchedulePolicyTest.kt`

**Docs** (9)

- `docs/PHASE2A_CHAT_PART1.md`
- `docs/PHASE2A_CHAT_PART2.md`
- `docs/PHASE2A_CHAT_PART3_GIF_STICKERS.md`
- `docs/PHASE2A_CHAT_PART4_POLLS_LOCATION.md`
- `docs/PHASE2A_CHAT_PART5_ROUND_VIDEO.md`
- `docs/PHASE2A_CHAT_PART6_SCHEDULED_RICHTEXT_CONTACTS.md`
- `docs/PHASE2A_CHAT_PART7_GROUPS_CHANNELS.md`
- `docs/PHASE2B_CALLS_PART1.md`
- `docs/PHASE2_STATUS.md`

### Modified (67)

**Backend (src)** (23)

- `backend/.env.example`
- `backend/src/app.module.ts`
- `backend/src/config/env.validation.ts`
- `backend/src/modules/attachments/attachment.dto.ts`
- `backend/src/modules/attachments/attachments.controller.ts`
- `backend/src/modules/attachments/attachments.repository.ts`
- `backend/src/modules/attachments/attachments.service.spec.ts`
- `backend/src/modules/attachments/attachments.service.ts`
- `backend/src/modules/attachments/dto/upload-attachment.dto.ts`
- `backend/src/modules/conversations/conversations.repository.ts`
- `backend/src/modules/conversations/conversations.service.spec.ts`
- `backend/src/modules/conversations/conversations.service.ts`
- `backend/src/modules/messages/dto/send-message.dto.ts`
- `backend/src/modules/messages/messages.controller.ts`
- `backend/src/modules/messages/messages.module.ts`
- `backend/src/modules/messages/messages.repository.ts`
- `backend/src/modules/messages/messages.service.spec.ts`
- `backend/src/modules/messages/messages.service.ts`
- `backend/src/modules/notifications/notifications.service.spec.ts`
- `backend/src/modules/notifications/notifications.service.ts`
- `backend/src/modules/realtime/chat.gateway.ts`
- `backend/src/modules/realtime/realtime-events.ts`
- `backend/src/modules/users/users.service.spec.ts`

**Android (main)** (41)

- `android/app/build.gradle.kts`
- `android/app/src/main/AndroidManifest.xml`
- `android/app/src/main/java/com/omidgame/mench/MainActivity.kt`
- `android/app/src/main/java/com/omidgame/mench/MenchApplication.kt`
- `android/app/src/main/java/com/omidgame/mench/core/database/AppDatabase.kt`
- `android/app/src/main/java/com/omidgame/mench/core/database/MessageEntity.kt`
- `android/app/src/main/java/com/omidgame/mench/core/di/DatabaseModule.kt`
- `android/app/src/main/java/com/omidgame/mench/core/di/ImageLoaderModule.kt`
- `android/app/src/main/java/com/omidgame/mench/core/di/NetworkModule.kt`
- `android/app/src/main/java/com/omidgame/mench/core/media/VoicePlaybackController.kt`
- `android/app/src/main/java/com/omidgame/mench/core/media/VoiceRecorder.kt`
- `android/app/src/main/java/com/omidgame/mench/core/network/ChatApi.kt`
- `android/app/src/main/java/com/omidgame/mench/core/network/realtime/RealtimeEventParser.kt`
- `android/app/src/main/java/com/omidgame/mench/core/network/realtime/RealtimeEvents.kt`
- `android/app/src/main/java/com/omidgame/mench/core/ui/components/Motion.kt`
- `android/app/src/main/java/com/omidgame/mench/core/ui/navigation/MenchNavGraph.kt`
- `android/app/src/main/java/com/omidgame/mench/core/webrtc/WebRtcClient.kt`
- `android/app/src/main/java/com/omidgame/mench/core/webrtc/WebRtcEglContextHolder.kt`
- `android/app/src/main/java/com/omidgame/mench/core/work/OutboxPayloads.kt`
- `android/app/src/main/java/com/omidgame/mench/core/work/OutboxSyncWorker.kt`
- `android/app/src/main/java/com/omidgame/mench/feature/calls/data/CallRepositoryImpl.kt`
- `android/app/src/main/java/com/omidgame/mench/feature/calls/domain/CallModels.kt`
- `android/app/src/main/java/com/omidgame/mench/feature/calls/domain/CallRepository.kt`
- `android/app/src/main/java/com/omidgame/mench/feature/calls/domain/CallSessionManager.kt`
- `android/app/src/main/java/com/omidgame/mench/feature/calls/domain/CallUiState.kt`
- `android/app/src/main/java/com/omidgame/mench/feature/calls/presentation/CallHistoryDialog.kt`
- `android/app/src/main/java/com/omidgame/mench/feature/calls/presentation/CallOverlay.kt`
- `android/app/src/main/java/com/omidgame/mench/feature/calls/presentation/CallViewModel.kt`
- `android/app/src/main/java/com/omidgame/mench/feature/chat/data/ChatMappers.kt`
- `android/app/src/main/java/com/omidgame/mench/feature/chat/data/ChatRepositoryImpl.kt`
- `android/app/src/main/java/com/omidgame/mench/feature/chat/domain/ChatModels.kt`
- `android/app/src/main/java/com/omidgame/mench/feature/chat/domain/ChatRepository.kt`
- `android/app/src/main/java/com/omidgame/mench/feature/chat/domain/SendMessageUseCase.kt`
- `android/app/src/main/java/com/omidgame/mench/feature/chat/presentation/ChatScreen.kt`
- `android/app/src/main/java/com/omidgame/mench/feature/chat/presentation/ChatViewModel.kt`
- `android/app/src/main/java/com/omidgame/mench/feature/chat/presentation/ConversationListScreen.kt`
- `android/app/src/main/java/com/omidgame/mench/feature/chat/presentation/ConversationListUiState.kt`
- `android/app/src/main/java/com/omidgame/mench/feature/chat/presentation/ConversationListViewModel.kt`
- `android/app/src/main/java/com/omidgame/mench/feature/chat/presentation/GroupInfoScreen.kt`
- `android/app/src/main/java/com/omidgame/mench/feature/chat/presentation/GroupInfoUiState.kt`
- `android/app/src/main/java/com/omidgame/mench/feature/chat/presentation/GroupInfoViewModel.kt`

**Android (tests)** (1)

- `android/app/src/test/java/com/omidgame/mench/feature/chat/data/ChatMappersTest.kt`

**Android (resources)** (2)

- `android/app/src/main/res/values-fa/strings.xml`
- `android/app/src/main/res/values/strings.xml`
