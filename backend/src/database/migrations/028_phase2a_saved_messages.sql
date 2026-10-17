-- Phase 2A: "Saved messages" — a private one-member conversation of kind 'saved' per user.
ALTER TABLE conversations DROP CONSTRAINT IF EXISTS conversations_kind_check;
ALTER TABLE conversations ADD CONSTRAINT conversations_kind_check CHECK (kind IN ('direct', 'group', 'saved'));
-- At most one saved conversation per user (created_by is the owner of a 'saved' conversation).
CREATE UNIQUE INDEX IF NOT EXISTS conversations_one_saved_per_user ON conversations (created_by) WHERE kind = 'saved';
