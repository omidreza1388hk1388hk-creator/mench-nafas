# MENCH — منچ

A private messenger, built in phases. This repository currently contains
**Phase 1: Foundation** — phone+OTP authentication, session management,
and the architectural skeleton that later phases (real-time messaging,
media, calls, privacy center) build on without a rewrite.

The "Manch/Ludo" concept referenced in the product brief is a brand/visual
identity inspiration only — this is not a game, and no game logic exists
or is planned in this codebase.

## What's actually implemented (Phase 1 + Phase 2 + Phase 3: images, files, voice, video + Phase 4: message actions + Phase 5: group conversations)

- Phone number + OTP authentication (request → verify → session)
- Refresh token rotation, logout, logout-all-devices
- Direct (1:1) conversations, started by phone number lookup
- Group conversations: create with a name and initial members, add/remove
  members, rename (any member can add members or rename; only the owner
  can remove someone who isn't themselves — anyone can always leave),
  member list with owner/member roles, sender-name labels on received
  messages so a multi-person conversation is legible
- Real-time text messaging over a raw WebSocket (`/ws`), with idempotent
  send (client-generated dedupe key), paginated history, and read
  receipts
- Image and file attachments: pick from device, upload with real
  server-side validation (size/mime), automatic thumbnail generation for
  images, tap to view full-size
- Voice messages: record in-app, upload with reported duration, playback
  (one at a time, app-wide) for both your own recordings and received
  ones
- Video messages: pick from device, client-extracted thumbnail/duration/
  dimensions (no server-side video processing), in-app playback for your
  own sent videos
- Edit and delete your own text messages (deleted messages stay in
  history as a "This message was deleted" placeholder, not a gap);
  reactions (one active emoji per user per message, tap to toggle); and
  forwarding text messages to another conversation — all long-press
  actions on a message bubble, all durable through the same offline
  Outbox as sending
- Local-first, offline-capable Android chat UI: messages (including
  attachments) render optimistically from Room the instant you hit send,
  before any network round trip
- A durable Outbox (WorkManager-backed) that retries failed sends —
  including attachment uploads, edits, deletes, reactions, and forwards —
  across app restarts, process death, and device reboots, not just an
  in-memory retry loop
- Real PostgreSQL schema + migrations (see `backend/src/database/migrations`)
- Android: Compose UI for auth + conversation list + chat (text/images/
  files/voice) + group info, Room-cached state, Keystore-backed encrypted
  token storage, Retrofit + auto-refresh-on-401
- CI that actually builds and tests both sides on every push

## What's explicitly NOT implemented yet (see docs/ARCHITECTURE.md)

Group conversations have no realtime push for membership/title changes
yet — deliberately deferred rather than introducing a circular module
dependency between ConversationsModule and RealtimeModule blind (no
compiler available while building this phase to verify a `forwardRef`
fix works); the client refreshes the conversation list and Group Info
screen on demand instead of getting a live update. Forwarding media
messages (text-only for now — see
`MessagesService.forward`'s doc comment for why), GIF, stickers, custom
emoji, WebRTC calls, E2EE, push notifications, privacy center,
backup/device migration, the full MENCH visual identity and startup
animation. These have architectural extension points reserved for them
but no implementation — nothing here pretends otherwise.

## Known gaps (read before your first build)

Two files could not be authored correctly in the sandbox this project
was built in (no internet access, no local Gradle/npm registry access):

- `android/gradle/wrapper/gradle-wrapper.jar` — a compiled binary.
  `android/gradlew` and `gradlew.bat` are present and real; the CI
  workflow regenerates a correct, checksum-verified jar automatically.
  See `android/README-WRAPPER.md` for the one-time local-dev fix.
- `backend/package-lock.json` — requires real npm registry resolution.
  CI runs `npm install` (not `npm ci`) until a real one is generated and
  committed. See `backend/README-LOCKFILE.md`.

Both are self-healing in CI on first push; neither blocks local
development once you run the one-time step documented in each file.

## Repository layout

```
/backend    NestJS + PostgreSQL + Redis API (Phase 1: auth/users/devices/sessions)
/android    Kotlin + Jetpack Compose app (Phase 1: auth flow + foundation)
/docs       Architecture, security, environment documentation
/.github    CI workflows
```

## Running the backend

```bash
cd backend
cp .env.example .env      # edit DATABASE_URL / REDIS_URL / secrets
npm install
npm run migrate
npm run start:dev
```

## Running the Android app

```bash
cd android
# Point at your backend. The emulator reaches the host machine at 10.0.2.2.
./gradlew assembleDebug -PMENCH_API_BASE_URL="http://10.0.2.2:3000/api/v1/"
```

Install the resulting APK from `app/build/outputs/apk/debug/`, or open the
`android/` folder directly in Android Studio and run it.

## Testing

```bash
# Backend
cd backend && npm test && npm run test:e2e   # e2e needs Postgres + Redis running

# Android
cd android && ./gradlew testDebugUnitTest
```

## Important: build validation

This project was authored in a sandboxed environment with no internet
access and no Android SDK, so `npm install`, `npm run build`,
`./gradlew assembleDebug`, and the test suites have **not** been executed
against real dependencies — only reviewed by hand. The GitHub Actions
workflows in `.github/workflows/` will run the real build and real tests
on first push. Treat the first CI run as the actual validation step, and
expect to fix real compiler/dependency-version errors it turns up — that
is normal for a first run, not a sign anything was faked.

See `docs/ARCHITECTURE.md`, `docs/SECURITY.md`, and `docs/ENVIRONMENT.md`
for more detail.
