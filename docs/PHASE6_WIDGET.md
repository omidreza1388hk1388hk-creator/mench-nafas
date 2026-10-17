# Phase 6 — Home-screen widget «ما»

- Long-press the home screen → Widgets → MENCH. Shows 💗, «N روز با هم بودیم» and the countdown to your next meeting («N روز تا دیدار» / «امروز می‌بینیمت!»). Tapping opens the app.
- Reads the dates cached in the app's database, so it works offline. Refreshes about every 30 minutes (system limit), instantly when either of you changes the dates (via the background service), and the day count rolls over within ~30 min of midnight.
- If no dates are set yet it says «تاریخ‌تون رو تو اپ بذارین 💗» — set them from the heart button in the chat.
- Built with plain RemoteViews (no new dependency). Today's mood isn't on the widget (it isn't cached locally).
