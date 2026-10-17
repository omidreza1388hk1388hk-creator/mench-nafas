# Phase 1C (part 1) — People, onboarding, account and legal (v12)

## Done in this drop
- **Onboarding:** 3 intro slides → profile (name / username / bio / photo, skippable) → permission primers (notifications, contacts; mic/camera explained, asked at call time). Shown after every sign-in but skips itself for anyone who already finished it. The old "ask for notifications at launch" in `MainActivity` was removed; the primer replaces it.
- **Finding people:** search by `@username` (`GET /users/by-username/:u`), invite code (`/users/me/invite`, `/users/invite/:code`), invite link `mench://invite/<code>` (deep link in the manifest), QR generation (zxing core), share sheet, SMS invite (opens the SMS app, no permission).
- **Blocking:** `user_blocks` table, `POST/DELETE /users/:id/block`, `GET /users/me/blocks`; sending in a 1:1 chat to or from a blocked user is rejected (403); blocked users are invisible to username/invite lookups. Blocked-users screen with unblock.
- **Account deletion (Google Play requirement):** `GET /users/me/export` (JSON), `DELETE /users/me` with body `{"confirm":"DELETE"}`. Anonymises the account in place (phone number replaced, profile cleared, sessions/devices revoked, memberships removed). Android: export + share, acknowledge checkbox, confirm dialog, local sign-out.
- **Help / legal:** FAQ, terms, privacy policy, changelog screens (fa + en).

## Files
Backend: `migrations/020_phase12_account_safety.sql`, `users.{repository,service,controller}.ts`, `user.dto.ts`, `dto/delete-account.dto.ts`, `messages.service.ts` (block check), specs.
Android: `feature/people/**`, `feature/onboarding/**`, `feature/account/**`, `UsersApi`, `ChatRepository(+Impl)`, `MenchNavGraph`, `SettingsScreen`, `MainActivity`, `AndroidManifest.xml`, `build.gradle.kts` (zxing), strings fa/en.

## Manual steps
1. Run `npm run migrate` on the backend (migration 020). No new env vars. No Room migration.
2. Have the terms and privacy text reviewed by someone qualified before publishing; add a real contact address when you have one.
3. Gradle sync (new dependency `com.google.zxing:core:3.5.3`).

## Known gaps
- Access tokens already issued stay valid until they expire after an account is deleted (sessions are revoked, so no refresh).
- Avatars of people you share no chat with do not load (server rule); search results show initials.
- Blocking is reachable from Find people only; a block entry inside the chat header is not added yet.
