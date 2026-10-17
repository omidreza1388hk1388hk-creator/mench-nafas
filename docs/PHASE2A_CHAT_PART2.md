# Phase 2A (part 2) — Chat list: folders, filters, swipe, mark read/unread (v14)

Builds on v13 (part 1). **Android only, no backend change, no migration, no new dependency.**

## Done
- **Filter strip** above the chat list: All · Unread (with count) · Groups (only when you have a group) · your folders · “+”. Long-press a folder → rename / delete. Up to 12 folders, names ≤ 24 chars, duplicates (case-insensitive) rejected.
- **Folders** are local to this phone (SharedPreferences, `ChatListPrefs`), like drafts. Add a chat via its long-press menu → “Add to folder” (checkboxes + “New folder” that already contains the chat).
- **Swipe a row**: toward the end = mark read / unread, toward the start = archive / unarchive. The row always springs back; the list re-emits and restyles/moves it.
- **Mark as unread**: manual dot on the row (bold title), cleared when you open the chat. **Mark as read** on a chat with real unread messages moves the read cursor to its newest message (`ChatRepository.markConversationRead`, also tells the server through the existing `markRead`).
- Drafts already showed in the list (“Draft: …”), unchanged.
- Strings in English + Persian.

## Files
New: `feature/chat/presentation/{ChatFolders,ChatListPrefs,ChatListFolderUi}.kt`, `ChatFoldersTest.kt`.
Modified: `ConversationListScreen`, `ConversationListViewModel` (now also injects `@ApplicationContext`), `ConversationListUiState`, `ChatRepository(+Impl)` (`markConversationRead`), `MenchNavGraph`, strings (en + fa).

## Verified here / not verified
- `ChatFolders` compiled and run with kotlinc on the JVM (33 checks pass); all touched files parse cleanly.
- **Not compiled against Android/Compose** (same as part 1). Most likely first errors: `SwipeToDismissBox` / `rememberSwipeToDismissBoxState(confirmValueChange=…)` / `state.dismissDirection` (material3 1.3.0 expected from BOM 2024.09.03), `Icons.Filled.MarkEmailRead/MarkEmailUnread/Unarchive` (material-icons-extended).

## Behaviour notes
- Folders and manual-unread flags are per device, not synced; reinstalling clears them. Syncing them needs a backend table — say so if you want it.
- Swipe direction follows the layout direction (it mirrors in RTL).
- Filter selection is not persisted across app restarts (always starts on All).

## Still missing in Phase 2A
GIF search, sticker packs, round video messages, photo albums/editor, polls, locations, contact cards, scheduled & silent messages, translation, rich text / mentions / clickable links, floating date chips / seen-by / grouped bubble corners, groups & channels (roles, permissions, slow mode, topics), long-press-and-slide reaction picker, voice-to-text, earpiece switching. Phase 2B (calls) not started.
