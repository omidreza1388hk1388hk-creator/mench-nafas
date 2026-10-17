# Phase 8B — Motion

- **Screen transitions** (MenchNavGraph): forward = slide in from the start edge (RTL-aware) + fade; back = reverse. Splash/Auth hand-offs cross-fade.
- **Animated splash** (`AnimatedSplash`, core/ui/components/Motion.kt): double-beat heart, expanding halo rings, fading title. Same gradient as the static launch window, so the hand-off is seamless. Shown for at least 1.3 s (`SplashRoute`).
- **Messages and chat list**: `animateItem()` — new items fade in, others glide into place.
- **Send button**: round gradient button, dim when there is nothing to send, springs up when text appears, icon morphs send <-> check (edit mode), light haptic tick. Send arrow now uses the auto-mirrored icon (points left in RTL).
- **Scroll-to-bottom button**: springs in/out instead of popping.
- **Helpers**: `MenchMotion.bouncy()/soft()` shared springs, `Modifier.pressScale(interactionSource)` for any tappable.
