# Phase 4 (part 3) — Privacy settings, disappearing messages, two-step verification

Builds on parts 1 and 2. E2EE is still intentionally not started (spec: after the first successful build).

## Manual steps (do these first)
1. `npm run migrate` in `backend/` — applies `021_phase13_privacy_disappearing.sql` and `022_phase13_two_step.sql`.
2. Optional env: `DISAPPEARING_SWEEPER=off` disables the 30 s expiry sweep (it is also off under `NODE_ENV=test`).
3. Deploy the backend BEFORE the new app (the app calls `/privacy`, `/two-step`, `/conversations/:id/disappearing`).

## What was built
**Privacy settings** (Settings → Privacy & security → Privacy)
- Last seen & online, profile photo, who can find me, who can call me: Everybody / Nobody. Read receipts on/off.
- Enforcement is server-side: presence is neither broadcast nor snapshotted for a "Nobody" user; avatar and by-username/invite lookups answer 404 (indistinguishable from "no such user"); calls answer 403; with receipts off the read cursor is still stored (unread counts) but `message.read` is not sent.
- `PrivacyService` is `@Global` and injected as `@Optional()` into Users/Calls/Messages, so existing constructors and specs are unchanged.

**Disappearing messages** (timer icon in the chat top bar; Off / 1 h / 24 h / 7 d / 30 d)
- Timer lives on the conversation; a DB trigger stamps `messages.expires_at` at insert, so every message kind is covered and changing the timer never rewrites old messages.
- `ExpirySweeper` (every 30 s, `FOR UPDATE SKIP LOCKED`, safe with several instances) marks due messages deleted and broadcasts `message.deleted`. Direct chat: either person may change the timer; group: owner only. Change is pushed live (`conversation.disappearing_changed`) and a banner shows under the top bar.

**Two-step verification** (Privacy → Two-step verification)
- Optional password (argon2) asked after the SMS code. Login returns `200 {twoStepRequired, ticket, hint}`; `POST /auth/two-step/verify` finishes it. The ticket lives 5 min, so a typo never costs a new SMS. 5 wrong passwords lock the account for 15 min (429); a ticket allows 5 tries. Changing or removing the password needs the current one.

## Tests added
Backend: `privacy.service.spec`, `expiry.sweeper.spec`, `two-step.policy.spec`, `two-step.service.spec`.
Android: `PrivacyRulesTest`, two new `AuthViewModelTest` cases (two-step success / wrong password), duplicate-key guard in `StringsParityTest`.

## Known limits (honest list)
- Never compiled or run: no Gradle here, and the backend has no `node_modules` in the sandbox (only a syntax-level check of the new TS files was possible).
- An expired disappearing message appears as the existing "This message was deleted" placeholder on every device; it is not removed from the list. Attachments of expired messages are not purged from storage yet.
- "Nobody" is not reciprocal (hiding your last seen does not hide others' from you). "Who can find me" covers username and invite lookups only, not phone-number contact sync.
- Two-step has no recovery path (no e-mail reset) by design for now: a forgotten password locks the user out; the UI warns about it.
- The login error texts in `AuthViewModel` are English literals like the existing ones (not localized).
- A pre-4C app version that meets an account with two-step enabled cannot sign in (it cannot show the password step); update the app first.

## Still missing in Phase 4
4A: drag-and-drop attachments, plurals, 200 % font-scale audit.
4B: remote crash reporting + breadcrumbs, ANR detection, Compose/screenshot/DAO tests, detekt/ktlint, Redis pub/sub fan-out, BullMQ jobs, Prometheus metrics.
4C: end-to-end encryption (X3DH + Double Ratchet, safety numbers, encrypted DB/attachments), view-once media, per-chat lock, encrypted backup, certificate pinning option.
