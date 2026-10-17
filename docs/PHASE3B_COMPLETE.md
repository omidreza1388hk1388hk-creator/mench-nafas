# Phase 3B (graphics) — completion drop (v21.0, versionName 0.21.0-phase3b-complete)

Android only, **no backend change, no migration, no new dependency, no asset files**. Not compiled or run.

## Phase 3B checklist
| Item | Status | Where |
|---|---|---|
| Living backgrounds (aurora with AGSL on Android 13+, bubbles, starry sky) | done (v15) | `LivingBackground.kt`, wallpaper picker |
| Seasonal ambience (Valentine, Nowruz, Yalda, New Year) | done (v15) | `SeasonalEffects.kt` |
| Depth (tilt) and glass surfaces | done (v15) | `GraphicsModifiers.kt` |
| Graphics performance guardrail (Auto / Battery saver / Full) | done (v17) | `GraphicsQuality.kt` |
| Stories | done (v18) | `StoriesComponents.kt` + backend |
| Profile showpiece | done (v20) | `CoupleProfile.kt` |
| **Custom icon set** | **new** | `core/ui/components/MenchIcons.kt` (Heart, Counter, Canvas, Story, Letter, Vault, Couple) + `FeatureButton`, used for the Counters / Canvas / Stories / Vault buttons in the «ما» dialog |
| **Animation catalog** (replaces Lottie) | **new** | `MenchAnimations.kt`: `DrawnCheck`, `PulsingHeart`, `SparkleBurst`, `EmptyHeartState`. In use: empty states of Counters and Vault, the "heartbeat delivered" check, the milestone-day burst on the profile |
| **Cinematic image viewer** | **new** | `CinematicViewer.kt` replaces the old viewer: fade/scale-in, spring double-tap zoom, pinch to 6×, clamped pan, **drag up/down to dismiss** with backdrop fade, tap to close |
| **Onboarding "wow"** | **new** | the intro slides now have soft hearts floating up behind them (`LivingKind.HEARTS`) |
| **Typography polish** | **new** | paragraph-quality line breaking for every text style (`LineBreak.Paragraph`) on top of the existing Persian line-height tuning |
| **Micro-interactions** | **new** | `Modifier.pressScale()` (springy press response that never consumes touches), used on `FeatureButton` |

## Honest notes
- **No Lottie.** A Lottie catalog needs the Lottie library plus animation files from a designer. Instead the animations are drawn in code, so they follow the theme colours and add no dependency. If you later want Lottie, you supply the `.json` files and I add the dependency.
- Typography was already well tuned (Vazirmatn support, all 15 styles, Persian line-height ratio); this drop only adds the line-break mode.
- The viewer's gestures (custom single/multi-finger handling) are the part most likely to need adjustment on a real device, e.g. whether vertical drag fights with the dialog's own back gesture.

## New/edited files
New: `MenchIcons.kt`, `MenchAnimations.kt`, `CinematicViewer.kt`, `GraphicsHelpersTest` (4 tests).
Edited: `GraphicsModifiers.kt` (+`pressScale`), `LivingBackground.kt` (+HEARTS), `ChatScreen.kt` (viewer), `UsComponents.kt`, `CountersComponents.kt`, `VaultComponents.kt`, `HeartbeatComponents.kt`, `CoupleProfile.kt`, `OnboardingScreen.kt`, `Type.kt`.

## Likeliest compile errors
`PathBuilder.arcTo(...)` argument order and `ImageVector.Builder(...).path(pathBuilder = …)` in `MenchIcons.kt`; `calculateZoom` / `calculatePan` / `positionChanged` / `consume()` in `CinematicViewer.kt` (versions of Compose foundation differ); `LineBreak.Paragraph` needs Compose UI 1.4+; `animate(...)` import in the viewer.
