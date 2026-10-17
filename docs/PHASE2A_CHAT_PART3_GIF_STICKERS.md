# Phase 2A (part 3) — GIFs & romantic stickers (v15)

Order you asked for: GIF + stickers (this drop) → polls + location → round video messages.

## What you get
- **Media panel** under the composer: Emoji | Stickers | GIF tabs.
- **34 built-in romantic stickers** in 6 categories (love, missing you, kisses & hugs, greetings, sweet & silly, celebrate), Persian-first captions with English twins. Drawn by the app (gradient card + big emoji + caption): no assets, work offline, tiny messages.
- **“Make your own”**: your emoji (1–3) + your own caption + one of 8 colours, with live preview — this is how you make stickers that are only yours and your partner's.
- Recently used stickers; sticker/GIF messages show as “Sticker” / “GIF” in the chat list, reply quotes, pinned banner and push notifications (`[sticker]`, `[GIF]`).
- **GIF search**: opens on romantic topics (Romantic, I love you, Kiss, Hug, Miss you, Hearts, Cute, Good morning/night, Sorry…). Typing works in Persian (“دوستت دارم” → “i love you”, 35 mapped terms) or English; infinite scroll; “Powered by GIPHY” attribution (required by Giphy's terms).

## How it works (important design decisions)
- **Tenor is gone**: Google shut its API down on 2026-06-30, so this uses **Giphy**. Klipy is the other common replacement; the provider sits behind `GifsService`, so swapping later is a one-file change.
- **Phones never contact Giphy.** The backend does the search *and* proxies the GIF bytes (`GET /gifs/:id/media`). Reasons: your API key stays on the server; it works on networks that filter foreign CDNs (the same reason link-preview is server-side); the app's bearer token is never sent to a third party. A GIF message carries only the Giphy id.
- **No schema change.** Stickers and GIFs are normal text messages with a small marker body: `sticker:v1:<id>`, `sticker:v1:c:<palette>:<emoji>:<caption>`, `gif:v1:<id>:<w>:<h>`. Old app versions would show the marker text; the server maps it to `[sticker]` / `[GIF]` for notifications.
- Backend safety: ids validated by strict regex, media only fetched from `https://*.giphy.com` URLs that came from Giphy's own API responses, redirects refused, 4 MB cap, 8 s timeout, in-memory LRU (48 MB), throttled, API key never logged.

## You must do
1. Get a Giphy API key (developers.giphy.com), set `GIPHY_API_KEY=` (and optionally `GIPHY_RATING=g|pg|pg-13|r`, default `pg-13`) in the backend `.env`. A beta key is rate limited and Giphy requires approval for production. Without a key the GIF tab shows “GIF search isn't set up on the server yet” — stickers and emoji still work.
2. Android: Gradle sync (new dependency `io.coil-kt:coil-gif:2.7.0`). No backend migration this time.

## Found on the way (worth knowing)
The app's authenticated Coil loader was provided through Hilt but never registered as Coil's default loader, so `AsyncImage` calls would use a loader with no bearer token. `MenchApplication` now implements `ImageLoaderFactory` and returns the Hilt loader (lazily). If images already worked for you through another path, this is harmless; if they didn't, it fixes them.

## Files
Backend new: `modules/gifs/{gifs.service,gifs.controller,gifs.module}.ts` (+ `gifs.service.spec.ts`, 13 tests), `modules/messages/rich-body.ts` (+ spec). Modified: `app.module.ts`, `config/env.validation.ts`, `.env.example`, `notifications.service.ts` (+ spec).
Android new: `StickerCatalog`, `RichBody`, `GifQuery`, `StickerUi`, `GifUi` (picker, bubble image, `MediaPanel`), `GifViewModel`, `core/network/GifApi.kt`; tests `RichBodyTest`, `GifQueryTest`. Modified: `ChatScreen`, `ChatPrefs`, `ConversationListScreen`, `NetworkModule`, `ImageLoaderModule`, `MenchApplication`, `build.gradle.kts`, strings (en + fa).

## Verified / not verified
- Verified: backend `tsc` + Jest (all suites pass); `StickerCatalog`, `RichBody`, `GifQuery` compiled and run with kotlinc on the JVM (60+ checks, incl. every catalog sticker round-tripping); all touched Kotlin files parse cleanly.
- **Not compiled against Android/Compose**, and **no real Giphy call was made** (no key here — the service is tested against a fake fetch). First real test: set the key, open the GIF tab.

## Known limits
- Search results (in-chat search) still show the raw marker text for stickers/GIFs.
- Sticker/GIF messages can't be edited or copied (hidden in the menu); forwarding works only through the existing text-forward path.
- Stickers are static; GIFs animate. GIFs depend on Giphy's availability/links.

## Next (as you ordered): polls + location, then round video messages.
