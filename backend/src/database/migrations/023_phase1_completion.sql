-- Phase 1 completion (v15): contact-sync privacy, cross-device settings/draft sync,
-- checklist items + event participants, blurhash placeholders, web push cursors.

-- 1C: contact-sync privacy. When false, a contact-sync lookup (POST /users/lookup-batch) never returns this user.
ALTER TABLE users ADD COLUMN IF NOT EXISTS discoverable_by_phone BOOLEAN NOT NULL DEFAULT true;

-- 1D.4: participants (display names) for events created from a chat.
ALTER TABLE planner_items ADD COLUMN IF NOT EXISTS participants JSONB NOT NULL DEFAULT '[]'::jsonb;

-- 1D.1: cross-device sync of small per-user state (composer drafts, synced settings). Last write wins per key.
CREATE TABLE IF NOT EXISTS sync_state (
  user_id    UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  key        TEXT        NOT NULL,
  value      TEXT        NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (user_id, key)
);
CREATE INDEX IF NOT EXISTS sync_state_user_updated_idx ON sync_state (user_id, updated_at DESC);

-- 1B: BlurHash placeholder (about 28 chars) computed server-side from the image / video thumbnail.
ALTER TABLE attachments ADD COLUMN IF NOT EXISTS blur_hash TEXT;
