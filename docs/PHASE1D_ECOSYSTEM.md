# Phase 1D — Ecosystem (v13)

## Backend (migration 021)
- backend/src/database/migrations/021_phase1d_ecosystem.sql
- modules/safety: reports, restrictions (mute/suspend), appeals, auto rules (flood, repeat_message), audit log, transparency stats
- modules/growth: referrals + configurable rewards, MENCH Plus entitlements (server-authoritative), redeem codes, sticker packs
- modules/cloud: Saved Messages (references existing attachments), folders/tags, Files browser, usage
- modules/planner: reminders / tasks / events with repeat, checklist, linked message, Overdue/Today/Upcoming/Completed filters
- modules/ecosystem: user controllers + admin API (reports queue, restrictions, appeals, rules, audit, referral config, Plus grant, redeem codes, sticker packs)
- Admin panel (/api/v1/admin) gained Reports, Moderation and Growth tabs. Moderator name is asked at login and stored in the audit log.
- Sending now passes one gate: block check + active restriction + auto rules (AccountService.assertNotBlocked).

## Android
- Reminders & Tasks (local alarms via ReminderScheduler), Saved messages + Files browser, Invite friends / referrals,
  MENCH Plus + Sticker store (restore + redeem code), Report dialog, restriction notice + appeal, Transparency, Data usage dashboard
- Message long-press sheet: Save to cloud, Remind me, Report
- UsageTracker OkHttp interceptor counts bytes per day / Wi-Fi vs mobile / media

## Needs your action
- Run migrations 020 and 021; set ADMIN_API_KEY.
- Referral rewards are OFF by default: enable them in Admin > Growth.
- Add sticker packs in Admin > Growth (stickers are emoji glyphs for now).
- Plus is granted by redeem codes / admin / referrals. There is no Google Play Billing or Iranian payment-gateway integration yet.

## Not built (honest list)
- 1D.1 multiple accounts on one device (needs per-account database/cache isolation; large architectural change) and the account switcher
- 1D.2 Web/PWA and Desktop clients, QR-code login
- 1D.3 saved-item download/share actions, folder/tag management screens (API supports them)
- 1D.4 checklists and event cards inside conversations, participants on events, Jalali date picker
- 1D.5 per-type cleanup (photos/videos/files/voice), per-chat usage breakdown, per-chat storage rules, space-to-free confirmation
- 1D.6 shareable invitation card image
- 1D.7 real payment/billing
- 1D.8 moderator roles/permissions beyond a single admin key, user lookup by report, in-chat "report user" menu

---
# Phase 1D completion (v14)

Added after v13:
- **Multiple accounts** (core/accounts): each account is a "slot" with its own preferences, Room database, files and cache. The first
  account keeps its original file names, so existing installs lose nothing. Switching restarts the app. Settings > Accounts.
  Limits: only the open account receives notifications / unread counts; app-lock is shared by all accounts.
- **QR login for Web/Desktop**: backend modules/weblogin (challenge, nonce, secret, single-use claim) + phone screen Settings > Link a device
  that shows WHICH device/platform asks before approving. Needs `npm install` (new dependency: qrcode).
- **MENCH Web (PWA)** at /api/v1/web: installable, RTL Persian, Jalali dates, QR sign-in, chat list, read, send, dark/light.
  It polls every 4 seconds (no WebSocket yet) and shows text only (no attachments). Installing it from Chrome/Edge gives a desktop app window.
- **Moderator role**: MODERATOR_API_KEY can use reports/appeals/restrictions/audit/user lookup only; rules, rewards, Plus, packs need ADMIN_API_KEY.
- **Report user** from the chat-list menu; per-chat media auto-download rule; selective cleanup by media type with exact sizes and protection of unsent files.
- **Event cards** in conversations (events created from a chat are posted as a card); folder/tag editing and folder filter in Saved messages;
  shareable invitation card image.

Still not built: native Desktop app (Compose Desktop/Electron) and web attachments/E2EE; multi-account notifications for inactive accounts;
real payment gateway; cross-device sync of drafts/settings; per-chat usage breakdown.
