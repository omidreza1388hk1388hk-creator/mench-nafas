# Phase 2 completion pass (v21)

Base: mench-phase2-all (the Phase 2 lineage). Not compiled here; build and send errors.

## Added
- Chat list: "typing…" shown in the row instead of the last message; pinned-chat limit (5, pin item disabled at the limit); archived chats return to the main list when a new message arrives unless muted (local + best-effort server flag).
- Chat: tap a reply quote to jump to the original message, which flashes (animated tint).
- Groups: ownership transfer (backend POST conversations/:id/transfer-owner, atomic role swap, tests; app: "Make owner" button for the owner with confirmation dialog).
- Groups: "Seen by" for your own messages (backend GET conversations/:id/messages/:messageId/seen-by, sender only, tests; app: action in the message sheet + dialog).
- Composer: @mention autocomplete from group members (names with spaces become Name_Surname so RichText mentions keep working).
- Message translation: backend modules/translate (POST /translate, LibreTranslate-compatible provider, 503 when not configured, throttled, tests); app "Translate" action + dialog (Persian target, English for Arabic-script text).
- Per-chat theme: palette button in the chat top bar; wallpaper (or "same as app") + accent colour for that conversation only (local, ChatThemeDialog / rememberChatTheme).
- Long-press-and-slide reactions: long press shows a floating emoji bar above the bubble; slide to an emoji (it scales up) and release to react; release elsewhere opens the normal sheet. TalkBack long-click falls back to the sheet.
- Saved messages: kind 'saved' conversation (migration 025, POST /conversations/saved, unique per user, tests); app: bookmark button in the chat list opens or creates it, localized title, no call / couple buttons.
- Live location: location confirm dialog offers 15 min / 1 h / 8 h; foreground service (type location) sends one message carrying an expiry (`loc:v1:lat:lon:until`) and edits it every 30 s; Stop in the notification ends it for recipients immediately; bubble shows "Live until HH:mm" / "ended" and hides the edited label. New permission FOREGROUND_SERVICE_LOCATION.
- Viewer transition: the image viewer grows out of the tapped thumbnail and shrinks back into it (container-transform style, background fade).

## Manual steps
Run migration 025 (saved messages). No new dependency. Optional: set TRANSLATE_URL (and TRANSLATE_API_KEY) in the backend .env to enable translation.

## Risks
- ChatScreen has many new parameters (onLoadSeenBy, isGroupChat, onShowSeenBy, onJumpToMessage): check named-argument call sites.
- Backend specs for the new service methods build services by hand; adjust constructor arity if it differs.
- Live location: each update is a message edit (the server keeps edit history rows); recipients on old app versions would see the raw `loc:v1:` text only if they lack location support (they already do not).
- Live location needs the app's location permission granted before starting; background location access is not requested (foreground service covers it).
- Viewer transition assumes an edge-to-edge window; a few dp of vertical offset is possible on some devices.
- Ownership transfer does not push a realtime event; other members see the new roles after refreshing the group info.

## Still missing in Phase 2 (see PHASE2_STATUS.md)
Photo multi-select with album grouping and in-app photo editor, earpiece switching + voice-to-text, server-hosted sticker packs,
topics, formatting toolbar, 2B: group calls, screen sharing, background blur, noise suppression, ConnectionService/Telecom.
