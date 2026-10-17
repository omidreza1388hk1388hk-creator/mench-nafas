# Phase 7 — everything else (before the build-and-fix pass)

## Messaging
- **Delivered tick:** grey ✓✓ when the other phone has received your message, blue ✓✓ when read. Phones ack on receive and after catch-up sync (`POST /conversations/:id/delivered`, live event `message.delivered`; not persisted, like read). No DB migration (state is stored as text).
- **Pinned message:** long-press → «سنجاق کردن»; a banner under the top bar (tap = jump, ✕ = unpin). One per chat, shared. Migration `019`; Room v12 → v13.
- **Link previews:** the server fetches the page (SSRF-guarded: http(s) only, address validated at connect time, no private/metadata ranges, 3 redirects max, 512 KB, 5 s, cached 6 h) and the card shows site/title/description under the message. Phones never contact the linked site.
- **Reply from the notification:** inline reply box on message notifications posted by the background service (not on FCM notifications, and not in "hide content" mode).
- **Call history:** 📞 icon in the chat-list top bar (the backend + repository already existed; there was no screen).
- **Export chat:** long-press a chat → «خروجی گرفتن از چت» → a .txt with Jalali timestamps via the share sheet (what this phone has synced; attachments appear as labels).
- **Drawing pad:** 🎨 in the composer: finger-draw, colours, thickness, undo/clear, sends a 1080×1080 PNG as an image.

## Couple
- **Question of the day:** one question per day (deterministic from the date); the partner's answer stays hidden until you've answered. Day boundary is Iran time (UTC+3:30).
- **Wishlist:** shared "things to do together" with checkboxes.
- **Anniversary reminders:** local daily check (~9:00): yearly + monthly anniversaries, 100/200/365/500/1000… days, and the day before / of your next meeting. Offline, no server.
- **Chat wallpaper:** Settings → «پس‌زمینه‌ی چت» (default / sunset / night / lavender / mint / plain).

## Migrations / versions
Backend `019_phase11_couple_extras.sql` (pin, daily_answers, wishlist_items). Android Room v13.

## Found by a tool check
`tsc` (no dependencies installed, so only dependency-free type errors show) found one real bug — a `string | null` return in the S3 provider — now fixed. Kotlin could not be checked at all.

## Deliberately NOT built
- **End-to-end encryption:** touches every message/attachment/search/push path and needs key backup for a lost phone. Doing it on top of unbuilt code risks a broken scheme that looks secure but isn't. Do it after the first successful build.
- **Voice waveform:** needs amplitude samples captured while recording (wire change) — pseudo-waveforms would be fake.
- **Scheduled / disappearing messages, live location, synced music/video:** each needs a server job or a continuous realtime/permissions layer; left out to avoid more unverified code.
