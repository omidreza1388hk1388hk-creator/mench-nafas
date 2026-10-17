# Phase 4 (part 2) — Tablet layout, calendar switch, TalkBack announcements

Builds on `PHASE4_PART1_A11Y_RELIABILITY.md`. E2EE is still intentionally not started (spec: after the first successful build).

## Done in this drop (4A)
- **Two-pane layout** for width >= 840 dp (tablets, unfolded foldables, landscape tablets): `NavigationRail` (Chats / Contacts / Search / Settings) + conversation list (380 dp) + chat pane. The chat pane is its own small NavHost so each chat gets a correct per-conversation ViewModel. Esc (hardware keyboard) closes the open chat. Phones are unchanged (full-screen CHAT route still used). The chat screen body was moved into `ChatRoute(...)` so both hosts share it. Group info and media gallery open full-screen over the panes.
- **Calendar switch** (Jalali / Gregorian) in Appearance; `MenchCalendar` is now the single place that builds the date locale and also honours the digit setting (`numbers=latn|arabext`). Call history, chat export, last seen, day headers, bubble times and the "Us" page all go through it.
- **TalkBack live announcement**: a message that arrives while a chat is open is read out ("sender, text, time"); history loaded on open stays silent.
- Tests: `MenchCalendarTest`, `AdaptiveTest`; fa/en strings parity still enforced (new keys added to both).

## Files
`core/ui/adaptive/Adaptive.kt`, `core/ui/theme/MenchCalendar.kt`, `core/ui/navigation/MenchNavGraph.kt`, `feature/appearance/**` (model/store/repo/VM/screen), `feature/chat/presentation/{ChatScreen,LastSeenFormatter,ChatExporter,UsComponents}.kt`, `feature/calls/presentation/CallHistoryDialog.kt`, `MainActivity.kt`, strings fa/en, tests under `src/test/.../adaptive` and `.../theme`.

## Manual steps
None (no migration, no Gradle line, no env var).

## Risks / how to verify
- Never compiled. Most likely compile spots: `MenchNavGraph.kt` (new imports, `ChatRoute` extraction, `VerticalDivider` needs material3 >= 1.3, which the BOM pins) and the `a11y*` block in `ChatScreen.kt`.
- Calendar/digit changes apply to screens as they next recompose or are re-opened; an already-visible date string does not refresh by itself.
- Test on a tablet/emulator at >= 840 dp: pick a chat, rotate, fold/unfold; open group info and come back.
- Two-pane selection is saved across rotation; the open chat's own state is restored by the inner NavHost.

## Still missing in Phase 4
4A: Arabic localization, plurals, drag-and-drop attachments, 200 % font-scale audit, pseudo-localization test.
4B: remote crash reporting + breadcrumbs, ANR detection, Compose/screenshot/DAO tests, detekt/ktlint, Redis pub/sub fan-out, BullMQ jobs, Prometheus metrics.
4C: E2EE, disappearing / view-once messages, per-chat lock, encrypted backup, two-step verification, privacy settings (last seen / photo / receipts), certificate pinning option.
