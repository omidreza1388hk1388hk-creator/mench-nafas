# Phase 3 — final batch (v24.0, versionName 0.24.0-phase3-final-batch)

Everything buildable without an external file or a product decision is now written. **None of it has been compiled or run** (no Kotlin/Gradle, jest or Postgres in the sandbox). The backend `tsc` shows no new errors compared with v12 apart from missing-type-package noise. Note: `CoupleComponents.kt` has one more `(` than `)` in a string/comment — this is already the case in v12 and is not caused by this work.

**Run migrations 021 – 026 before deploying the backend** (counters, canvas, stories, vault, quizzes, counter themes).

## New in this batch
| Feature | Details |
|---|---|
| **Sync hearts** | If you and your partner hold the heart at about the same time (their heartbeat arrives while you hold, or within 6 s after you let go) both phones play the hearts effect and a "held it together" toast. `HeartbeatSync.kt` |
| **Counter themes** | Each counter card can have its own gradient (6 choices); backend column `theme` (migration 026), DTO, service, wire, UI. Milestone days now show a **sparkle burst**, and date counters show the **Persian (Jalali) date** under the title (ICU `fa_IR@calendar=persian`). |
| **Story rings on the chat list** | New `GET /stories/unseen`; the list view-model keeps the set of conversations with unseen stories (refreshes on realtime `story.changed`); `UserAvatar(showStoryRing = true)` draws a brand-gradient ring. |
| **Story reactions** | 5 quick emoji buttons under someone's story send "💗 ↩ «story text»" into the chat. |
| **Birthdays** | Partner's birthday (month/day only, kept on the phone) with a countdown tile on the couple profile; on a birthday the chat plays balloons once that day. |
| **Mesh-gradient wallpaper (AGSL)** | New animated wallpaper "Mesh gradient": four colour points on slow loops (AGSL on Android 13+, soft blobs below). |
| **Frame-time monitor** | In *Auto* graphics quality the animated wallpaper pauses itself if the average frame takes more than 24 ms and resumes below 18 ms (hysteresis). `shouldDegrade` |
| **Real blur** | The chat is blurred (Android 12+) while the private vault is open on top of it. |
| **Collapsing/parallax profile header** | The header drifts at half speed and fades as you scroll the tiles. |

## New tests
`FinalBatchTest` (sync rule, frame budget hysteresis, birthday countdown incl. 29 Feb, header fade), counters spec (theme), stories spec (unseen summary).

## Likeliest compile errors
`MenchNavGraph.kt` (the list route now reads `hiltViewModel<ConversationListViewModel>()` a second time and provides two locals), `LivingBackground.kt` (`MeshShader`, `Choreographer`, the new `when` branch), `CountersComponents.kt` (nested `CompositionLocalProvider` inside the card; imports added by script), `UserAvatar.kt` (`Modifier.border(Dp, Brush, Shape)`), `ChatScreen.kt` (`Modifier.blur` import, `context` use inside the new lambdas), and `persianDateText` (`android.icu` classes).

## Still NOT built
**Needs a file from you:** Persian handwriting font (letters), sound pack (notification/ringtone/UI sounds), Lottie files.
**Needs your decision:** live location + route for "I'm coming" (privacy rules), music/video provider for **Together mode**.
**Not built (large or server-heavy):** shared album, letter attachments (photo/voice), scheduled surprises and letter-unlock reminders (need a server scheduler + push), stories with photos/music/audiences, sticker packs and animated-emoji pack, bubble skins and pop-in animations, ripple/glow-trail shaders, per-chat accent colour, Glance widgets (last message, quick reply), a visual onboarding profile-setup flow, Eid theme.

---
# Addendum (v25.0, versionName 0.25.0-phase3-reminders-eid)

- **Letter-unlock reminders (server):** a time-capsule letter that becomes openable now triggers a push to the recipient ("the letter he/she wrote is ready 💌", content never included, respects the privacy mode). `LetterReminderJob` runs every minute (not under `NODE_ENV=test`), claims due letters **atomically** (`UPDATE … WHERE id IN (… FOR UPDATE SKIP LOCKED) RETURNING`) so a letter is reminded exactly once even with several instances, never overlaps two passes, and swallows errors. Migration **027** (`unlock_notified_at`; letters already unlocked are marked so no late push goes out). 4 jest tests.
- **Islamic-calendar occasions:** Eid al-Fitr and Eid al-Adha (confetti), Eid al-Ghadir (hearts), Mid-Sha'ban (lanterns), from Java's built-in Umm al-Qura calendar; may be a day off from local moon sighting. Test included.
- **Import audit:** a script compared every identifier in the 76 changed Kotlin files with the project's imports. Real misses found and fixed: `mutableStateOf`/`DisposableEffect` in `GraphicsModifiers.kt`, `Alignment` in `YearAndCards.kt`, `DisposableEffect`/`mutableStateOf`/`setValue` in `LivingBackground.kt`. The rest of what it flagged were named arguments or lambda parameters, not real misses. This is a heuristic, not a compiler.
- Run migrations **021 – 027** before deploying the backend.

## Remaining (unchanged list)
Needs a file: handwriting font, sound pack, Lottie. Needs a decision: live location for "I'm coming", media provider for Together mode. Not built: shared album, letter attachments, scheduled messages, stories with photo/music, sticker packs, bubble skins, ripple/glow shaders, per-chat accent, Glance widgets.

---
# Addendum 2 (v26.0, versionName 0.26.0-phase3-album-photos)

Still **not compiled or run**; backend `tsc` shows no new errors vs v12. **Run migrations 021 – 028.**

## New
- **Shared photo album** («ما» → 🖼️): 3-column grid, add a photo with an optional caption, tap for the full picture; each of you can leave **one reaction per photo** (💗😍😂🥺🔥; tapping yours again removes it); only the author can delete; live refresh through `album.changed`; max 300 photos. Backend module `modules/album` (+ `album_items`, `album_reactions`, 6 jest tests); photos are ordinary image attachments of the conversation, so they use the existing authenticated upload/thumbnail endpoints and the same "is this an image of this conversation" check as memories.
- **Photo in a time-capsule letter:** attach a photo when writing; the recipient only receives its id once the letter is opened (same rule as the text).
- **Photo stories:** a story can have a photo behind the text (darkened so the white text stays readable).
- **Pop-in animation** for brand-new messages (springs from 82 %); history is not animated.
- **Glow trails** on the shared canvas (a faint wider copy under every line).

## Files
Backend: migration `028_phase3_album_photos.sql` (album tables + `attachment_id` on letters and stories), `modules/album/*`, edits to `letters`, `stories`, `conversations.module.ts`, `realtime-events.ts`.
Android: new `AlbumComponents.kt`, `PhotoPick.kt`, `AlbumTest`, `PopInTest`; edited `ChatApi`, `RealtimeEvents/Parser`, `ChatModels` (`PickedPhoto`, `AlbumItem`, `attachmentId` on `Letter`/`Story`), `ChatRepository`/`Impl` (`uploadPhoto` helper), `ChatViewModel`, `ChatScreen`, `MenchNavGraph`, `UsComponents`, `LetterEnvelope`, `StoriesComponents`, `SharedCanvasComponents`, `GraphicsModifiers`, strings EN + FA.

## Likeliest compile errors
`ChatRepositoryImpl.kt` (the new `uploadPhoto` uses `asRequestBody` / `toMediaTypeOrNull` / `attachmentCache` that already exist for memories), `AlbumComponents.kt` (`LazyVerticalGrid` `items(items, key=…)` import), `StoriesComponents.kt` (the injected photo layer), the changed `onCreateLetter` / `onCreateStory` function types between `ChatScreen`, `LettersDialog`, `StoriesViewer`, `MenchNavGraph` and the view model.
