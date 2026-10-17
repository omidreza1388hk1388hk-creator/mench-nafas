-- Phase 8: presence (last seen), per-member mute and pin.
-- last_seen_at is written when a user's LAST live socket closes; "online"
-- itself is never stored — it is derived from live WebSocket state.
ALTER TABLE users ADD COLUMN last_seen_at TIMESTAMPTZ;

-- Per-member (not per-conversation): each person mutes/pins for themselves only.
ALTER TABLE conversation_members ADD COLUMN is_muted BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE conversation_members ADD COLUMN is_pinned BOOLEAN NOT NULL DEFAULT FALSE;
