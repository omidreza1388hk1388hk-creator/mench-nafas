# Phase 3B — Stories (v18.0, versionName 0.18.0-phase3b-stories)

24-hour text stories inside a conversation, with a full-screen viewer. **Not compiled or run** (no Kotlin/jest/Postgres in the sandbox).

## Behaviour
- Post a short text (≤280 chars) with an optional emoji and one of 6 gradient backgrounds; it disappears after 24 hours (expired rows are ignored by every query).
- Viewer: full screen, one progress bar per story, 5 s each, auto-advances; tap the end side to go forward, the start side to go back (mirrored in RTL); it closes after the last one. Opens at the first story you have not seen. Pauses while the composer or delete dialog is open.
- Others' stories are marked seen automatically (the author's phone updates live and shows "N seen"). You can delete your own stories. Max 10 active stories per person per conversation.
- Entry: «ما» dialog → «استوری‌ها» (direct chats). The backend itself works for any conversation member, so groups can reuse it later.

## Backend (run migration 023 before deploying)
- `023_phase3b_stories.sql` — tables `conversation_stories`, `conversation_story_views`
- New module `modules/stories`: `GET/POST /conversations/:id/stories`, `POST …/:storyId/view` (204), `DELETE …/:storyId` (204)
- Realtime event `story.changed`; `stories.service.spec.ts` (5 tests)

## Android
- New: `StoriesComponents.kt` (`StoriesViewer`, `storyBrush`, `firstUnseenIndex`, `stepStory`), `StoriesTest` (3 cases)
- Wired through `ChatApi`, `RealtimeEvents/Parser`, `ChatModels` (`Story`), `ChatRepository` (+defaults) / `Impl`, `ChatViewModel`, `ChatScreen`, `MenchNavGraph`, `UsComponents`; strings EN + FA

## Likeliest compile errors
`Dialog` + `DialogProperties(decorFitsSystemWindows = ...)` (needs a recent Compose UI), `Animatable` progress inside `LaunchedEffect` with `tween((…).toInt())`, `Modifier.weight` scope inside the viewer's Row/Column.

## Not included
Photo/video stories (would reuse the attachment upload pipeline), reactions/replies to stories, a "stories ring" on the conversation list, push notification for new stories.
