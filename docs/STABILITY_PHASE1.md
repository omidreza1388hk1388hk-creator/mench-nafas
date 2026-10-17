# Phase 1 — Stability notes

## Storage (S3 / R2)
Set in production: `STORAGE_PROVIDER=s3`, `S3_ENDPOINT`, `S3_ACCESS_KEY`, `S3_SECRET_KEY`, `S3_BUCKET`, `S3_REGION=auto`.
Cloudflare R2 works as-is. Run `npm install` (new dep: `@aws-sdk/client-s3`).

## Allowlist
`ALLOWED_PHONE_NUMBERS=+98912...,+98935...` (E.164, comma-separated). Unset = open registration.
Create both accounts first, then set it.

## TURN (code already wired — needs a server)
Backend already serves `TURN_URL / TURN_USERNAME / TURN_CREDENTIAL` to clients. You need a coturn instance
(Railway can't do UDP relay well; use a small VPS). Minimal `/etc/turnserver.conf`:

    listening-port=3478
    tls-listening-port=5349
    fingerprint
    lt-cred-mech
    user=mench:STRONG_PASSWORD
    realm=your.domain
    external-ip=YOUR_VPS_IP
    min-port=49152
    max-port=65535

Open UDP/TCP 3478, 5349 and UDP 49152-65535. Then set
`TURN_URL=turn:YOUR_IP:3478?transport=udp,turn:YOUR_IP:3478?transport=tcp`, `TURN_USERNAME=mench`, `TURN_CREDENTIAL=STRONG_PASSWORD`.

## Release signing
    keytool -genkeypair -v -keystore mench-release.keystore -alias mench -keyalg RSA -keysize 2048 -validity 10000
Export `MENCH_RELEASE_STORE_FILE`, `MENCH_RELEASE_STORE_PASSWORD`, `MENCH_RELEASE_KEY_ALIAS`, `MENCH_RELEASE_KEY_PASSWORD`
(env vars or ~/.gradle/gradle.properties). Without them, release falls back to debug signing. Back up the keystore.

## ProGuard
`proguard-rules.pro` now covers Moshi, Retrofit/OkHttp, Room, WebRTC, Hilt, Firebase. Test the release APK
(`assembleRelease`) on a device: login, send message, photo, call.

## WebSocket heartbeat
Server pings every 30s and terminates sockets that miss a pong. No client change.

## Still open
Token in WS URL (query string) and no re-check after connect; FCM reliability in Iran (consider a foreground-service WebSocket fallback); E2E encryption.
