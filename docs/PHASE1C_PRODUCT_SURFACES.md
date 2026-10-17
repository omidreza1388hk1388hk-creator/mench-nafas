# Phase 1C — Product surfaces (v12)

## Backend (run migration 020, set env)
- Migration: backend/src/database/migrations/020_phase1c_account_ops.sql
- New module: backend/src/modules/account (invite codes, username lookup, blocks, export, delete, feedback, remote config, analytics counters, admin API + panel)
- Messages: a blocked person can no longer send into a direct chat (403)
- Env: ADMIN_API_KEY (>=16 chars) enables the admin panel at /api/v1/admin; unset = 404.
  PUBLIC_LINK_BASE (default https://mench.app), APP_LATEST_VERSION_CODE, APP_MIN_VERSION_CODE, APP_DOWNLOAD_URL for the update prompt.
- Feature flags: insert rows through the admin panel (Flags tab); rollout is stable per user.

## Android
- feature/product: onboarding, find people (username, QR show/scan, invite link, SMS invite), blocked users, delete account + export,
  help/FAQ, feedback (+ crash log attach), terms/privacy, changelog, announcement banner, update prompt, rating prompt, share-to-MENCH picker, deep links
- core/notifications: NotificationPolicy (timed mute, quiet hours, per-chat sound/vibration, per-category channels), MarkReadReceiver,
  ShortcutPublisher (launcher + Direct Share), NewMessageTileService (Quick Settings)
- Manifest: mench:// scheme, https://mench.app/i/ app link (needs assetlinks.json), ACTION_SEND filter, shortcuts.xml, QS tile
- Deps: zxing core, CameraX (no Google Play Services needed)

## Not in this step
Glance widgets, notification bubbles, Android Auto/Wear, Play In-App Updates/Review (need Google Play Services; replaced by
config-driven update dialog and a store-link rating prompt), contact-sync privacy controls UI, per-chat sound picker UI
(policy + channels exist, no screen yet), reports/moderation queue in admin (comes with 1D.8).
