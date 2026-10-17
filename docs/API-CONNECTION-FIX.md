# MENCH API connection fix

## Production API

The Android app now defaults to:

`https://mench-production.up.railway.app/api/v1/`

This prevents a locally-built APK from silently using `10.0.2.2:3000`, which only works from the Android emulator.

## Auth endpoints

- `POST /api/v1/auth/otp/request`
- `POST /api/v1/auth/otp/verify`
- `POST /api/v1/auth/token/refresh`
- `POST /api/v1/auth/logout`

## Public health endpoints

- `GET /health` — conventional Railway/platform health check
- `GET /api/v1/diagnostics/ping` — API liveness check used by Android

## Local development

To point the Android app at a local backend, override the default at build time:

`./gradlew assembleDebug -PMENCH_API_BASE_URL="http://10.0.2.2:3000/api/v1/"`

For a physical Android phone, use the computer's LAN IP instead of `10.0.2.2`, for example:

`./gradlew assembleDebug -PMENCH_API_BASE_URL="http://192.168.1.10:3000/api/v1/"`

The production URL remains HTTPS and does not require cleartext traffic.
