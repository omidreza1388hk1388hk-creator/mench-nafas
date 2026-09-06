-- Phase 3 (voice messages): audio joins image/file as a third attachment
-- kind. Duration is reported by the client (it already knows exactly how
-- long the recording ran) rather than extracted server-side — avoids
-- adding another native audio-processing dependency for a value the
-- client already has for free.

ALTER TABLE attachments DROP CONSTRAINT IF EXISTS attachments_kind_check;
ALTER TABLE attachments ADD CONSTRAINT attachments_kind_check
    CHECK (kind IN ('image', 'file', 'audio'));

ALTER TABLE attachments ADD COLUMN duration_ms INTEGER;
