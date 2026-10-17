# Phase 3A (part 5) — Daily streak on the question of the day (v13.4)

The existing "question of the day" now has a streak: how many days in a row **both** of you answered.

## Rules
- A day counts only if both people answered that day's question (Iran calendar day, same as the question itself).
- **current** = run of consecutive days ending today, or ending yesterday while today is still incomplete (the streak stays alive until the day is over). One fully missed day resets it to 0.
- **bestStreak** = the longest run ever.
- Nothing new is stored: it is derived from `daily_answers`. **No migration.**

## Backend
- `couple/streak.ts` (`computeStreak`, pure) + `streak.spec.ts` (5 cases: empty, run ending today, alive through yesterday, reset + best, month/year boundary + junk)
- `couple.repository.ts` — `listBothAnsweredDates` (days with 2+ distinct answerers, newest first, max 800)
- `couple.service.ts` — `GET /conversations/:id/question` now also returns `streak` and `bestStreak` (extra fields; old clients ignore them)

## Android
- `QuestionResponse` / `Question` gain `streak`, `bestStreak` (default 0, so an older server still works)
- `QuestionDialog` shows "🔥 N-day streak — you both answered" and, when higher, "your best: M days". Strings EN + FA. versionName 0.13.4-phase3a5.

## Risks
- Nothing compiled or run here: jest and Kotlin are unavailable. `tsc` is clean on the new files apart from the missing-jest-types noise.
- The streak refreshes when the dialog loads the question. The partner's answer already triggers a reload through `question.answered`.

## Phase 3 status
**3A done:** heartbeat, effects, time-capsule letters, shared counters, on-this-day, milestones, question streak.
**3A not built:** sync hearts, custom trigger phrases, letter attachments + Persian handwriting font, counter widgets, shared canvas/album, together mode, couple games, extra rituals (e.g. good-morning/good-night check-in), mood duet, distance + "I'm coming", Year in Love, couple profile/vault, romantic sounds.
**3B (graphics) and 3C (polish):** not started.
