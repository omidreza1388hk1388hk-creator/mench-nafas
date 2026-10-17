# Phase 1B — Speed and fluidity (what is in v11)

Done: image compression before upload (ImageCompressor), Coil memory/disk cache tuning, Outbox retry policy
(max 8 attempts, permanent-4xx detection, FAILED state, tap-to-retry, long-press delete, upload progress ring + cancel),
WebSocket jittered backoff + immediate retry on network return + delta sync after reconnect + connection banner,
auto-download rules + Data & Storage additions, static baseline profile.

Not done in this step (honest list):
- Paging 3 for messages/conversations (chat screen indexes the full list for search, unread divider, jump-to-reply)
- BlurHash/ThumbHash placeholders (needs server-side hash generation)
- Video transcoding (Media3 Transformer) and chunked/resumable uploads (needs backend endpoints)
- Macrobenchmark module (needs a device to record)
- Enforcement of auto-download rules inside bubbles (rules and UI are stored; bubble placeholder comes with 2A media work)
