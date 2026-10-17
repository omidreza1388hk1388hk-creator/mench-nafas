# Phase 3C (part 1) — Themes and Material You (v16.0, versionName 0.16.0-phase3c1)

Android only, no backend change, no new dependency. **Not compiled or run.**

## What is new (Settings → Appearance)
- **Ready-made themes:** 7 presets (Rose evening, Midnight, Mint morning, Lavender dream, Ocean aurora, Soft bubbles, Classic). One tap sets light/dark, accent colour and bubble shape (through the existing DataStore) **and** the chat wallpaper (through the chat's own prefs). Each card is a **live preview**: it renders its own colours, wallpaper tint and two sample bubbles by wrapping itself in a MaterialTheme built from the preset; the active preset gets a highlighted border.
- **Material You opt-in:** a switch (shown only on Android 12+) that takes the colours from the phone's wallpaper. Off by default; picking a preset turns it off again so the preset colours show.
- **Smooth theme change:** every colour of the scheme now cross-fades (about 350 ms) when the theme, accent, preset or Material You changes, instead of snapping. Immediate under "remove animations". (A full circular-reveal transition was not built; this is the lighter version.)

## Files
New: `feature/appearance/presentation/ThemePresets.kt`, `ThemePresetTest`.
Edited: `AppearanceModels.kt` (`useDynamicColor`), `AppearanceStore.kt` (+ DataStore key), `AppearanceRepository(Impl).kt`, `AppearanceSettingsViewModel.kt`, `AppearanceSettingsScreen.kt`, `core/ui/theme/Theme.kt` (`previewColorScheme`, `animatedColorScheme`, dynamic scheme), `MainActivity.kt`, `MenchNavGraph.kt`, strings EN + FA.
Existing users keep their saved settings; the new key defaults to off.

## Likeliest compile errors
`animatedColorScheme` (local `@Composable fun a(...)` inside a composable and `ColorScheme.copy` with the surfaceContainer fields); `dynamicDarkColorScheme` import; `AppearanceRepository` fakes elsewhere that must implement the new `setUseDynamicColor`.

## Phase 3C still to build
Shared-element and predictive-back transitions, skeleton loaders everywhere, a haptics/sounds map in settings, extra bubble-style options (tail/no-tail, density), home-screen widgets, and the circular-reveal theme switch.
## Phase 3B still to build
Lottie catalog (needs the dependency and animation files), custom icon set, stories, cinematic media viewer, profile showpiece, onboarding wow, typography polish, micro-interaction pass, graphics quality setting.
