# MENCH — Phase 3 (complete drop, v3.0.0)

One project: the Phase 1 + 2 base you sent, with everything built for Phase 3 merged on top (backend NestJS + Android Kotlin/Compose).
**Status: written, not compiled.** The build sandbox had no Kotlin/Gradle, jest or Postgres. The only automated checks that ran were `tsc` on the backend (no new errors compared with the Phase 2 baseline apart from missing-package noise) and static scripts (string resources present in EN + FA, no duplicate names, brackets balanced, an import audit). Expect a round of compile errors on the first Gradle build — send them over and they get fixed together.

## 1 · Deploy steps
1. Apply migrations **021 → 028** in order (`backend/src/database/migrations`):
   021 counters · 022 canvas · 023 stories · 024 vault · 025 quizzes · 026 counter themes · 027 letter-unlock reminder · 028 album + photos on letters/stories.
2. Backend: `npm i`, run the jest specs (21 spec files), start. The letter-reminder job starts by itself (every minute, not under `NODE_ENV=test`).
3. Android: Gradle sync, fix compile errors, run the unit tests (26 test files). No new Gradle dependency, no new asset, no new permission.

## 2 · What Phase 3 contains

### 3A — the romantic layer
| Feature | Notes |
|---|---|
| Heartbeat (hold the heart) + **sync hearts** | live pulse, rhythmic vibration, "delivered/offline" feedback, celebration when you hold together |
| Full-screen effects from words + hand-picked + **custom trigger words** | 5 effects, FA/EN phrases |
| **Time-capsule letters** | wax seal, countdown, opening animation, "open when…" titles, **photo attachment**, **server push when it unlocks** |
| **Shared counters** | date/tally, 6 card themes, milestones with sparkle, Jalali date, live sync |
| Love timeline | year filter, memory of the week, "on this day" |
| **Shared canvas** | draw together, glow trails |
| **Shared album** | photos with caption, one reaction each, live |
| **Couple games** | 36 questions, would-you-rather, love-language quiz (history), "how well do you know me" quizzes (server-scored) |
| **Year in Love** | story pages with real chat statistics, share |
| Rituals | good-morning / good-night, "I'm coming" (minutes only, no location), question-of-the-day **streak** |
| Mood duet, partner's local time, birthdays | on the couple profile / «ما» |
| **Private vault** | biometric gate + end-to-end encrypted notes (AES-256-GCM, PBKDF2), `FLAG_SECURE`, blur behind it |
| **Couple profile** | parallax header, rolling day count, milestones, tiles |

### 3B — graphics
Living wallpapers (aurora and **mesh gradient** with AGSL, bubbles, starry sky) · seasonal + **Islamic-calendar** + birthday effects · tilt / **gyro tilt** / glass · custom icon set · animation catalog (drawn check, pulsing heart, sparkle burst, rolling digits, empty states) · **cinematic image viewer** · **stories** (text/photo, rings on the chat list, reactions, seen counts) · graphics quality (Auto / Battery saver / Full) with a **frame-time monitor** · onboarding hearts · pop-in for new messages · paragraph-quality line breaking.

### 3C — polish
Ready-made themes with live preview (11) · Material You opt-in · **AMOLED black** · new accents (Teal, Gold, Mono) · 4 bubble shapes (Rounded, Squared, Pill, Leaf) · smooth theme cross-fade · **shared-element avatar transition** · predictive back flag · **haptics map** (touch / affection / heartbeat switches).

## 3 · Not in this drop (and why)
- **Needs a file from you:** Persian handwriting font, sound pack (notification/ringtone/UI sounds), Lottie animations, sticker/mascot art.
- **Needs your decision:** live location + route for "I'm coming" (privacy rules); music/video provider for **Together mode**.
- **Possible but not built yet:** per-chat accent colour, Glance widgets (last message / quick reply), full bubble skins (neon / glass / paper), ripple shader for reactions, scheduled free-text messages (letters with an unlock date + push already cover surprises), photo/music stories with audiences.

## 4 · Documents inside `docs/`
`PHASE3A_PART1…5`, `PHASE3A_FINAL`, `PHASE3B_STORIES`, `PHASE3B_COMPLETE`, `PHASE3C_PART1`, `PHASE3C_COMPLETE`, `PHASE3_VAULT`, `PHASE3_PROFILE`, `PHASE3_GAMES_YEAR_AND_MORE`, `PHASE3_FINAL_BATCH` (with two addenda), `PHASE3_PART_RITUALS_QUALITY` — each lists its files, tests and the likeliest compile errors.

## 5 · Known thing to check first
`android/.../CoupleComponents.kt` has one more `(` than `)` by plain character count; the same is true of the Phase 2 file, so it is probably inside a string or comment — but confirm when you build.
