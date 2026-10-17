# Phase 3A — consolidated drop (v14.0, versionName 0.14.0-phase3a-final)

Everything below was written in one pass and **has never been compiled or run** (no Kotlin/Gradle, jest or Postgres in the build sandbox). The plan is: build, send me the errors, I fix them in one round.

## What is in the app now (all of 3A that fits without GPS, widgets or audio assets)
| Feature | Where |
|---|---|
| Hold-to-send heartbeat + live full-screen pulse + rhythmic vibration | «ما» dialog |
| 5 full-screen effects from words (FA + EN), manual picker (long-press send), **your own trigger words** | chat, Settings → «افکت‌ها و قلب‌تپش» |
| Time-capsule letters: wax seal, live countdown, opening animation, handwriting-style text | «ما» → Letters |
| Shared counters (date + tally), milestones | «ما» → Counters |
| "On this day" banner | «ما» → Memories |
| Question-of-the-day streak + best streak | «ما» → Question |
| **Shared canvas** (draw together, normalised strokes, atomic append, 400-stroke cap) | «ما» → 🎨 |
| **Year in Love** summary (days together, memories, letters, counters) | «ما» → ✨ |
| **Conversation cards** (deep / fun / little dares, 24 cards FA + EN) | «ما» → 🎴 |

## Backend changes since v12 (run both migrations before deploying)
- `021_phase3a_counters.sql` (table `couple_counters`), `022_phase3a_canvas.sql` (table `couple_canvas`)
- New modules: `counters`, `canvas`; `couple/streak.ts`; heartbeat + screen-effect routes in `conversations`
- New realtime events: `heartbeat`, `screen_effect`, `counter.changed`, `canvas.changed`
- New jest specs: counters (7), canvas (4 + 3 validation), streak (5), conversations (+4)

## Android changes
New files: `MessageEffects.kt`, `HeartbeatComponents.kt`, `LetterEnvelope.kt`, `CountersComponents.kt`, `TimelineHelpers.kt`, `SharedCanvasComponents.kt`, `YearAndCards.kt`. No Room migration, no new permission or dependency.
New unit tests: `LetterCountdownTest`, `CounterDaysTest`, `TimelineHelpersTest`, `YearAndCardsTest`, `MessageEffectTriggersTest`.

## Likeliest compile errors (so you know where to look first)
1. `MessageEffects.kt` / `LetterEnvelope.kt`: `withTransform`, `clipPath`, `Path.combine`, `Rect(center, radius)`.
2. `Motion.kt`: `combinedClickable` opt-in; `SharedCanvasComponents.kt`: `awaitEachGesture` / `drag` (needs a recent Compose foundation).
3. `CountersComponents.kt`: `FilterChip` (Material3 experimental in older versions).
4. `ChatScreen.kt`: many new parameters; named-argument mismatches with `MenchNavGraph.kt`.
5. Unit tests that touch `MessageEffect` need the generated `R` class (normal in Gradle unit tests).

## NOT built (honest list)
- **Sync hearts** (both online) and **letter attachments / Persian handwriting font** (needs a font file from you).
- **Distance + "I'm coming" live tracker**: needs location sharing; left out on purpose until you decide the privacy rules (opt-in, precision, who can see).
- **Counter widgets / home-screen widgets**, **together mode** (music/video sync), **couple games** beyond the cards, **extra rituals** (good-morning/night check-in), **mood duet**, **couple profile + private vault**, **romantic sound pack** (needs audio assets).
- **All of Phase 3B (graphics)** and **3C (polish)**: living backgrounds, glass, AGSL shaders, Lottie, custom icons, stories, cinematic viewer, seasonal themes, onboarding, Material You, theme previews, shared-element transitions, skeletons, widgets.
