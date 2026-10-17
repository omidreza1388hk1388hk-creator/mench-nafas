-- Phase 10: per-member archive (like mute/pin: each person archives for themselves only).
ALTER TABLE conversation_members ADD COLUMN is_archived BOOLEAN NOT NULL DEFAULT FALSE;
