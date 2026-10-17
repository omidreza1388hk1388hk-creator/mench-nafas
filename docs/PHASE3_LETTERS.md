# Phase 3 — "Open when…" letters

- Heart button → «ما» card → **💌 نامه‌ها**. Write a letter with a title ("وقتی دلت برام تنگ شد…"), a body (up to 5000 chars) and an optional unlock date.
- The recipient sees only the title until they open it. The body is **never sent to their phone while sealed** (server enforces this, and refuses opening before the unlock time).
- Author sees what they wrote plus "sent" / "opened 💗". Recipient gets a live update or a push ("برات یه نامه نوشته 💌" — no title/body in the push).
- Backend: migration `014_phase9_love_letters.sql`; endpoints `GET/POST /conversations/:id/letters`, `POST …/letters/:letterId/open`; events `letter.received`, `letter.opened`.
- Android: no new Room version (letters are fetched live, not cached).
- Also fixed: ProGuard now keeps `core.network.**` wire classes (they're parsed by reflection, not codegen) — without this release builds would fail to parse API responses.
