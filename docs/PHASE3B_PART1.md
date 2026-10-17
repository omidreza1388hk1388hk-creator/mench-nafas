# Phase 3B (part 1) — Graphics foundations (v15.0, versionName 0.15.0-phase3b1)

Android only, no backend change, no new dependency or asset. **Not compiled or run** (same caveat as the 3A drop).

## What is new
- **Living chat backgrounds** (Settings → Chat wallpaper): *Aurora*, *Bubbles*, *Starry sky*. Drawn behind the bubbles, never touch-blocking.
  - Aurora uses an **AGSL shader** on Android 13+ and drifting soft colour blobs below that.
  - One slow 60 s clock; positions are pure functions of time. Static under "remove animations"; fewer particles on low-RAM devices. Colours follow the app theme.
- **Seasonal ambience:** on Valentine's Day (hearts), Nowruz 20–22 Mar (confetti), Yalda 20–21 Dec (moon and stars) and 1 Jan (confetti) the chat plays one gentle effect the first time it is opened that day. Follows the existing "full-screen effects" switch.
- **Depth:** `Modifier.tiltOnTouch()` — a surface leans towards your finger and springs back. Used on the conversation cards and the Year in Love cards.
- **Glass:** `Modifier.glass()` — frosted translucent gradient with a bright edge (real backdrop blur is not available to a plain Compose surface). Used on the Year in Love cards.

## Files
New: `LivingBackground.kt`, `GraphicsModifiers.kt`, `SeasonalEffects.kt`; tests `SeasonalEffectsTest`.
Edited: `ChatWallpaper.kt` (3 new entries + `living`), `ChatScreen.kt` (livingLayer + seasonal hook), `MessageEffects.kt` (`claimSeasonalPlay`), `YearAndCards.kt`, strings EN + FA.

## Likeliest compile errors
`RuntimeShader` / `ShaderBrush(android.graphics.Shader)` usage and `@RequiresApi` in `LivingBackground.kt`; `composed` + `drawBehind` reading a delegated `State`; `awaitFirstDown(requireUnconsumed=false)` in `GraphicsModifiers.kt`.

## Phase 3B still to build
Lottie catalog (needs the Lottie dependency and animation files), custom icon set, stories, cinematic media viewer, profile showpiece, onboarding wow, typography polish, micro-interaction pass, a graphics performance settings screen (quality low/auto/high).
## Phase 3C still to build
Material You opt-in, curated themes + live preview, circular-reveal theme switch, shared-element/predictive-back transitions, skeletons everywhere, haptics/sounds map, bubble-style options, widgets.
