# Phase 3 — Private couple vault (v19.0, versionName 0.19.0-phase3-vault)

Encrypted shared notes for the two of you. **Not compiled or run** (no Kotlin/jest/Postgres in the sandbox).

## How it protects the notes
1. **Biometric gate:** opening the vault shows the phone's fingerprint/face prompt (reuses the app's `BiometricAuthenticator`). If the phone has no biometrics enrolled, the vault explains that and stays closed (no weaker fallback was added on purpose).
2. **End-to-end encryption:** you and your partner agree on a passphrase in person. Each note is encrypted **on the phone** with AES-256-GCM (key = PBKDF2-HMAC-SHA256, 120k iterations, salted per conversation; fresh random IV per note; token `v1:` + Base64). **The server stores only the opaque blob** and cannot read it.
3. A wrong passphrase fails the GCM tag check; the vault is accepted if it is empty (first passphrase) or at least one existing note decrypts. A note written with a different passphrase shows "can't open with your passphrase".
4. **No recovery:** if you both forget the passphrase, the notes are gone. The dialog says so.

Not protected: metadata (that a note exists, its size, who wrote it and when); a partner who knows the passphrase and has an unlocked phone; screenshots. Notes are plain text, up to 80 chars title + 1500 chars body, max 100 notes. Direct chats only; either person can add; only the author can delete.

## Backend (run migration 024 before deploying)
- `024_phase3_vault.sql` — `couple_vault_items`
- New module `modules/vault`: `GET/POST /conversations/:id/vault`, `DELETE …/vault/:itemId` (204); the DTO only accepts `^v1:[base64]{16,}$`, max 16 000 chars
- Realtime event `vault.changed`; `vault.service.spec.ts` (5 tests)

## Android
- New: `VaultCrypto.kt` (pure crypto, no Android classes, so it runs in plain unit tests), `VaultComponents.kt` (`VaultDialog`), `VaultCryptoTest` (6 crypto cases + 2 passphrase-rule cases)
- Wired through `ChatApi`, `RealtimeEvents/Parser`, `ChatModels` (`VaultItem`), `ChatRepository` (+defaults) / `Impl`, `ChatViewModel`, `ChatScreen`, `MenchNavGraph`, «ما» dialog button «🔐 گاوصندوق خصوصی»; strings EN + FA

## Likeliest compile errors
`androidx.fragment.app.FragmentActivity` / `BiometricAuthenticator` imports in `VaultComponents.kt`; smart cast of `n.note` (private data class, should be fine); `KeyboardOptions` import path in older Compose.

## Not included
Editing a note (delete and re-add), photo/voice notes, changing the passphrase (would need re-encrypting every note), auto-lock after a timeout, hiding the vault from the recents screenshot.
