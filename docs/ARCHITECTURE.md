# MENCH Architecture

## Overview

Clean-architecture-flavored, split by feature module on Android and by
domain module on the backend. The goal of Phase 1 was to fix these
boundaries early so later phases are additive.

## Backend (`/backend`)

NestJS, TypeScript, PostgreSQL (raw `pg`, no ORM — every query is
inspectable SQL), Redis (OTP cooldown/rate-limit state).

```
src/
  config/          env validation (fails fast on missing/invalid config)
  database/        Pool provider + plain numbered SQL migrations + runner
  redis/           Redis client provider
  common/filters/  global exception filter (sanitizes error responses)
  modules/
    auth/          OTP request/verify, SmsProvider interface + dev impl,
                    JWT access tokens, controller, module wiring
    users/         user lookup/creation by phone
    devices/       device registration, listing, revocation
    sessions/      refresh-token issuance/rotation/revocation, TokenService
```

Each module exposes a narrow public surface (its `*.module.ts` `exports`
array); nothing reaches into another module's repository directly.

### Extension points reserved for later phases

- `messages`, `conversations`, `conversation_members` tables exist in the
  Phase 1 migration with the FK/index shape already fixed, but have no
  service/controller — Phase 2 adds those without an ALTER TABLE that
  could touch existing data.
- `SmsProvider` interface (`modules/auth/sms-provider.interface.ts`) —
  Phase 2+ adds a real carrier implementation behind this same interface;
  nothing else in the auth module needs to change.
- `devices.public_key` column exists now, unused, reserved for Phase 5 E2EE
  key registration.
- WebSocket gateway, media/S3 module, calls/TURN module: not present yet;
  add as new top-level modules under `src/modules/` following the same
  repository+service+controller shape as `auth`/`users`.

## Android (`/android`)

Kotlin, Jetpack Compose, Hilt, Room, Retrofit/OkHttp, Coroutines/Flow.

```
core/
  network/    Retrofit AuthApi, two OkHttp clients (unauthenticated vs
              authenticated+auto-refresh), TokenAuthenticator
  database/   Room AppDatabase, entities, DAOs
  security/   TokenStore (Android Keystore-backed, via EncryptedSharedPreferences)
  di/         Hilt modules (Network, Database, Auth bindings)
  ui/theme/   Design tokens (Color/Type/Shape/Theme) — dark/light
  ui/navigation/  Nav graph: Splash -> Auth -> Home

feature/
  splash/     Decides Auth vs Home based on stored session
  auth/       domain/ (repository interface, use cases, models)
              data/   (AuthRepositoryImpl — the only class that knows
                       about Retrofit/Room/EncryptedSharedPreferences)
              presentation/ (ViewModel, Compose screens)
  home/       Minimal post-auth landing screen (Phase 2+ replaces this
              with the actual chat list)
```

### Why the repository interface lives in `domain`, not `data`

`AuthViewModel` and the use cases depend only on `AuthRepository`
(the interface). `AuthRepositoryImpl` — the thing that actually calls
Retrofit, Room, and EncryptedSharedPreferences — is bound to that
interface in `AuthBindingsModule` via Hilt `@Binds`. This means Phase 2+
can change transport details (e.g. add a WebSocket-based session refresh,
swap Retrofit internals) without touching the ViewModel or any Compose
screen.

### Extension points reserved for later phases

- `core/database/AppDatabase.kt` — new entities are added via new Room
  `Migration` objects, never `fallbackToDestructiveMigration`.
- `feature/*` package stubs referenced in the product brief (chat, media,
  calls, settings, privacy, storage, sync) are not yet created as
  directories — create them following the same
  `data/domain/presentation` split as `feature/auth` when Phase 2 starts.
- `core/network/AuthenticatedClient` qualifier — Phase 2+ APIs (messages,
  media, etc.) should use this client so they get auto-refresh-on-401 for
  free, rather than each feature reinventing token handling.

## Phase 2: real-time messaging

Adds conversations, text messages, and a WebSocket layer, on top of
Phase 1's auth foundation without changing it.

### Backend additions

```
src/modules/
  conversations/  direct-conversation creation (idempotent), membership
                   checks (404, not 403, for non-members), other-member
                   lookup for the conversation list
  messages/        idempotent send (client_msg_id dedupe), paginated
                   history (cursor = message.sequence), read receipts
  realtime/        raw-'ws' WebSocket gateway + REALTIME_BROADCASTER
                   interface (same pattern as SmsProvider in Phase 1)
```

**JwtAuthGuard moved from `modules/auth/` to `modules/sessions/`.** Its
only real dependency is `TokenService`. Once `UsersModule` needed the
guard too (for the new phone-lookup endpoint), leaving it in `AuthModule`
would have created `UsersModule → AuthModule → UsersModule`, an actual
circular import. `SessionsModule` has no dependents that could create the
same problem, so it's the guard's real home now.

**WebSocket protocol.** Uses `@nestjs/platform-ws` (raw `ws`), not the
default socket.io adapter — the wire format is plain JSON matching
`realtime-events.ts`, decodable by any WebSocket client including
OkHttp's built-in one on Android, with no socket.io protocol/client
library needed on either side. Mounted at `/ws`, **not** `/api/v1/ws` —
`app.setGlobalPrefix()` only applies to the HTTP router. Auth happens
once, at connect time, via `?token=<accessToken>` in the connection URL;
there is no periodic re-validation of a long-lived connection (see
docs/SECURITY.md for why that's an accepted Phase 2 limitation, not a
silent gap).

**Message state.** The DB only knows two server-observable states: a
message exists (SENT) and has been acknowledged read (via
`read_receipts`). DELIVERED (as in: another device actually received the
push before the recipient opened the app) isn't tracked — doing that
honestly needs per-device delivery acks, which is real added
infrastructure, not a one-column addition. Documented as a gap rather
than faked.

### Android additions

```
core/database/    ConversationEntity, MessageEntity, OutboxEntity + DAOs,
                   AppDatabase v1→v2 via a real Migration (not
                   fallbackToDestructiveMigration)
core/network/      ChatApi (REST), network/realtime/ (RealtimeClient —
                   OkHttp WebSocket with backoff reconnect, on its own
                   OkHttpClient with no read-timeout — sharing the REST
                   client's 15s timeout would kill an idle chat socket)
core/work/         OutboxSyncWorker (Hilt + WorkManager) +
                   OutboxSyncScheduler — the actual mechanism behind
                   "outbox survives process death / device restart"
                   (spec 14): WorkManager persists its own retry queue
                   and can run even when the app process isn't alive.
feature/chat/      domain/data/presentation, same split as feature/auth
```

**Local-first, single source of truth.** `ChatRepository`'s
`observeConversations()`/`observeMessages()` are backed entirely by Room.
Sending a message writes a `PENDING` row to Room immediately (before any
network call) and queues a durable Outbox entry — the UI never branches
on "am I online", it just renders whatever Room currently has, and
realtime/sync updates are applied by writing to Room, which the same Flow
re-emits.

**Hilt + WorkManager wiring.** `MenchApplication` implements
`Configuration.Provider` using `HiltWorkerFactory`; the default
`androidx.startup` WorkManager initializer is removed in
`AndroidManifest.xml` (`tools:node="remove"` on its `<meta-data>`). Both
halves are required together — `OutboxSyncWorker`'s `@AssistedInject`
constructor can't be satisfied by WorkManager's default factory.

## Phase 3a: image and file attachments

Adds the media foundation — images and generic files — that video, voice,
stickers, and GIF (later Phase 3 sub-steps) will build on.

### Backend additions

```
src/storage/          StorageProvider interface + LocalDiskStorageProvider
                       (dev-safe; forbidden in production by
                       env.validation.ts, same guard pattern as
                       OTP_PROVIDER=dev) — real S3-compatible provider is
                       the swap point, not yet implemented
src/modules/
  attachments/         upload (multipart) + authenticated content/
                       thumbnail streaming endpoints, mime/size
                       validation, sharp-based thumbnailing for images
```

**`MessageDto` embeds the full attachment summary, not just an id.**
`MessagesRepository` LEFT JOINs `attachments` in both `createIdempotent`
and `listForConversation` specifically to avoid an N+1 fetch — a client
rendering message history needs mime type/dimensions/filename immediately
per message, not as a follow-up round trip per image.

**Known Phase 3a limitation, not silently assumed away:** attachment
upload has no idempotency key server-side (unlike message send, which is
idempotent via `client_msg_id`). If Android's Outbox worker's two-call
sequence (upload, then send-with-attachmentId) is interrupted between the
two calls and retries, the retry uploads a second copy rather than
reusing the first. The send itself stays correctly deduplicated; only the
underlying attachment can end up orphaned server-side. Fixing this
properly needs an idempotency key on the upload endpoint — a reasonable
Phase 3b+ hardening item.

### Android additions

```
core/media/           AttachmentCache (copies picker content into
                       app-private storage immediately — picker Uri
                       grants don't reliably survive until the durable
                       Outbox actually gets around to uploading),
                       ContentUriMeta (mime type / filename resolution)
core/di/               ImageLoaderModule — Coil's ImageLoader built on the
                       @AuthenticatedClient OkHttpClient, since attachment
                       content/thumbnail URLs need the same bearer token
                       as every other API call
core/work/             OutboxSyncWorker gains a SEND_ATTACHMENT_MESSAGE
                       branch: upload, then send-with-attachmentId, then
                       delete the local cache copy — same durability
                       guarantees as text messages (WorkManager, survives
                       process death/reboot)
```

**Room migration v2→v3 is a full table rebuild, not a plain
`ALTER TABLE`.** Making `messages.body` nullable (an attachment-only
message has no caption) isn't expressible as a SQLite `ALTER COLUMN` —
SQLite has no such operation. `MIGRATION_2_3` uses the standard SQLite
pattern instead: create the new table shape, copy data across, drop the
old table, rename.

**Deferred to a later Phase 3 sub-step:** opening a received file in
another app (would need a `FileProvider` + `ACTION_VIEW` flow — the
current file bubble is informational only, name/size, no open action),
pinch-to-zoom in the image viewer, upload progress indicators, and
compression before upload.

## Phase 3 (voice messages)

Adds a third attachment kind — audio — on the same foundation as Phase
3a's images/files: same upload/storage/authorization pipeline, same
Outbox durability model, same embedded-summary DTO shape.

### Backend additions

Migration 004 widens `attachments.kind`'s CHECK constraint to include
`'audio'` and adds `duration_ms`. Postgres has no direct "alter a CHECK
constraint" operation — the migration does the standard
`DROP CONSTRAINT IF EXISTS` / `ADD CONSTRAINT` pair.

**Duration is reported by the client, not computed server-side.** The
recording device already knows exactly how long it recorded for; adding
server-side audio duration extraction (ffprobe or similar) would mean a
new native dependency for a value already available for free. This does
mean `duration_ms` is a client-asserted value, not independently
verified — see docs/SECURITY.md.

### Android additions

```
core/media/     VoiceRecorder (MediaRecorder → AAC/.m4a, discards
                anything under 500ms as too short to be meaningful),
                VoicePlaybackController (exactly one voice message plays
                at a time app-wide; streams a received message straight
                from the authenticated content URL via MediaPlayer's
                Uri+headers overload — the plain setDataSource(String)
                has no way to attach the bearer token)
```

A recorded voice message is routed through the same
`sendAttachment()`/`AttachmentCache`/Outbox path as a picked file —
`VoiceRecorder` already writes directly into app-private storage, so
routing it through `AttachmentCache.copyToCache()` again is one
redundant copy, accepted for not adding a second code path.

## Phase 3 (video messages)

Video is the fourth attachment kind, reusing `width_px`/`height_px`/
`duration_ms` (already added for images/audio) rather than needing new
columns. Migration 005 only widens the `kind` CHECK constraint.

**No server-side video processing at all — deliberately.** Duration,
dimensions, and the thumbnail frame are all extracted client-side
(`VideoMetadataExtractor`, using `MediaMetadataRetriever`) and uploaded
alongside the video file itself; the thumbnail is uploaded as a plain
JPEG that `sharp` resizes exactly like any other image thumbnail. Adding
ffmpeg (or similar) server-side to do this instead would be a real,
heavy native dependency for values the client already has for free —
same reasoning as audio's client-reported duration in Phase 3.

**Two known, honestly-scoped gaps, not silently missing pieces:**
- `VideoMetadataExtractor` reads raw encoded width/height, not adjusted
  for `METADATA_KEY_VIDEO_ROTATION` — a portrait video recorded on a
  landscape sensor can report swapped dimensions. Fine for a rough
  aspect-ratio hint, not for anything precision-dependent.
- In-app playback only works for the sender's own local copy
  (`VideoView.setVideoURI` on a local file path — no auth needed). A
  *received* video's `VideoPlayerDialog` falls back to a larger still
  thumbnail rather than attempting authenticated streaming: `VideoView`
  has no equivalent of `MediaPlayer`'s `Uri`+headers overload (which
  `VoicePlaybackController` already uses for audio), and wiring that
  through was left for a follow-up rather than rushed.

The composer has no separate "record/pick video" button — the existing
attach button's `GetContent("*/*")` picker already handles it: the
ViewModel detects a video mime type and routes through
`VideoMetadataExtractor` before calling the same `sendAttachment()` path
images and files already use.

## Explicitly out of scope for Phase 3 (and beyond)

GIF, stickers, custom emoji, message edit/delete/forward/reactions,
WebRTC voice/video, E2EE, push notifications, privacy/security center UI,
backup & device migration, the full MENCH visual identity + startup
animation, feature flags, remote config, group conversations. See the
product brief's phase breakdown for the intended order.

## Phase 6 — App lock, Account settings, Privacy & Security

**App lock is entirely local.** No backend endpoint exists or is needed:
`AppLockStore` (EncryptedSharedPreferences, Keystore-backed, same pattern
as `TokenStore`) holds only a PBKDF2 salt+hash of the PIN — never the PIN
itself — plus the biometric opt-in flag, the auto-lock timeout, and a
last-active timestamp. `AppLockCoordinator` observes `ProcessLifecycleOwner`
(not any single Activity) so the lock state survives configuration changes
and reflects the *process's* foreground/background transitions. It's
registered once in `MenchApplication.onCreate()`. `MainActivity` overlays
`LockScreen` on top of the nav graph whenever `AppLockCoordinator.isLocked`
is true, rather than the lock being a nav destination itself — it has to be
able to cover whatever screen the user was on, including mid-navigation.

`MainActivity` is a `FragmentActivity`, not a plain `ComponentActivity` —
the only reason is that `androidx.biometric.BiometricPrompt` requires one.

Setting, changing, and disabling the PIN all go through one screen
(`PinSetupScreen`) driven by `PinSetupMode` (CREATE/CHANGE/DISABLE) and a
`PinSetupStep` state machine. CHANGE and DISABLE both require the current
PIN before doing anything.

**Account settings** added the first profile-mutation endpoint:
`GET/PATCH /users/me`, resolved from the JWT's subject claim, never a
client-supplied id. `PATCH` accepts any subset of
displayName/username/bio; only the fields present are updated. A
username unique-constraint violation (Postgres 23505) surfaces as 409,
not a generic 500.

**Data & Storage** exposes `AttachmentCache`'s real on-disk size and a
clear-cache action — this only deletes the local media cache; it cannot
and does not touch server-side data.

**Sessions**: `POST /auth/logout-all` already existed server-side
(Phase 2) but had no Android caller until now — it's wired into Privacy &
Security's "Log out from all devices," which is labeled accurately: the
backend's session revocation doesn't exempt the calling device, so this
always ends in the local device being signed out too, not just other
devices.

## Explicitly out of scope for Phase 6 (and beyond)

GIF, stickers, custom emoji (excluded by product decision, not
schedule), WebRTC voice/video calls, presence/online-status, device list
UI (revocation of individual other devices — only all-at-once exists),
push notifications, backup & device migration, connection
health/diagnostics, feature flags, remote config, universal search.

## Phase 6 merge — calls, notifications, search/diagnostics, security/settings

Four Phase 6 branches (calls, push notifications, search+diagnostics,
app lock+settings) were developed independently on top of Phase 5 and
merged together here. Notable points from that merge:

**A real Phase 5 gap got fixed along the way.** Group actions — add
member, remove member, leave, rename — previously only updated the
database; they were never broadcast over the WebSocket at all. Other
members of a group had no way to find out about a change except by
fully re-syncing. `ConversationsService` now emits
`conversation.added` / `group.member_added` / `group.member_removed` /
`group.member_left` / `group.renamed`, and the Android client
(`ChatRepositoryImpl`, `GroupInfoViewModel`) handles all five.

**RealtimeModule ⇄ ConversationsModule is intentionally circular**,
resolved with `forwardRef()` on both sides: `RealtimeModule` needs
`ConversationsRepository` (membership checks on inbound typing/call
frames), and `ConversationsModule` needs `RealtimeModule`'s
`REALTIME_BROADCASTER` (to emit the group events above). `CallsModule`
and `NotificationsModule` both depend on `RealtimeModule` too, but
neither is part of that cycle — only the one direct pair needs the
forwardRef treatment.

**Migrations 008–010** were three independently-numbered `008_*.sql`
files (calls, notifications, search) that collided; renumbered to
008/009/010 in dependency-free order — none of the three touch
overlapping tables/columns.

**TokenStore now persists deviceId** (`StoredTokens.deviceId`),
introduced by the notifications work so the client can register its FCM
token against the correct device row. `TokenAuthenticator`'s
token-refresh path preserves it across a refresh rather than needing it
re-supplied (refresh responses never include it — it doesn't change).

**Everything push/call-signaling related degrades gracefully by
design**, not as an afterthought: no `google-services.json` → the
Firebase plugin simply isn't applied and the app still builds and
runs, just without push delivery (see `app/build.gradle.kts`'s
comment). No `FIREBASE_*` env vars → `NotificationsModule` falls back
to `DevNotificationProvider`, which logs instead of sending. No TURN
credentials → calls still connect between directly-reachable/simple-NAT
devices via the default public STUN server; only the harder NAT-traversal
case needs a real TURN deployment.

See each feature's own module/directory for details:
`backend/src/modules/{calls,notifications,search,diagnostics}`,
`android/.../feature/{calls,search,diagnostics,security,settings}`,
`android/.../core/{webrtc,notifications,security}`.
