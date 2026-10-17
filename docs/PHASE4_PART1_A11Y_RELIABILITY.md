# Phase 4 (part 1) — Accessibility, localization guard, reliability basics

Master-prompt scope: 4A (partial), 4B (partial), 4C (screen security only). **E2EE is intentionally not started**:
the spec says it goes last, after the first successful build.

## Done in this drop
**4A Accessibility / i18n**
- TalkBack: every message bubble is one focus stop announced as "sender, content, time, status";
  reply / heart-react / delete (own) exposed as custom accessibility actions.
- WCAG AA: `MenchA11y.readableOn` picks `onPrimary` by real contrast (white if >= 4.5:1, else dark ink); used by `Theme.kt` for every accent in both modes. `Modifier.minTouchTarget()` helper (48 dp).
- Digits setting (Auto / Persian / Latin) in Appearance; `MenchTheme(digitStyle=…)`.
- Unit tests: contrast maths, and a **fa/en strings parity + placeholder check** (fails the build if a key is missing in either language).

**4B Reliability**
- Backend: `GET /health/ready` (Postgres + Redis, 503 when either is down; `/health` stays dependency-free liveness).
- Backend: JSON access log with `X-Request-Id`; **query strings/bodies/headers are never logged** (OTP codes safe).
- GitHub Actions CI (`.github/workflows/ci.yml`): backend lint/format/test/build, Android unit tests + assembleDebug, signed release on `v*` tags.

**4C Security**
- Screen security switch (Appearance): `FLAG_SECURE` blocks screenshots/recording and blanks the recents preview. Baseline security headers on the API.

## Files
Android: `core/ui/a11y/MenchA11y.kt`, `core/ui/theme/Theme.kt`, `feature/appearance/**` (model, store, repo, VM, screen), `feature/chat/presentation/{MessageSemantics,ChatScreen}.kt`, `MainActivity.kt`, `MenchNavGraph.kt`, strings fa/en, tests under `src/test/.../a11y` and `.../localization`.
Backend: `src/common/health/readiness(.spec).ts`, `src/common/logging/request-logger(.spec).ts`, `src/main.ts`. CI: `.github/workflows/ci.yml`.

## Manual steps
1. No migration, no new Gradle dependency, no new env var.
2. For release-by-CI add repository secrets: MENCH_KEYSTORE_B64, MENCH_KEYSTORE_PASSWORD, MENCH_KEY_ALIAS, MENCH_KEY_PASSWORD.

## Risks / how to verify
- Never compiled (no Gradle in the sandbox). Most likely compile spots: `ChatScreen.kt` semantics block (imports added), `AppearanceSettingsScreen.kt` new rows.
- FLAG_SECURE is applied after DataStore loads, so the very first frame after a cold start can be capturable.
- Merged bubble semantics: reaction chips and image taps inside a bubble are reached through the custom actions / long-press menu, not as separate TalkBack stops.
- Backend tests could not be run here (no node_modules); the two helpers were transpiled and exercised with a plain Node script.

## Still missing in Phase 4
4A: tablet two-pane + NavigationRail, Arabic, plurals, Jalali/Gregorian switch, live-region announcements, 200 % font audit.
4B: remote crash reporting + breadcrumbs, ANR detection, Compose/screenshot/DAO tests, detekt/ktlint, Redis pub/sub fan-out, BullMQ, Prometheus metrics.
4C: E2EE (X3DH + Double Ratchet, safety numbers, encrypted DB/attachments), disappearing/view-once messages, per-chat lock, encrypted backup, two-step verification, privacy settings (last seen / photo / receipts), cert pinning option.
