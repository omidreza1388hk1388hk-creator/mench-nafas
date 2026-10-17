# Phase 3A (part 1) — Heartbeat and full-screen message effects (v13)

Master-prompt reference: Phase 3 → 3A "The romantic layer" (items **Heartbeat** and **Word-triggered full-screen effects**).
Phases 1 and 2 are the base; nothing from them was rewritten.

## Done in this drop

### Heartbeat
- **Hold the heart** (new big button in the «ما» dialog, under the miss-you button). While you hold, the heart pulses and *your own* phone vibrates "lub-dub", so you feel what they will feel.
- On release (1–10 s) the held duration goes to the server (`POST /conversations/:id/heartbeat {durationMs}`), which relays a live `heartbeat` event to the partner only.
- The partner's phone plays a **full-screen heartbeat** (pulsing heart, expanding ring, glow) for the same duration and vibrates in the same rhythm (custom `VibrationEffect` waveform with amplitudes). Tap the overlay to close it early.
- The sender is told honestly whether the partner was online (`delivered`): "Your heartbeat arrived" vs "They are offline, so they didn't feel it".
- Direct chats only. Never stored, never pushed (a pulse that arrives hours later is noise).

### Word-triggered full-screen effects (5)
| Effect | Persian triggers | English triggers |
|---|---|---|
| 💗 falling hearts | دوستت دارم، عاشقتم | i love you, love you, love u |
| 🎉 confetti | تبریک، مبارک، مبارکه | congrats, congratulations |
| 🌙 moon and stars | شب بخیر، شبت بخیر، شبتون بخیر، شب خوش | good night, goodnight |
| 🏮 glowing lanterns | دلم برات تنگ، دلتنگتم | miss you |
| 🎈 balloons | تولدت مبارک، تولد مبارک | happy birthday |
- Detection happens **on each phone from the message text** (normalised: Persian ی/ک, ZWNJ, diacritics, punctuation), so no wire change and nothing extra is stored. Priority: balloons → hearts → lanterns → night → confetti.
- Your own message plays the effect the moment you send it; incoming messages play it only if they are new and less than 60 s old (history and old catch-up never replay).
- **Manual picker:** long-press the send button → choose an effect → the message is sent and the effect goes to the others as an ephemeral `screen_effect` event (works in groups too).
- **Settings → «افکت‌ها و قلب‌تپش»:** switch for effects, switch for heartbeat vibration.
- Overlays never intercept touches. They draw nothing under "remove animations" and use half the particles on low-RAM devices. Colours come from `MaterialTheme.colorScheme`.

## Files
Backend (no migration, no new env vars — nothing is persisted):
- `realtime/realtime-events.ts` — events `heartbeat`, `screen_effect`
- `conversations/dto/send-heartbeat.dto.ts`, `dto/send-screen-effect.dto.ts` (new)
- `conversations/conversations.service.ts` — `sendHeartbeat`, `sendScreenEffect`
- `conversations/conversations.controller.ts` — `POST :id/heartbeat` (12/min), `POST :id/screen-effect` (30/min)
- `conversations/conversations.service.spec.ts` — 4 new tests

Android (no Room migration):
- New: `feature/chat/presentation/MessageEffects.kt`, `HeartbeatComponents.kt`
- `core/network/ChatApi.kt` (+bodies/endpoints), `core/network/realtime/RealtimeEvents.kt` + `RealtimeEventParser.kt`
- `feature/chat/domain/ChatRepository.kt` (new methods have default bodies so fakes still compile), `data/ChatRepositoryImpl.kt`
- `feature/chat/presentation/ChatViewModel.kt`, `ChatScreen.kt`, `UsComponents.kt`
- `core/ui/components/Motion.kt` (`AnimatedSendButton` gets optional `onLongClick`), `core/ui/navigation/MenchNavGraph.kt`
- `feature/settings/presentation/SettingsScreen.kt`, `res/values{,-fa}/strings.xml`, `build.gradle.kts` (versionName only)

## Manual steps
1. Deploy the backend (no migration). 
2. Gradle sync + build; nothing new to add to Gradle, Proguard (`core.network.**` is already kept) or the manifest (`VIBRATE` already declared).

## Risk list (what is most likely to fail, and how to check)
1. **Compile — `combinedClickable`** in `Motion.kt`: wrapped in `@OptIn(ExperimentalFoundationApi)`. If your foundation version has it stable you only get an "opt-in not needed" warning.
2. **Compile — Compose graphics calls** in `MessageEffects.kt`: `Path.combine`, `Rect(center, radius)`, `withTransform({ translate/rotate/scale }) { … }`. These exist in the pinned BOM; if one fails, the error will name the exact line.
3. **Runtime — hold gesture inside the dialog**: `HeartbeatHoldButton` uses `detectTapGestures(onPress)` inside `MenchDialog`. Check: press and hold ≥ 1 s → release → partner feels it; < 1 s → "hold a little longer".
4. **Runtime — vibration**: devices without amplitude control fall back to timings only; some OEMs throttle long waveforms while the screen is off. Heartbeat needs the partner's app in the foreground or its socket live.
5. **Runtime — effect false positives**: "مبارک" alone triggers confetti by design; if that feels too eager, remove it from `MessageEffectTriggers.phrases`.
6. **Perf**: effects allocate small lambdas per particle per frame (≈30–70 particles). Fine on mid-range; check on a 2 GB device (the low-RAM path halves the count).

## Known gaps in this drop
- Trigger phrases are built in; there is no editor for custom phrases yet.
- If you hand-pick an effect for a text that also contains a trigger word, the partner may see two effects back to back (the later one replaces the earlier).
- Heartbeat is "send on release" (a pulse of the length you held), not a continuous live stream while you hold; the "sync hearts when both are online" mode is not built.
- No push for heartbeats or hand-picked effects when the partner is offline (by design).

## Phase 3 checklist — what is still missing
**3A (romantic layer)** — done: heartbeat, word-triggered effects, manual effect picker. Still to build: sync-hearts mode; custom trigger-phrase editor; time-capsule letters (sealed until a date, envelope + wax-seal animation, countdown, handwriting font, attachments); multiple shared counters (+ widgets); love timeline + "on this day"; shared canvas and shared album; together mode (music/video sync); couple games; daily rituals and streaks + mood duet; distance/time and live "I'm coming" tracker; surprises and "open when…" extensions; Year in Love; couple profile and private vault; romantic sounds and haptic signatures (kiss/hug haptics already exist from earlier phases).
**3B (graphics)** — not started: living backgrounds, animated bubbles, real glassmorphism, depth/tilt cards, AGSL shaders, Lottie catalog, custom icon set, stories, cinematic viewer, profile showpiece, seasonal themes, onboarding wow, typography polish, micro-interactions, graphics performance guardrails (the effects here already follow the low-RAM and reduce-motion rules).
**3C (polish)** — not started: Material You opt-in, curated themes + live preview, circular-reveal theme switch, shared-element/predictive-back transitions, skeletons everywhere, haptics/sounds map, profile pages, widgets, bubble-style options.
