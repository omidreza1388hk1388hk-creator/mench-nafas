# Phase 3 — big combined drop (v23.0, versionName 0.23.0-phase3-games-year)

Written in one pass, in the priority order requested. **Nothing here has been compiled or run** (no Kotlin/Gradle, jest or Postgres in the sandbox); `tsc` on the backend shows no new errors compared with v12 apart from the usual missing-type-package noise.

Run migrations **021 – 025** before deploying the backend (counters, canvas, stories, vault, quizzes).

## 1. Couple games («ما» → 🎮)
- **36 questions to fall in love:** 36 cards (EN + FA), Back/Next, progress.
- **Would you rather:** 10 pairs, pick a side, next.
- **Love-language quiz:** 10 questions; the ten pairs cover every combination of the five languages once. Result with the top language (ties show all), a score per language, and the last 8 results kept on the phone as a history.
- **How well do you know me?** (backend + app): you write 1–8 questions (2–4 options, one marked right); your partner answers once; the server scores it and **never sends the right answers to the taker until they have answered**. Both sides see the score; sparkle on a perfect score. Max 10 quizzes per person. Migration `025_phase3a_quizzes.sql`, module `modules/quizzes`, event `quiz.changed`, 6 jest tests + helper tests, `QuizDraft`/`buildQuestion` unit tests.

## 2. Year in Love — now a story
Full-screen pages (tap the end side to go on): days together (rolling digits) → memories → **messages you sent / received** → **busiest hour and day** → **favourite emoji** → **most used words** → letters and counters → a final page with sparkle and a **Share** button (plain-text summary through the system share sheet). The chat statistics are computed on the phone from the local message database (new Room query, no schema change): `YearMessageStats.kt` + 4 unit tests.

## 3. Love timeline
Year filter chips, **memory of the week** (stable all week, changes on Monday), plus the existing "on this day" banner. Tests in `TimelineYearTest`.

## 4. Themes
New accents **Teal, Gold, Mono**; new presets **Ocean, Forest, Gold night, Mono**; **AMOLED true-black** switch in Appearance; two more bubble styles from before (Pill, Leaf).

## 5. Smaller items
- **Letters:** "Open when you're sad / miss me / can't sleep / happy" quick titles in the letter writer.
- **Mood duet:** when both of you have a mood, a shared gradient banner blends the two (💞).
- **Partner's local time** on the couple profile (pick the city once; difference in hours shown; stored on the phone only).
- **Rolling digits** on the profile day count and on tally counters; **gyro tilt** (accelerometer) on the profile card.
- **Vault:** every vault dialog window is now `FLAG_SECURE` (no screenshots / blank in recents).

## Likeliest compile errors
`YearAndCards.kt` (the page list `mutableListOf<@Composable () -> Unit>()`, `togetherWith`), `CoupleGames.kt` (`Card(onClick = …)` overload, `RadioButton`), `GraphicsModifiers.kt` (`DisposableEffect` sensor listener), `PartnerTime.kt` (`dismissButton = if … else null`), `MoodDuet.kt`, `Theme.kt` (`amoledScheme`), and the exhaustive `when` on `AccentColor` anywhere else in the code base.

## Still NOT built (be precise)
- Needs a file from you: handwriting font, sound pack (soft notification/ringtone, UI sounds), Lottie files.
- Needs your decision: live location tracking ("I'm coming" with route), music provider for Together mode.
- Large features not started: **Together mode**, **sync hearts**, **shared album**, **letter attachments**, **scheduled surprises** (needs a server scheduler), **stories with photos/music + chat-list rings + reactions**, **chat stickers/animated emoji pack**, real **backdrop blur**, **AGSL mesh/ripple/glow shaders**, **bubble skins/animations**, **collapsing profile header**, **frame-time monitor**, **per-chat accent**, **Glance widgets (last message, quick reply)**.
