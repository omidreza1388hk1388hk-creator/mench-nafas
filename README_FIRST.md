# MENCH - everything built so far (Phase 1A to 1D)

This is ONE merged project: the v9 "new design" code plus every change from 1A, 1B, 1C and 1D.
Nothing here has been compiled yet (no Android SDK / Gradle was available when it was written),
so the first build may show a few compile errors. Send them back and they get fixed.

## Folders
- android/  Kotlin + Compose app (version 0.13.1-phase1d-final)
- backend/  NestJS server (migrations 001 to 022)
- docs/     one note per phase: PHASE1A..PHASE1D (what was built, what is NOT built)
- scripts/  fetch-fonts.sh (downloads the Vazirmatn font files)

## Steps before building
1. Fonts: run `sh scripts/fetch-fonts.sh` (or copy 4 Vazirmatn TTFs into android/app/src/main/assets/fonts/).
2. Backend: `cd backend && npm install`, then run the SQL migrations in order up to 022_phase1d_web_login.sql.
3. Backend env (see backend/.env.example style): set ADMIN_API_KEY (16+ chars) to enable the admin panel at /api/v1/admin.
   Optional: MODERATOR_API_KEY, PUBLIC_LINK_BASE, APP_LATEST_VERSION_CODE, APP_MIN_VERSION_CODE, APP_DOWNLOAD_URL.
4. In the admin panel (Growth tab) turn referral rewards on and add sticker packs if you want them.
5. Android: open android/ in Android Studio (or run Gradle) and build. Android App Links need
   https://mench.app/.well-known/assetlinks.json to auto-verify.

## Web version
After the backend runs, open https://YOUR-SERVER/api/v1/web in Chrome/Edge. "Install" gives a desktop window.
Sign in by scanning its QR with the app: Settings > Link a device.

## Not built (see docs/ for the full list)
Native Desktop app, real payment gateway, and Phases 2, 3 and 4 of the NEW master prompt (MENCH-master-prompt-updated.md).
Note: the older files docs/PHASE2_*, PHASE3_*, PHASE4_* ... PHASE8_* belong to the original v9 project's own history, not to the master prompt's phases.
