# Settings system — Phase 2

STATUS: **Phase 2 is built for every category (E to K) plus the tablet layout, but not every item of the brief.** What is
missing is listed per section under "Not built" with the reason. Nothing has been compiled or run (no Kotlin compiler / Android
SDK / node_modules / network in the build environment); see "Not verified".

Rule kept from Phase 1: a setting only gets a row if it changes real behaviour. Where the app has no such feature, the item is
listed as not built instead of adding a dead switch. Defaults reproduce the behaviour before the setting existed.

All new settings screens are reachable from their category (search finds them) and their values are covered by
"Reset this section".

## E. Chats  (`settings/chats`)
`ChatSettingsStore` (prefs `mench_chat_settings`; in-memory copy loaded in `MenchApplication.onCreate` so the repository can read
it). Pure rules in `ChatSettingsModel.kt`.

| Setting | Consumer |
|---|---|
| Message text size, line spacing | `RichMessageText` (all text bubbles) |
| Enter sends / auto-capitalise / spell check | composer text field (IME action, hardware Enter, Shift+Enter) |
| Link previews | `ChatRepositoryImpl.linkPreview` (no server request when off) |
| Quick reactions (up to 6) | `QUICK_REACTIONS` in `ChatScreen` (default = the old 8 until customised) |
| Double-tap reaction emoji | existing `ChatPrefs` (had no UI) |
| Swipe-to-reply direction | `SwipeToReply` (RTL aware) |
| Keep archived chats archived | `ChatRepositoryImpl` auto-unarchive |
| Photo quality / strip location / view-once default | attachment send path, `MediaPrivacy`, photo composer |
| Clear drafts | `DraftStore.clearAll` (this phone only) |
| Per-chat settings entry | pick a chat and open it (theme, lock, sound live in the chat menu) |

Not built: disappearing-message default timer and list (the app has no disappearing messages), clear search history (none is
stored), long-press menu ordering, default bubble skin / wallpaper for new chats, sender-name visibility in groups, video
compression level, send-on-long-press options.

## F. Us  (`settings/us`)
`UsSettingsStore`. Hide Mood, Question of the day, Wishlist, Memories (nothing deleted; `UsDialog`); hug/kiss/heartbeat vibration
strength soft/normal/strong (`AffectionHaptics`, NORMAL = original waveform); floating hearts on/off (`ChatScreen`).
Not built: shared-dates editor, anniversary lead time (the worker only knows "today"), widget options, letters / surprise /
Together defaults.

## G. Calls  (`settings/calls`)
`CallSettingsStore` (in-memory copy for WebRTC code). Video quality preset auto / data saver / high (`CallQualityPreset.apply`,
weak networks are never raised), noise suppression and echo cancellation switches (audio constraints), voice calls start on
speaker (`AudioRoutePolicy`), default camera front/back, mirror own picture, and last-call diagnostics (RTT, loss, jitter, tier,
bitrate cap; numbers only) with "Copy diagnostics".
Not built: ring / vibration options and the heartbeat ringtone choice, "calls while the app is locked" rule, TURN/relay info
screen, connection type in diagnostics (the stats sampler does not expose it).

## H. Data & Storage  (`settings/data-cleanup`)
Storage per media type + clear, auto-download rules and data usage already existed and are unchanged. Added: edited-photo
scratch files (`cache/photo-edits`) are removed daily by `CacheHygieneWorker` after 1 / 7 / 30 days (default 1), "clear now" with
size, and read-only shortcuts to backup status and data usage. Files queued for upload are NOT touched (they live in the
outbox cache, not in `photo-edits`).
Not built: storage breakdown per chat, low-data mode and proxy settings (the app has none), auto-clearing the general
attachment cache (could delete a payload still queued in the outbox).

## I. Appearance  (`settings/appearance-extras`)
Theme, AMOLED, accent, dynamic colour, font size, bubble style, digits and calendar already existed. Added: text weight
regular / medium (`rememberMediumTextWeight` -> typography), animation level full / reduced (feeds `animationsEnabled()`),
UI sounds volume and one switch per event (sent, received, error, confirm).
Not built: letter spacing (it breaks Persian letter joining, so deliberately omitted), a third "off" animation level (it would
behave exactly like "reduced"), app icon variants.

## J. Language & Region  (`settings/language`)
In-app language system / Persian / English with immediate effect (`AppLanguageStore`, `MainActivity.attachBaseContext`,
activity recreate; RTL follows), and a live preview of date, time and number using `MenchCalendar`. Calendar and digit style stay
in Appearance (linked).
Not built: first day of week and 12/24-hour override (no screen consumes them yet; times use `HH:mm` in places and the
locale default in others, which should be unified first). Notification text built outside the activity still follows the phone
language.

## K. Help & About  (`settings/about`)
Version / build with tap-to-copy (includes phone model, Android version), open-source licences list (Kenney CC0, Vazirmatn and
Gulzar SIL OFL, libraries), shortcuts to terms and changelog. FAQ, feedback and diagnostics already existed.
Not built: "what's new" shown once after an update, searchable FAQ, crash-report sender, diagnostics page extras.

## L. Polish
Done: two-pane settings at >= 840dp (list left, category right; the right pane does not take over back), new components used
everywhere, TalkBack roles on switches/radios/checkboxes, 48dp+ rows, no analytics.
Not done: loading skeletons, a formal 1.5x font-scale and contrast pass, per-screen error states beyond what exists.

## Tests added in Phase 2
`ChatSettingsModelTest`, `UsSettingsModelTest`, `CallSettingsModelTest`, `CacheHygienePolicyTest`, `AppearanceExtrasModelTest`,
`AppLanguageTest`; `SettingsRegistryTest` updated (every category has a row now).

## New strings
`sx_` keys now total 368 per language (en = fa, no duplicates, placeholders match; verified by script).

## Backend
No Phase 2 backend change.

## Not verified
Compilation; unit tests (not run); behaviour on a device: IME/hardware keys, swipe direction, language recreate and RTL,
worker scheduling, call settings during a real call, two-pane layout.

## Manual QA checklist (Phase 2, add to the Phase 1 list)
- [ ] Chats: text size/line spacing change bubbles immediately; Enter-sends works on soft and hardware keyboards; link previews off -> no preview request; quick reactions list shows your picks (max 6); swipe direction respected in Persian and English.
- [ ] Chats: keep-archived on -> an archived chat stays archived after a new message; photo original skips compression; location removed from an original photo (check with an EXIF viewer); view-once default pre-checked.
- [ ] Clear drafts removes unsent text on this phone only.
- [ ] Us: hiding Mood / Question / Wishlist / Memories removes only the button; soft vs strong vibration differ on a phone with amplitude control; hearts off -> no floating hearts.
- [ ] Calls: data saver caps bitrate (diagnostics shows cap 400000); noise/echo off changes audio; voice starts on speaker; default camera and mirror apply; diagnostics copy works.
- [ ] Data: clear now empties edited photos; 1/7/30 day choice persists; shortcuts open Security hub and Data usage.
- [ ] Appearance: medium weight changes text weight app-wide; reduced animations removes floating hearts; volume slider previews; each sound event can be silenced alone.
- [ ] Language: switching language recreates the app in Persian (RTL) or English (LTR); preview matches the chosen calendar/digits; "same as phone" restores the phone language.
- [ ] About: tap copies version and device details; licences list shows Kenney CC0 and the two SIL OFL fonts.
- [ ] Tablet (>= 840dp): settings shows list + detail, back leaves Settings from the list pane.
- [ ] Reset this section / Reset all revert the new settings (chat text size back to 100%, call preset auto, etc.).
