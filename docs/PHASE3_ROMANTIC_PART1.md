# Phase 3 — Romantic layer (part 1)

## Added
- **Heart button in direct chats** (top bar) opens the "ما" card: days together, countdown to the next meeting, two Jalali-labelled date setters (Material date picker), and the big "دلم برات تنگ شده 💗" button.
- **Dates are shared:** either person can set them; both phones update live (`us.updated` event). Stored on `conversations.together_since / next_meet_date` (TEXT, yyyy-MM-dd).
- **Miss-you:** `POST /conversations/:id/miss-you` (20/min). Partner online: floating hearts + heartbeat vibration instantly. Partner offline: push "دلم برات تنگ شده 💗" (generic text if their privacy mode hides content; skipped if they muted the chat). Never stored as a message.

## Migrations
- Backend: `013_phase9_us_page.sql`.  Android: Room v9 → v10 (`MIGRATION_9_10`).

## Notes
- Accent color PINK already exists in Settings → Appearance; pick it on both phones for the pink look.
- The picker returns UTC dates, so the stored day matches the day chosen.
- Clearing a date isn't offered in the UI (Moshi omits nulls).

## Next slices
"Open when…" scheduled letters, shared memories album/timeline, heart animation on reactions, shared mood, hidden private chat with its own PIN.
