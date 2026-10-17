# Phase 3 (rituals + graphics quality) — v17.0, versionName 0.17.0-phase3-rituals

Android only, no backend change, no new dependency. **Not compiled or run.**

## What is new
- **Graphics quality** (Settings → «افکت‌ها و قلب‌تپش»): Auto / Battery saver / Full.
  - Auto: half the particles on low-RAM phones (as before). Battery saver: ~35% of the particles and **animated wallpapers switched off**. Full: always everything.
  - Applies to the full-screen effects and the living wallpapers; updates live.
- **Daily rituals** («ما» dialog): one-tap «صبح بخیر ☀️» and «شب بخیر 🌙» send a ready-made message to the chat. The night one triggers the moon-and-stars effect through the normal word detection.
- **"I'm coming" 🚗** («ما» dialog): pick about 10 / 20 / 30 / 60 minutes and it is shown to your partner as your shared mood ("On my way — about 20 min"). **No location is read or sent**; only the number you pick. It reuses the existing mood feature, so there is no backend change and it expires like any mood.

## Files
New: `GraphicsQuality.kt`, `RitualComponents.kt`, `GraphicsQualityTest` (4 cases).
Edited: `MessageEffects.kt` (particle budget + quality chips), `LivingBackground.kt`, `UsComponents.kt`, `ChatScreen.kt`, strings EN + FA, `build.gradle.kts`.

## Likeliest compile errors
Unused imports (`ActivityManager`, `Context` in `LivingBackground.kt`) are only warnings; the `when` inside `FilterChip` label in `MessageEffects.kt`; the new `Row` import in `UsComponents.kt`.

## Note
The conversation-list skeleton loader already exists in the project, so no new skeleton was added.
