# Phase 3A (part 2) — Time-capsule letters (v13.1)

Builds on the existing Letters feature (title, body, optional unlock date, sealed until then). **No backend change, no migration, no new permission.**

## What changed (Android only)
- **Sealed envelopes:** a letter you received that is still sealed shows a wax seal (heart) instead of an emoji.
- **Live countdown:** while a letter is waiting for its unlock date the card shows "opens in X days Y hours Z min", refreshed every 30 s while the dialog is open.
- **Opening animation:** tapping an unlocked sealed letter plays the seal breaking in two, the flap folding back, the paper sliding out, then the text fading in (about 1.1 s). Letters you already opened, or wrote yourself, open straight to the text. Skipped under "remove animations".
- **Handwriting look:** the letter body uses the system cursive family. It reads as handwriting for Latin text; Persian falls back to the normal font (a bundled Persian handwriting font would need a font file added to `res/font`).

## Files
- New: `feature/chat/presentation/LetterEnvelope.kt` (`WaxSeal`, `EnvelopeGraphic`, `LetterCountdownText`, `LetterReveal`, `splitCountdown`)
- Edited: `UsComponents.kt` (`LettersDialog`), `res/values{,-fa}/strings.xml` (`letters_countdown`), `build.gradle.kts` (versionName 0.13.1-phase3a2)
- New test: `LetterCountdownTest` (2 cases)

## Risks
1. Not compiled here (no Kotlin toolchain). Likeliest spots: `clipPath` + `withTransform` in `WaxSeal`, `weight()` inside the new `Row` in `LettersDialog`.
2. Check on device: open a sealed letter from the other account and watch the animation, then reopen it (should show text directly).

## Still missing in 3A
Attachments inside letters (photo/voice), a bundled Persian handwriting font, a local notification at the unlock moment, multiple shared counters (+ widgets), love timeline + "on this day", shared canvas/album, together mode, couple games, rituals/streaks, distance + "I'm coming", Year in Love, couple profile/vault.
