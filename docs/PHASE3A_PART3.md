# Phase 3A (part 3) — Shared counters (v13.2)

Several named counters per couple, shared live between both phones (direct chats only, max 20).

## Two kinds
- **date** — days since / until a chosen date. Past date: "N days so far"; future: "N days to go"; today: "Today! 🎉".
- **tally** — a number both people can bump with + / − (never below 0), e.g. "times we hugged". The bump is one atomic SQL update, so two simultaneous taps are never lost.

Anyone can create and bump; only the author can delete. The partner's phone refreshes through the new `counter.changed` realtime event (no push).

## Backend (new module `modules/counters`)
- **Migration `021_phase3a_counters.sql`** — table `couple_counters`. **Run it before deploying.**
- `GET/POST /conversations/:id/counters`, `POST …/counters/:counterId/bump {delta: 1|-1}`, `DELETE …/counters/:counterId`
- Registered in `conversations.module.ts`; event added to `realtime-events.ts`
- `counters.service.spec.ts` — 7 tests (create, validation, cap, bump rules, delete rules, group/non-member)

## Android
- New: `feature/chat/presentation/CountersComponents.kt` (`CountersDialog`, `dateCounterDays`)
- Wired through `ChatApi`, `RealtimeEvents/Parser`, `ChatModels` (`Counter`), `ChatRepository` (+defaults) / `Impl`, `ChatViewModel`, `ChatScreen`, `MenchNavGraph`, and a new «شمارنده‌ها» button in the «ما» dialog
- Strings EN + FA; `CounterDaysTest` (4 cases); versionName 0.13.2-phase3a3. No Room migration.

## Risks
1. Neither jest nor Kotlin was run here. `tsc` showed only the missing-jest-types noise on the spec; the Android code is uncompiled. Likeliest: `FilterChip` (opt-in), `items(counters, key=…)`.
2. Check on two phones: add a tally on A, tap + on B, A's number updates without reopening.

## Still missing in 3A
Home-screen widgets for counters, auto-created "days together" counter from the existing us dates, counter milestones (100/365 days effects), love timeline + "on this day", shared canvas/album, together mode, couple games, rituals/streaks, distance + "I'm coming", Year in Love, couple profile/vault.
