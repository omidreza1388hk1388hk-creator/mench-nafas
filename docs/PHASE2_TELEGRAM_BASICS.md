# Phase 2 — Telegram-style basics (part 1)

## Added
- **Receipts:** own messages show an icon instead of text: clock (sending), ✓ (sent), ✓✓ tinted (read), ! (failed). Read state was already tracked; no backend change.
- **Online / last seen:** server derives "online" from live sockets and stores `users.last_seen_at` when a user's last socket closes. New realtime event `presence.changed` (also sent as a snapshot right after connecting). Shown under the contact's name in the chat top bar ("typing…" wins over presence). Last seen uses the Jalali calendar and Persian digits (ICU).
- **Mute / pin:** per-user flags on `conversation_members`. `PATCH /conversations/:id/settings {isMuted?, isPinned?}`. Muted chats still receive messages but no push. Pinned chats sort to the top. Long-press a chat in the list for the menu.

## Migrations
- Backend: `012_phase8_presence_mute_pin.sql` (runs automatically via the migrate script).
- Android: Room v8 → v9 (`MIGRATION_8_9`, two columns with DEFAULT 0).

## Not done yet (next slices)
Emoji picker, draft messages, archive, Jalali dates in message list, "delivered" (double grey tick) state. Stickers/GIF intentionally skipped.
