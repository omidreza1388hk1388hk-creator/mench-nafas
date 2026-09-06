# Environment Setup

## Backend

Requires: Node.js 20+, PostgreSQL 16+, Redis 7+.

```bash
cd backend
cp .env.example .env
# edit .env — at minimum set DATABASE_URL, REDIS_URL,
# JWT_ACCESS_SECRET, JWT_REFRESH_SECRET to real local values
npm install          # generates package-lock.json on first run —
                      # commit it once generated, so CI's `npm ci` works
npm run migrate
npm run start:dev
```

Variables (see `backend/.env.example` for the authoritative list):

| Variable | Required | Notes |
|---|---|---|
| `DATABASE_URL` | yes | Postgres connection string |
| `REDIS_URL` | yes | Used for OTP resend-cooldown state |
| `JWT_ACCESS_SECRET` / `JWT_REFRESH_SECRET` | yes | ≥16 chars, generate with e.g. `openssl rand -hex 32` |
| `OTP_PROVIDER` | yes | `dev` locally; a real provider must be added (see ARCHITECTURE.md) before this can be `sms` |
| `SMS_PROVIDER_API_KEY` / `SMS_PROVIDER_SENDER_ID` | only when `OTP_PROVIDER=sms` | not used by any code yet — Phase 2+ |
| `STORAGE_PROVIDER` | yes | `local` locally (writes to `STORAGE_LOCAL_DIR`); forbidden in production, same enforcement as `OTP_PROVIDER=dev` — a real S3-compatible provider must be added before `s3` can be used |
| `STORAGE_LOCAL_DIR` | only when `STORAGE_PROVIDER=local` | defaults to `./uploads`, gitignored |
| `MAX_ATTACHMENT_SIZE_BYTES` | no | defaults to 60MB (bumped from 25MB for video clips) |
| `S3_*` | not yet used | reserved for the real production storage provider |
| `TURN_*` | not yet used | reserved for Phase 4 calls |

## Android

Requires: JDK 17, Android SDK (compileSdk 35, minSdk 26), Gradle (wrapper
included — no local Gradle install needed).

```bash
cd android
./gradlew assembleDebug -PMENCH_API_BASE_URL="http://10.0.2.2:3000/api/v1/"
```

`10.0.2.2` is the Android emulator's alias for the host machine's
`localhost` — use your backend's real reachable address for a physical
device or a deployed backend. This value is injected via
`BuildConfig.API_BASE_URL` at build time (see `app/build.gradle.kts`); it
is never hardcoded in source.

## Running against Termux specifically

`./gradlew` needs a JDK and enough memory headroom
(`org.gradle.jvmargs` in `gradle.properties` is set to 2GB — lower it if
the device is memory-constrained). Building an Android app *inside*
Termux (rather than in CI) is possible but slow; the GitHub Actions
workflow in `.github/workflows/android-ci.yml` is the recommended way to
get a real, validated build without doing it on-device.

## Known limitation of this repository as delivered

This project was generated in a sandboxed environment with no internet
access and no Android SDK installed, so none of the install/build/test
commands above have actually been run against real dependencies yet.
Treat your first `npm install` + first CI run as the real validation
step — see the note in the root `README.md`.

`sharp` (used for image thumbnailing, added in Phase 3a) compiles/fetches
a native binary on `npm install`. It ships prebuilt binaries for common
platforms including the `ubuntu-latest` GitHub Actions runner, so this is
expected to work without extra setup — but if the backend CI run fails
specifically at `npm install` or on first use of `sharp`, that native
binary step is the first place to look.
