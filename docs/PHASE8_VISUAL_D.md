# Phase 8D — Chat list + Settings

## Data (no DB migration, no schema change)
- `MessageDao.observeLastMessages()` / `observeUnreadCounts(selfId)` + `ChatRepository.observeConversationPreviews()` (default empty, so fakes still compile) -> `ConversationPreview` (last message, own/deleted flags, unread count). Unread = received messages with `sequence > lastReadSequence`.

## Chat list
- Gradient top bar; gradient FAB with glow.
- Card rows: 54dp avatar, bold title, **last-message preview** ("شما:" prefix for own, 📷/🎬/🎤/📎 labels for media, draft in red), **time** (today = HH:mm, yesterday, else Jalali date), **unread badge** (gradient; grey when muted), pin/mute icons, pinned chats tinted.
- Empty state: glowing heart that beats + title/hint. Loading: shimmering skeleton rows instead of a spinner.

## Settings
- Gradient profile card (real photo, name, phone) that opens Account.
- Rows grouped in rounded cards (You / App / Help) with coloured icon tiles; logout in its own card; version centred at the bottom.
- Fixed: wallpaper and battery rows had reused the notifications icon.

New strings (en + fa): preview_*, empty_list_*, settings_section_*.
