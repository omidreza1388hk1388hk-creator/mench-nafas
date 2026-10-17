# Settings system — Phase 1 (slices 1A foundation, 1B reset, 1C security hub, 1D notifications, 1E account)

STATUS: **Phase 1 is NOT complete.** This slice delivers section A (architecture, component library, search,
categorized home, deep-link-safe routing). Sections B (Security hub), C (Account), D (Notifications) and the
typed `SettingsRepository` / reset flows are the next slices. Nothing here was compiled (no Kotlin compiler or
Android SDK in the build environment) — see "Not verified".

## What was built
**A. Architecture and components**
- Categorized home: profile card, search field, 10 category rows (Account, Security & Privacy, Notifications,
  Chats, Us, Calls, Data & Storage, Appearance, Language & Region, Help & About) with one-line summary and an
  "attention" badge (Security: shown when the phone has no screen lock; summary shows app lock / biometrics).
- One generic category screen (`SettingsCategoryScreen`) listing every registry row of a category. Rows that were
  on the old flat list are all still reachable (same destinations, same stored values — no pref keys touched).
- `core/ui/settings`: `SettingsSection`, `SettingsRow`, `SwitchRow`, `SliderRow`, `SingleChoiceRow` (bottom-sheet
  picker), `MultiChoiceRow`, `InfoBanner` (info/warning/danger), `ConfirmDialog` (optional typed confirmation),
  `PermissionRow` (icon + text status, opens system settings), `SettingsScaffold` (collapsing title, shared back
  handling), `ResetSectionRow`. Rows are >= 56dp, switches/radios/checkboxes use row-level semantics roles.
- Search: `SettingsTextNormalizer` (ي/ی, ك/ک, ZWNJ/ZWJ/tatweel/diacritics, Persian + Arabic-Indic digits -> ASCII,
  case, punctuation), `SettingsSearchIndex` (AND over tokens; title prefix > title substring > keyword prefix >
  keyword substring > subtitle; ties keep registry order). A hit opens its category screen, scrolls to the row and
  flashes it for ~1.6 s (colour fade only, no motion).
- Registry: `SettingKey` + `SettingsRegistry` (29 rows), titles reuse existing strings; subtitle/keywords are new
  `sx_sub_<id>` / `sx_kw_<id>` strings (en + fa).
- Routes: `settings/category/{key}?highlight={id}`; unknown category key pops back instead of showing a blank screen.
- `SecurityCheckup` scoring (pure, 6 items, ordered by impact, "needs attention" rule) — logic only, no UI yet.

## Files added
core/ui/settings/{SettingsComponents,SettingsPickers,SettingsDialogs,PermissionRow,SettingsScaffold}.kt
feature/settings/domain/{SettingsCategory,SettingKey,SettingsRegistry,SettingsSearchIndex,SettingsTextNormalizer,SecurityCheckup}.kt
feature/settings/presentation/{SettingsVisuals,SettingsActionHost,SettingsCategoryScreen,SettingsHomeParts}.kt
test: SettingsTextNormalizerTest, SettingsSearchIndexTest, SecurityCheckupTest, SettingsRegistryTest, TypedConfirmTest

## Files changed
- SettingsScreen.kt: flat list replaced by search + categories; two params appended (`onOpenCategory`, `onOpenSearchHit`);
  no existing parameter renamed or reordered; delete-account / log-out footer and profile card unchanged.
- SettingsViewModel.kt / SettingsUiState.kt: + `AppLockRepository`, `@ApplicationContext`, `refreshSecurityFacts()`,
  3 new state fields with defaults.
- MenchNavGraph.kt: + `Routes.SETTINGS_CATEGORY`, `Routes.settingsCategory()`, one `composable`, two new args on the
  existing SettingsScreen call. No other route touched.
- values/strings.xml, values-fa/strings.xml: +103 keys each (all prefixed `sx_`), no duplicates (checked by script and
  by the existing StringsParityTest rules).

## New keys / endpoints / migrations (whole of Phase 1)
- Strings: 233 `sx_` keys per language, en = fa, no duplicates, placeholders match (verified by script).
- Endpoints: `GET /devices` gains `isCurrent`; new `DELETE /devices/others` (see "Backend additions"). Migrations: none. No existing pref key changed.

## Slice 1B: real "Reset this section" and "Reset all"
- `SettingsResetPlan` (pure) lists ONLY explicit existing keys/prefixes: auto-download rules + mobile size cap
  (`mench_auto_download`), UI sounds, haptics, graphics quality, message-effect switches. Whole files are never wiped;
  per-chat download overrides, PINs, biometrics, keys, messages and accounts are untouched.
- Security reset only returns the auto-lock delay to 30 s (via `AppLockRepository`, and only if app lock is on).
- `SettingsResetter` executes the plan; removing a key lets each store fall back to its own default.
- UI: category screens show "Reset this section" only when the plan has something to reset; home has "Reset all
  settings" with typed confirmation (RESET / بازنشانی). New string `sx_reset_done` (now 104 keys per language).
- Deviation from the brief: no DataStore `SettingsRepository` yet. Existing stores stay the source of truth (rule 4),
  so reset works through adapters on those stores. A Flow-based repository is only needed once new settings are added.
- Not reset (still on their own screens): appearance theme/accent (DataStore), notification privacy and quiet hours
  (server/DataStore), wallpaper. Screens already open keep showing old values until reopened.
- Tests: `PrefResetPlanTest` (overrides kept, secrets never listed, canReset).

## Slice 1C: Security hub (section B, partly)
Route `settings/security-hub`, first row of the Security category (also searchable).
- **Checkup card:** score x/6 with a one-tap fix per missing item (phone screen lock -> system security settings; app lock,
  2-step, devices -> existing screens; E2EE -> encrypts the main chat; backup -> section on the same screen).
  Facts: KeyguardManager, AppLockRepository, `getTwoStep()` (offline/unknown is NOT reported as a problem), backup date,
  a local "devices reviewed" flag set when the user opens devices, main chat = most recent direct chat.
- **Encrypted chats:** every secure chat with a switch to turn it off (same `setSecureChat` call the chat uses), read-only
  security codes (my key, partner key, 60-digit shared code, QR of the shared code), and key-change status.
- **Key-change detector (trust on first use):** `PeerKeyTrust` pins the partner key; if it differs, `E2eeManager.prepareOutgoing`
  returns null so nothing is sent (the outbox retries, never plaintext) and the chat shows a dialog requiring
  acknowledgement; "Not now" hides it without unblocking. The security code is also shown in the chat's security dialog.
  This is a 2-line change inside `prepareOutgoing` plus read-only `cachedPeerKey`; no crypto routine was modified.
- **Locked chats:** list with a switch to unlock.
- **Backup:** last backup date (recorded by `BackupManager.write`), reminder never/weekly/monthly, banner when encrypted chats
  exist and no backup was ever made, "time for a backup" notice when due. The existing `BackupSection` is embedded here.
- Strings after 1C: 150 `sx_` keys per language (check script: parity, placeholders, no duplicates).
- New tests: `SecurityCodeTest`, `SecurityHubLogicTest` (reminder policy, main chat, banner).

Known limits of 1C: existing secure chats are pinned on their first send after this update (trust on first use, so a key that
changed BEFORE the update is not detected); the reminder is shown in the hub only (no push/WorkManager nag yet); no in-app QR
scanner for comparing codes (the QR can be read by any scanner, digits can be compared by eye); `BackupSection` is still also
on Data & Storage until Phase 2 replaces it with a status shortcut; reported-users list, app-lock extras (lock on app switch,
hide recents, failed-attempt limiter) and the 2-step last-changed date (backend returns no date) are not done.

## Slice 1D: Notifications (section D)
Route `settings/notifications-hub` (the "Notifications" row now opens it; the old privacy screen is one row inside it).
- **Master switch + per-type switches** (messages, "I miss you"/hugs/kisses, letters & surprises, reminders & anniversaries).
  Default ON, so nothing changes until flipped. Gate is `NotificationPolicy.shouldPost(type)`, checked at every posting site:
  `RealtimeForegroundService`, `MenchFirebaseMessagingService` (maps the backend's `data.type`; unknown types count as
  messages so nothing is swallowed by accident), `ReminderScheduler`, `AnniversaryWorker`.
- **Sound / vibration / importance per type:** Android makes channel settings immutable for apps, so each channel row opens
  the system's channel page (`ACTION_CHANNEL_NOTIFICATION_SETTINGS`). The app's own UI sounds remain on Appearance (Phase 2).
- **Preview privacy:** existing screen, linked. **Quiet hours:** existing `QuietHoursSection` embedded, plus "let calls ring in
  quiet hours" (default ON = today's behaviour; OFF silences the ring via `CallForegroundService`).
- **Per-chat overrides:** list of chats with mute/silent/vibration/custom sound (`ChatOverrides`, pure, tested), clear one or all.
- **Reliability panel:** background-connection status, battery-optimisation state + request, per-vendor auto-start pages
  (Xiaomi, Samsung, Huawei, Oppo/Realme, Vivo; falls back to app settings), and a test notification on the real messages channel.
- **Permission rows:** notifications (asks on Android 13+, otherwise opens settings), full-screen call alerts (Android 14+).
- Notification reset: master, type switches and quiet hours are part of "Reset this section".

Deliberately NOT added (nothing would be controlled): switches for **reactions** and **Together invitations** (the app sends no
notification for them), an **exact-alarm** permission row (the manifest does not declare it; reminders use inexact alarms),
and a **Calls** in-app switch (blocking incoming-call alerts silently would be unsafe; use the Calls channel page instead).

## Slice 1E: Account (section C)
- **Devices & sessions:** backend `GET /devices` now returns `isCurrent`; new `DELETE /devices/others` (rate limited 5/min, no
  migration) logs out every device and session except the caller. Android shows a "This device" badge, hides per-row logout for
  it, and adds "Log out all other devices" with confirmation. Web logins (QR) appear in this same list as devices; there is no
  separate web-login list endpoint.
- **Data export:** new screen `settings/data-export`: system file picker (user chooses where), progress bar, optional passphrase
  encryption (default ON; `PassphraseCrypto`, PBKDF2-SHA256 210k + AES-256-GCM, marker `MENCHEXP1`), clear warning when plain.
- **Delete account:** 4 steps (what is deleted + export link -> type DELETE -> 10 s "think again" wait -> biometric / phone
  screen-lock re-authentication, then delete). Back walks one step back. A phone with no screen lock passes re-auth (nothing to
  verify) but all other steps still apply.
- **Profile:** unchanged (name, username, bio, photo, read-only phone). **Change phone number is a documented gap:** the backend
  has no endpoint for it.

## Backend additions (all listed)
| Route | Change | Auth / limits | Migration | Test |
|---|---|---|---|---|
| `GET /devices` | adds `isCurrent` per device | JWT | none | `devices.controller.spec.ts` |
| `DELETE /devices/others` | revoke all devices + sessions except caller | JWT, 5/min | none | `devices.controller.spec.ts` |
Android client: `DevicesApi.revokeOtherDevices`, `DeviceSession.isCurrent`.

## Behaviour change to be aware of
The wallpaper dialog, effects dialog and battery-optimisation intent moved from the home screen into
`SettingsActionHost` (used by the category screen). Same dialogs, same intent.

## Not verified (no compiler)
- Compilation of every new/changed file, Compose API signatures (LargeTopAppBar, BringIntoViewRequester, MenchDialog usage).
- JVM tests (Android) and the backend jest spec have not been run (no SDK / no node_modules / no network here). I traced the assertions by hand against the code.
- Static checks that WERE done: bracket balance, every `R.string.*` exists in en and fa, project imports resolve
  (two unresolved ones — `BuildConfig`, `LIST_PANE_WIDTH_DP` — pre-date this work and are generated/const).

## Known gaps (Phase 1 remainder) and next step
1. Reset coverage for DataStore-backed settings (appearance theme/accent, quiet hours) once those get adapters.
2. B leftovers: app-lock options (lock on app switch, hide recents, failed-attempt limiter), reported users list, backup reminder notification, in-app QR compare.
3. Server-side per-type notification preferences and a key-change push event (needs backend work; not required for the local switches).
4. Deep links from outside the app (notification -> settings screen) need `navDeepLink` + manifest check; routes exist.
6. Calls and Language & Region categories show an "arrives in the next update" state until Phase 2.
**Exact next step:** run the build and the unit tests on a machine with the Android SDK, fix whatever the compiler finds, then Phase 2.

## Manual QA checklist (slice 1A)
- [ ] Home opens instantly; profile card, search box, 10 category rows visible; delete/log-out still at the bottom.
- [ ] Type "قفل" / "pin" / "ساعت سکوت" / "۲" → results; clear (X) restores categories; nonsense shows empty state.
- [ ] Arabic ي/ك typed on an Arabic keyboard still finds Persian titles.
- [ ] Tap a result → category opens, row scrolls into view and flashes once, then stops.
- [ ] Every old destination reachable: account, appearance, wallpaper, effects, sync, privacy & security, notifications,
      data & storage, battery, accounts, link device, find people, referrals, saved, planner, plus, contact privacy,
      blocked, diagnostics, help, feedback, legal, changelog, transparency, data usage, delete account.
- [ ] Security card shows "Needs attention" only when the phone has no screen lock; app lock on/off + biometrics text updates after toggling in Privacy & Security and coming back.
- [ ] Data & Storage → Reset this section: auto-download rules return to defaults, per-chat overrides remain.
- [ ] Home → Reset all: confirm stays disabled until RESET is typed; chats, login and PIN are unaffected.
- [ ] Security hub: score matches reality; each Fix button goes to the right place; score updates after returning.
- [ ] Turn a secure chat off from the hub; it leaves the list; the chat's own switch agrees.
- [ ] Security code: same 60 digits on both phones for the same chat; different if a key is swapped.
- [ ] Key change: replace the partner's key on the server -> next send is held, dialog appears in chat, "Not now" keeps it held,
      acknowledging releases the queued messages.
- [ ] Backup: create one -> date shows, banner disappears; reminder choice persists.
- [ ] Notifications: master off -> no notification of any type; each type switch silences only its own type; test notification shows.
- [ ] Notification channel rows open the system page for that channel; battery and auto-start rows open the right place.
- [ ] Quiet hours + "let calls ring" OFF -> an incoming call does not ring inside the window; ON -> it rings.
- [ ] Per-chat override: mute a chat for a while, set a custom sound -> appears in the list, Clear removes it.
- [ ] Permission row: deny notifications in system settings -> banner + row show "Not allowed"; tapping fixes it.
- [ ] Devices: "This device" badge on the right row; "Log out all other devices" logs out a second phone and keeps this one.
- [ ] Export: plain -> warning shown, file readable JSON; encrypted -> short/mismatched passphrase blocked, file starts with MENCHEXP1.
- [ ] Delete account (use a test account): cannot skip steps; wait counts down; cancelling biometrics deletes nothing.
- [ ] Calls / Language & Region open the empty state, not a crash.
- [ ] TalkBack: rows read title then subtitle; back arrow labelled; 1.5x font scale, no clipping.
- [ ] Light, dark, AMOLED: banner/badge text readable; RTL mirrors chevrons and back arrow.
