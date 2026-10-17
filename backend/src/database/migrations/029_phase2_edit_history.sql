-- Phase 2 completion: edit history. Every edit stores the body it replaced, so the
-- "edited" label can open a list of earlier versions.
CREATE TABLE IF NOT EXISTS message_edits (
  id          BIGSERIAL PRIMARY KEY,
  message_id  UUID NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
  old_body    TEXT,
  edited_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS message_edits_message_idx ON message_edits (message_id, id);
