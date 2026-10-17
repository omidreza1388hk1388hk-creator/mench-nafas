# Phase 2A (part 6) — Silent & scheduled messages, text formatting + clickable text, contact cards (v19)

## Done
- **Send without sound**: long-press the Send button → "Send without sound". The message is delivered live / on next sync like any other, but **no push notification** is sent (server-side `silent` flag on `POST /conversations/:id/messages`; also stored in the offline outbox, so a silent message sent offline stays silent).
- **Scheduled messages ("send later")**: same long-press sheet → In one hour / Tonight 21:00 / Tomorrow morning / Tomorrow evening / pick date + time. The **server** sends it at the chosen time (so the phone can be off): table `scheduled_messages`, a worker (every 15 s, `FOR UPDATE SKIP LOCKED`, crash-safe via release of stuck rows) calls the normal `MessagesService.send()` — numbering, delivery, push all identical. Window 1 minute .. 1 year, max 50 pending per chat, idempotent by client id. A "N scheduled messages" button above the composer opens the list with cancel. Failed sends (e.g. you left the group) are marked failed, not retried forever.
- **Text formatting**: `**bold**`, `__italic__`, `~~strike~~`, `||spoiler||` (tap to reveal), `` `code` ``; unbalanced markers stay literal. Stored text is exactly what was typed (old app versions just show the markers).
- **Clickable text**: web links (incl. `www.`), e-mail (opens mail app), **phone numbers** (opens the dialer — never dials by itself; Persian digits supported), **#hashtags** (tap = in-chat search), @mentions (styled). Chat list and reply quotes show the text without markers.
- **Contact cards**: attach → Contact → system contact picker (no contacts permission needed) → a card with name + number, **Call** (dialer) and **Save** (add-contact screen). Sent as a normal text message `contact:v1:…`; push says `[contact]`.
- Strings en + fa.

## You must do
`npm run migrate` → `023_phase2a_scheduled_messages.sql` (plus 021/022 if you skipped them). No new env, no new Gradle dependency.

## Verified / not verified
- Verified: backend `tsc` + **141 Jest tests** (new: scheduled messages 7, silent push 1, contact/poll markers); `RichText` (40+ checks), `RichBody` contacts, `SchedulePolicy` compiled and run with kotlinc on the JVM; all touched Kotlin parses.
- **Not compiled against Android/Compose, never run on a device.** Likeliest first errors: `ClickableText` (deprecated but present), `combinedClickable` params on the send button, `DatePickerDialog`/`TimePickerDialog` wiring, `ContactsContract` insert intent.

## Known limits
- No formatting toolbar yet: markers are typed (discoverability is weak) — a selection-based toolbar needs the composer moved to `TextFieldValue`.
- @mentions are styled but there is no member autocomplete (needs the groups work).
- Scheduled messages are text only (no attachments), edited by cancel + reschedule.
- Grouped bubble tails/spacing already existed from phase 1; no further corner work in this drop.
