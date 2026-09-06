-- Phase 3 (video messages): video joins image/file/audio as a fourth
-- attachment kind. Width/height/duration are all client-reported (same
-- reasoning as duration_ms for audio in migration 004) — extracting them
-- server-side would need ffmpeg, a heavy native dependency this project
-- avoids adding when the client already has the values for free via
-- MediaMetadataRetriever. The thumbnail is also generated client-side
-- (a single extracted frame) and uploaded as a normal JPEG/PNG that sharp
-- resizes exactly like any other image thumbnail — no server-side video
-- processing at all.

ALTER TABLE attachments DROP CONSTRAINT IF EXISTS attachments_kind_check;
ALTER TABLE attachments ADD CONSTRAINT attachments_kind_check
    CHECK (kind IN ('image', 'file', 'audio', 'video'));
