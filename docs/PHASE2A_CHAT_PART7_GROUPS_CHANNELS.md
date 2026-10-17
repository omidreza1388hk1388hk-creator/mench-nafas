# Phase 2A (part 7) — Groups & channels: roles, permissions, slow mode, invite links (v20)

## Done
- **Roles**: owner › admin › member. The owner makes / removes admins (Group Info → "Make admin"); an admin can remove plain members but not other admins or the owner; members can only leave.
- **Permissions** (admins toggle in Group Info → "Who can…"): who can post, add members, edit name/info, pin messages — each "everyone" or "admins only". The server enforces all four (add members, rename, pin, send); the app also hides buttons that would be refused.
- **Channels**: a channel is a group with "Only admins can post" on. Members read and react; the composer is replaced by "Only admins can post in this channel." (No new conversation kind, so every existing group screen keeps working.)
- **Slow mode**: Off / 10 s / 30 s / 1 min / 5 min / 15 min / 1 h. Members wait between their own messages (server answers HTTP 429 with `retryAfterSeconds`); admins are exempt. A retried delivery of an already-accepted message is not counted as a second message.
- **Invite links**: admins create codes (12 readable characters, expiry 1 h / 1 d / 7 d / never, optional use limit, max 10 live), share them through the system share sheet, and revoke them. **Chats › ⋮ › Join a group with a code**: paste the code or the whole share message → preview (group name + member count) → Join. Joining twice is harmless and costs no invite use; revoked / expired / used-up codes say why. Lookups are throttled so codes can't be guessed.
- Strings en + fa.

## You must do
`npm run migrate` → `024_phase2a_group_governance.sql` (role `admin`, five permission columns, `conversation_invites`). No env, no new dependency.

## Verified / not verified
- Verified: backend `tsc` + **164 Jest tests** pass (new: group policy 8, group-admin service 10, enforcement cases in conversations/messages specs); client rules and invite-code parsing run on the JVM with kotlinc; all touched Kotlin parses.
- **Not compiled against Android/Compose; migration not run on a real Postgres here** (SQL is plain DDL, the role-constraint swap uses a DO block that finds the unnamed original CHECK).
- Likeliest first Gradle errors: `FlowRow` (needs the ExperimentalLayoutApi opt-in already added), new `ChatRepository` default methods, nav wiring in `MenchNavGraph`.

## Known limits / not built
- **Topics** (threads inside a group) are not built — they need `messages.topic_id`, per-topic unread counts and a topic list UI.
- No realtime push of permission changes: other members' apps re-read group info when their chat screen refreshes (they will get a 403/429 from the server in the meantime, shown as a failed message).
- A slow-mode / channel rejection of an *offline-queued* message ends as a failed message (tap to retry), not a friendly countdown.
- Transfer of ownership is not built (the owner role is fixed to the creator).
- Invite links are codes, not https deep links (no domain configured); the share text tells the receiver where to paste it.
