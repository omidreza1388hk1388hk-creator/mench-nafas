# Phase 3A (part 4) — "On this day" and counter milestones (v13.3)

Android only. No backend change, no migration.

- **On this day:** the memories dialog shows a banner at the top when a memory has today's month and day in an earlier year ("2 year(s) ago: …"). A Feb 29 memory shows on Feb 28 in non-leap years.
- **Counter milestones:** a date counter that is past its date shows either "🎉 N days — a milestone!" (50, 100, 200, 300, 365, 500, 730, 1000, then every 365) or "N days until the X-day mark".
- Pure helpers in `TimelineHelpers.kt`; 4 unit tests in `TimelineHelpersTest`. Strings EN + FA. versionName 0.13.3-phase3a4.
- Not compiled here. Likeliest issue: `item { }` placed inside the existing `LazyColumn` of `MemoriesDialog`.

## Phase 3 status
**3A done:** heartbeat, word/manual effects, time-capsule letters, shared counters, on-this-day, milestones.
**3A not built:** sync-hearts mode, custom trigger phrases, letter attachments + Persian handwriting font, counter widgets, shared canvas/album, together mode, couple games, rituals/streaks, distance + "I'm coming", Year in Love, couple profile/vault, romantic sounds.
**3B (graphics) and 3C (polish):** not started.
