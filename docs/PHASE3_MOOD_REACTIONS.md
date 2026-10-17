# Phase 3 — Today's mood + heart reactions

## حال امروز (mood)
- In the «ما» card: tap an emoji (🥰 😍 😊 😎 😴 🥱 😢 😡 🤒 💪) with an optional short note (≤60). The partner's mood shows in the card and as a small emoji next to their name in the chat top bar.
- A mood only counts for the local day it was set; after midnight it disappears on its own.
- Backend: migration `016_phase9_mood.sql` (per-member columns); `GET/PATCH /conversations/:id/mood`; event `mood.changed`. No push (ambient). No new Room version.

## Hearts on reactions
- When someone adds a heart-ish reaction (❤️ 😍 🥰 😘 💕 💗 💖) to a message already on screen, hearts float up and the phone gives a small pop. Tracked per message, so opening a chat or loading history never triggers it.
- Quick reactions row now: 👍 ❤️ 😍 🥰 😂 😮 😢 🙏.

## Decided against
Hidden private chat with its own PIN — not wanted.

## Still open
Phase 2 leftovers: emoji picker, drafts, archive, Jalali dates in the message list, "delivered" tick.
