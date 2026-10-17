# Phase 3 — Memories timeline + hug/kiss

## Hug 🤗 / Kiss 😘
- Two buttons in the «ما» card (heart icon in the chat top bar). Partner online: a burst of themed emoji floats up their screen with its own vibration (hug = slow soft squeezes, kiss = a tiny pop). Offline: push ("بغلت کرد 🤗" / "برات بوس فرستاد 😘"; generic text in hide-content mode; skipped if muted). Not stored as messages.
- `POST /conversations/:id/affection {kind: "hug"|"kiss"}`, event `affection`.

## Memories 📸
- «ما» card → «خاطرات»: a shared timeline sorted by the date each memory is ABOUT (Jalali shown). Title, optional note (≤2000), optional photo.
- Photos reuse the normal image attachment pipeline (uploaded first, then referenced), so the existing authenticated endpoints and storage (S3/R2) apply. The server only accepts an image attachment from the same conversation.
- Either person can add; only the author can delete theirs. The other phone refreshes live (`memory.changed`).
- Backend: migration `015_phase9_memories.sql`; `GET/POST /conversations/:id/memories`, `DELETE …/memories/:memoryId`. No new Room version (fetched live).

## Notes
- Photos aren't compressed client-side yet (server makes the thumbnail); big camera photos upload at full size.
- Next: heart animation on reactions, shared mood, hidden chat with own PIN.
