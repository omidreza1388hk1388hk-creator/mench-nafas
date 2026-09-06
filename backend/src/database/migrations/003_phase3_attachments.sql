-- Phase 3a: media foundation (images + generic files).
-- Video/voice/stickers/GIF/custom-emoji build on this same attachments
-- table in later Phase 3 sub-steps — the `kind` column and storage_key
-- scheme are deliberately generic enough not to need a breaking migration
-- when those are added.

CREATE TABLE attachments (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    uploader_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    conversation_id     UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    kind                TEXT NOT NULL CHECK (kind IN ('image', 'file')),
    storage_key         TEXT NOT NULL UNIQUE,
    thumbnail_storage_key TEXT,
    original_filename   TEXT NOT NULL,
    mime_type           TEXT NOT NULL,
    size_bytes          BIGINT NOT NULL,
    width_px            INT,
    height_px           INT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_attachments_conversation_id ON attachments(conversation_id);

-- Messages can now optionally carry a single attachment. Nullable, so
-- every Phase 1/2 text message row is untouched by this migration.
ALTER TABLE messages ADD COLUMN attachment_id UUID REFERENCES attachments(id);
