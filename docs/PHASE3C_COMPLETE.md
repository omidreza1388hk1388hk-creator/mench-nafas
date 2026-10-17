# Phase 3C (polish) — completion drop (v22.0, versionName 0.22.0-phase3c-complete)

Android only, **no backend change, no migration, no new dependency, no asset files**. Not compiled or run.

## Phase 3C checklist
| Item | Status | Where |
|---|---|---|
| Curated themes with live preview, Material You opt-in, smooth theme cross-fade | done (v16) | `ThemePresets.kt`, `Theme.kt` |
| Skeleton loaders | already in the project (conversation list) | — |
| Home-screen widget | already in the project (`UsWidgetProvider`, days together + next meeting) | — |
| Screen slide transitions | already in the project (`MenchNavGraph`) | — |
| **Shared-element transition** | **new** | the person's photo glides from the conversation-list row into the chat header and back. `SharedElements.kt` (`LocalSharedTransitionScope`, `LocalAnimatedVisibilityScope`, `Modifier.sharedAvatar`), `UserAvatar(sharedKey = …)`, `SharedTransitionLayout` + per-destination scope in `MenchNavGraph.kt` |
| **Predictive back** | **new** | `android:enableOnBackInvokedCallback="true"` in the manifest; with navigation-compose 2.8 the existing slide transitions now follow the back gesture on Android 14+ |
| **Haptics map** | **new** | Settings → «افکت‌ها، لرزش و کیفیت»: **Touch feedback** (every Compose haptic in the app, gated centrally through `GatedHapticFeedback` provided in `MenchTheme`, so no call site changed), **Hug/kiss/miss-you vibrations** (`vibrateAffection`/`vibrateHeartbeat`), plus the existing heartbeat vibration switch. `HapticSettings.kt` |
| **More bubble styles** | **new** | Appearance now offers Rounded, Squared, **Pill** (capsule-like) and **Leaf** (one sharper corner, mirrors in RTL) |

## Not built (and why)
- **Sounds map:** the app has no sound-effect system and no audio files. A sounds map needs a sound pack from you (send, receive, heartbeat, effects).
- **Circular-reveal theme switch:** replaced by the colour cross-fade from v16; a true circular reveal needs a screenshot-overlay pipeline and is not worth the risk.
- **More shared elements** (images in the media viewer, story ring on the list): the plumbing is in place (`sharedAvatar` pattern), only the avatar is wired.

## Likeliest compile errors
`MenchNavGraph.kt`: the two `CompositionLocalProvider` blocks were inserted into the `composable(...)` lambdas by brace matching, and `SharedTransitionLayout` wraps the whole `Box`; `this@composable` must resolve to the `AnimatedContentScope`; the function now carries `@ExperimentalSharedTransitionApi` (callers in `MainActivity` may need `@OptIn`). `Theme.kt`: `LocalHapticFeedback provides …` and the `AtomicBoolean` bridge. `AppearanceSettingsScreen.kt`: the extra `Column` I wrapped around the bubble-style cards.

## New/edited files
New: `core/ui/theme/HapticSettings.kt`, `core/ui/components/SharedElements.kt`, `BubbleStyleAndHapticsTest` (2 tests).
Edited: `MenchNavGraph.kt`, `UserAvatar.kt`, `ConversationListScreen.kt`, `ChatScreen.kt`, `Theme.kt`, `Shape.kt`, `AppearanceModels.kt`, `AppearanceSettingsScreen.kt`, `MessageEffects.kt`, `UsComponents.kt`, `AndroidManifest.xml`, strings EN + FA.
