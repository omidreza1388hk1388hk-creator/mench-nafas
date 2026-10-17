# Phase 6 — Chat comfort (all together)

- **Drafts:** unsent text is saved per chat on the phone (SharedPreferences, never sent to the server), restored when you come back, cleared on send. The chat list shows «پیش‌نویس: …» in red under the name.
- **Emoji picker:** 😀 button in the composer opens a built-in grid (love emojis first); works offline, no extra library.
- **Archive:** long-press a chat → «آرشیو کردن». An archive icon appears in the list top bar to switch between the main list and the archive. Per-user (like mute/pin). Backend migration `018_phase10_archive.sql`; Android Room v11 → v12.
- **Swipe to reply:** drag a (sent, non-deleted) message sideways; reply starts at the threshold and the bubble springs back.
- **Scroll:** the chat now opens at the newest message (before, a long chat could open at the oldest), follows new messages while you're at the bottom, and shows a «برو پایین» button with a count of messages that arrived while you were reading history.
- **Typing indicator:** animated dots instead of static text.

## Not done
Delivered (grey double-tick) state, voice waveform, link previews, home-screen widget, E2E encryption.
