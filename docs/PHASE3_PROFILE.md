# Phase 3 — Couple profile (v20.0, versionName 0.20.0-phase3-profile)

A showpiece full-screen page that gathers what you two have built. Android only, **no backend change, no migration, no new dependency**. Not compiled or run.

## What it shows («ما» dialog → «💞 پروفایل ما», the primary button)
- Brand-gradient header with your partner's photo (or initial), a heart, their name, and the **big day count** in a glass card that tilts under your finger. On a milestone day it says so; otherwise "N days until the X-day mark". Without a together date it tells you where to set one.
- Tiles: days until you next meet (or "you meet today"), the 🔥 question streak, shared memories count, letters count, and up to three featured counters (tallies first, biggest first).
- Opening it refreshes memories, counters, letters and the question, so the numbers are current.

## Files
New: `feature/chat/presentation/CoupleProfile.kt` (`CoupleProfile`, `buildCoupleProfile`, `CoupleProfileDialog`), `CoupleProfileTest` (5 cases).
Edited: `UsComponents.kt` (button), `ChatScreen.kt` (state + dialog), strings EN + FA, `build.gradle.kts`.
Reuses: `tiltOnTouch`, `glass`, `isMilestone`/`nextMilestone`, `MenchBrand.heroBrush`, `UserAvatar`.

## Likeliest compile errors
`Modifier.align` on the close button inside the scrolling Column; `DialogProperties`/`Dialog` imports; smart casts on `profile.daysTogether` (same-module data class, should be fine).

## Not included
Your own photo next to your partner's (the app has no self-avatar lookup in this screen yet), sharing the profile as an image, editing the dates from this page (still in the «ما» screen).
