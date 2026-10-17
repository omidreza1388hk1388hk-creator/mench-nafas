# Phase 2A (part 4) — Polls, location, animated attach menu (v16)

## Done
- **Polls**: question + 2–10 options, single or multiple answers. Tap an option to vote (bars/percentages move at once, optimistic), tap again to retract; live updates for the other person over the socket; the creator can close the poll; voters are visible to members (not anonymous). A poll is a normal chat message (`poll:v1:<id>`) so it is delivered through the existing outbox path; only *creating* the poll needs a connection (it says so if it fails).
- **Location**: share where you are *once* (confirm dialog with a map preview first — never sent silently). Bubble shows a static map with a pin; tap opens your maps app (`geo:` with an OpenStreetMap web fallback). Uses the platform `LocationManager` (no Play Services), permission requested only when you tap Location. Map tiles come through our server (`GET /maps/tile/:z/:x/:y`, cached, throttled), so phones never contact the tile provider.
- **Attach menu**: the paperclip now opens an animated grid sheet (File/photo · Poll · Location); items pop in one after another, and it respects the system “remove animations” setting.
- Chat list, reply quote, pinned banner and push notifications show “Poll” / “Location” (`[poll]`, `[location]`).
- Strings in English + Persian.

## You must do
1. `npm run migrate` — migration `022_phase2a_polls.sql` (3 tables). (Also `021` if you skipped part 1.)
2. Optional `.env`: `MAP_TILE_URL_TEMPLATE` (empty = OpenStreetMap public tiles; fine for two people, their policy wants an identifying `MAP_TILE_USER_AGENT`), plus `GIPHY_API_KEY` from part 3.
3. Manifest now declares `ACCESS_COARSE_LOCATION` / `ACCESS_FINE_LOCATION` (runtime-requested only when sharing a location).

## Verified / not verified
- Verified: backend `tsc` + 130 Jest tests (new: polls service 9, maps service 4, rich-body markers); `RichBody`, `Poll` logic and `MapTiles` compiled and run with kotlinc on the JVM; all touched Kotlin parses.
- **Not compiled against Android/Compose; no device/GPS run; no real tile fetch** (tested against a fake fetch). Likely first Gradle errors: `Icons.Filled.Poll/CheckBox*`, `LocationManagerCompat.getCurrentLocation`, `LinearProgressIndicator(progress = { })` (needs material3 ≥ 1.2, you have 1.3 via the BOM).

## Known limits
- A poll's state is fetched live (not cached in Room), so an old poll shows “Loading poll…” while offline.
- Location is a one-time snapshot. **Live location is not built yet** (needs server-side expiring sessions + periodic updates + a foreground service).
- A poll marker only checks that the poll exists when voting; sending a marker for a poll of another chat just shows a poll nobody in this chat can vote in (they get 404).
- In-chat search still shows raw markers for rich messages.

## Files
Backend new: `modules/polls/*` (service, repository, controller, module, DTOs, spec), `modules/maps/*` (+spec), `022_phase2a_polls.sql`. Modified: `app.module.ts`, `realtime-events.ts` (`poll.updated`), `messages/rich-body.ts` (+spec), `env.validation.ts`, `.env.example`.
Android new: `PollUi`, `LocationUi`, `AttachMenuSheet`, `MapTiles`, `RichBodyLabels`, `core/network/MapUrls.kt`, test `PollAndMapTest`. Modified: `RichBody`, `ChatModels` (Poll), `ChatApi`, `RealtimeEvents/Parser`, `ChatRepository(+Impl)`, `ChatViewModel`, `ChatScreen`, `ConversationListScreen`, `MenchNavGraph`, `AndroidManifest.xml`, strings (en + fa).
