# MENCH Security Model — Phase 1

## What Phase 1 actually provides

- **Transport**: TLS enforced by default (`network_security_config.xml`
  blocks cleartext except to the emulator's host alias `10.0.2.2`, for
  local dev only).
- **OTP**: 5-digit codes (configurable), generated with `crypto.randomInt`
  (CSPRNG, unbiased) — never `Math.random()`. Stored only as an argon2
  hash, never in plaintext, never logged. Bound to a `challengeId` +
  phone number pair, single-use, expires (`OTP_TTL_SECONDS`), rate-limited
  per-phone (resend cooldown) and per-endpoint (Nest Throttler: 3
  requests/min on `/auth/otp/request`), attempt-limited
  (`OTP_MAX_ATTEMPTS`) server-side.
- **Access tokens**: short-lived signed JWTs (default 15 min). Verified
  server-side on every protected route via `JwtAuthGuard` — the Android
  client's UI/navigation state is never treated as an authorization
  boundary.
- **Refresh tokens**: 384-bit random values. Only a keyed HMAC-SHA256
  hash is stored in Postgres (deterministic, so it's usable as a lookup
  key — see `token.service.ts` for why argon2 was deliberately *not* used
  here). Rotated on every use (old session revoked, new one issued and
  linked via `replaced_by_id`), so token replay after legitimate use is
  detectable in a future phase.
- **Android local storage**: tokens live only in
  `EncryptedSharedPreferences`, backed by a hardware-backed (where
  available) Android Keystore key. Never in plain `SharedPreferences`,
  never in plain DataStore, never logged.
- **Input validation**: every DTO validated server-side
  (`class-validator`, `whitelist: true`, `forbidNonWhitelisted: true`) —
  unknown fields are rejected, not silently ignored.
- **Error responses**: sanitized globally (`HttpExceptionFilter`) —
  internal error detail (stack traces, raw DB errors) is logged
  server-side only, never returned to the client.
- **Dev/prod OTP isolation**: `OTP_PROVIDER=dev` (logs the code instead of
  sending real SMS) is refused at startup if `NODE_ENV=production` — this
  is enforced by `env.validation.ts`, not just a comment or convention.

## Phase 2 additions

- **WebSocket auth is checked once, at connect time.** `ChatGateway`
  validates the access token when the connection is first established
  and never again for the life of that socket. A long-lived idle
  connection can technically outlive its access token's nominal 15-minute
  TTL. This does not bypass revocation: `logout-all-devices` still
  revokes the *refresh* token, so a revoked session can't obtain a new
  access token — it just doesn't retroactively close sockets opened
  before the revocation. Periodic re-validation (or closing sockets tied
  to a revoked session) is a reasonable Phase 5 hardening item, not
  silently assumed solved here.
- **Conversation membership is checked on every message/read-receipt
  request** (`ConversationsService.assertMember`), returning 404 rather
  than 403 for non-members so a non-member can't even confirm a
  conversation exists.
- **Phone-number lookup (`GET /users/lookup`) is a controlled
  enumeration surface**, same as OTP: it turns "does this phone number
  have an account" into a yes/no oracle. Rate-limited (10/min) for the
  same reason OTP requests are, but the underlying property — any
  authenticated user can probe phone numbers one at a time — is inherent
  to a "start a chat by phone number" design (shared by most messengers
  with this UX), not unique to this implementation.

## Phase 3a additions

- **Attachment access is authorized per-request, not just at upload
  time.** Both `GET /attachments/:id/content` and `.../thumbnail` re-check
  conversation membership on every fetch (`ConversationsService.assertMember`)
  — the same 404-not-403 policy as messages. A signed-in user who isn't a
  member of the conversation can't distinguish "attachment doesn't exist"
  from "exists but I can't see it."
- **Storage keys are always server-generated UUIDs**, never derived from
  the client-supplied filename — `LocalDiskStorageProvider` additionally
  guards every resolved path against escaping its base directory
  (defense in depth, tested against a real filesystem in
  `local-disk-storage.provider.spec.ts`, not just trusted to never be
  needed).
- **`STORAGE_PROVIDER=local` is forbidden in production** by
  `env.validation.ts`, same enforcement pattern as `OTP_PROVIDER=dev` —
  local disk storage doesn't survive a container restart/rescale and was
  never meant to run a real deployment.
- **Uploaded file size and mime type are validated server-side**
  (`AttachmentsService`), not trusted from client-reported values alone —
  a multipart request's declared `Content-Type` is just a label; nothing
  here treats it as ground truth beyond routing to the size/type check.

## Phase 3 (voice messages) additions

- **`duration_ms` is client-reported and not independently verified.**
  The recording device times its own recording and sends that value at
  upload time; the server enforces only a sanity cap (10 minutes) and
  that it's present/positive for audio uploads. A client could in
  principle report an inaccurate duration — the consequence is a
  cosmetically wrong duration label, not a security or data-integrity
  issue elsewhere in the system (the actual audio bytes stored are
  whatever was actually uploaded, regardless of the claimed duration).
- **Same authorization and storage-key guarantees as Phase 3a** apply
  unchanged to audio: per-request membership checks on content fetch,
  server-generated storage keys, size/mime validation.

## Phase 3 (video messages) additions

- **Same client-reported-and-trusted model as audio's duration** now also
  applies to video's `duration_ms`, `width_px`, and `height_px`, plus the
  thumbnail image itself — all extracted client-side and taken at face
  value server-side (beyond size/mime validation of the actual video and
  thumbnail bytes). The consequence of a misreported value is cosmetic
  (wrong duration label, wrong aspect ratio), not a security issue — the
  stored video bytes are always exactly what was uploaded.
- **The upload-then-send atomicity gap noted in Phase 3a applies here
  too**, now for two uploaded objects (video + thumbnail) instead of one
  — an interrupted-and-retried send can leave more orphaned objects, not
  a new category of issue.

## What Phase 1 explicitly does NOT provide

- **No end-to-end encryption.** Messages don't exist as a feature yet,
  but to be unambiguous: nothing in this codebase claims E2EE, and
  `devices.public_key` is an unused reserved column, not a working key
  exchange. E2EE is a Phase 5 deliverable, must use established
  primitives (e.g. Signal-protocol-derived design, X3DH/Double Ratchet or
  equivalent), and must be security-reviewed before any user-facing claim
  of "private"/"encrypted" is made about it.
- **No device attestation / root detection.**
- **No abuse-pattern detection beyond basic rate limiting** (e.g. no
  device-fingerprinting or velocity-based fraud scoring).
- **No refresh-token-reuse alerting.** Rotation makes reuse *detectable*
  (a revoked token being presented again), but Phase 1 doesn't yet act on
  that signal (e.g. revoke the whole session family). Add in Phase 5.
- ~~No app lock (PIN/biometric)~~ — added in Phase 6: `AppLockStore`
  (EncryptedSharedPreferences on the same Keystore-backed master key as
  `TokenStore`) holds a PBKDF2WithHmacSHA256 salt+hash of the PIN — the
  PIN itself is never written to disk, and matching happens by
  re-deriving the hash from the candidate PIN, never by comparing PINs
  directly (`PinHasher`).

## Secrets

Nothing in `.env.example` is a real secret — every value is a clearly
labeled placeholder. Real secrets belong in `.env` (gitignored) locally
and in GitHub Actions Secrets / your deployment platform's secret manager
in CI/production — never in source control.
