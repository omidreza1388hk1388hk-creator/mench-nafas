-- Phase 4C: privacy settings, disappearing messages.

-- One row per user, created lazily on first change. A missing row means "all defaults"
-- (everything visible, read receipts on), so existing users need no backfill.
CREATE TABLE user_privacy (
    user_id        UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    last_seen      TEXT NOT NULL DEFAULT 'everybody' CHECK (last_seen IN ('everybody', 'nobody')),
    profile_photo  TEXT NOT NULL DEFAULT 'everybody' CHECK (profile_photo IN ('everybody', 'nobody')),
    add_me         TEXT NOT NULL DEFAULT 'everybody' CHECK (add_me IN ('everybody', 'nobody')),
    calls          TEXT NOT NULL DEFAULT 'everybody' CHECK (calls IN ('everybody', 'nobody')),
    read_receipts  BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Per-conversation timer in seconds; NULL = messages stay forever.
ALTER TABLE conversations
    ADD COLUMN disappear_after_seconds INTEGER
    CHECK (disappear_after_seconds IS NULL OR disappear_after_seconds BETWEEN 60 AND 31536000);

-- Each message remembers when it should disappear (decided at send time, so changing the timer
-- later never rewrites history).
ALTER TABLE messages ADD COLUMN expires_at TIMESTAMPTZ;
CREATE INDEX messages_expires_idx ON messages (expires_at) WHERE expires_at IS NOT NULL AND deleted_at IS NULL;

-- A trigger (instead of touching every insert path) so text, media, voice and forwarded messages
-- all get the conversation's timer.
CREATE OR REPLACE FUNCTION set_message_expiry() RETURNS trigger AS $$
DECLARE
    secs INTEGER;
BEGIN
    SELECT disappear_after_seconds INTO secs FROM conversations WHERE id = NEW.conversation_id;
    IF secs IS NOT NULL THEN
        NEW.expires_at := now() + make_interval(secs => secs);
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER messages_set_expiry
    BEFORE INSERT ON messages
    FOR EACH ROW EXECUTE FUNCTION set_message_expiry();
