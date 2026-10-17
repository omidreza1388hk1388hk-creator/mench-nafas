-- Phase 3A: remember that the "your letter is ready to open" push for a time-capsule letter has been sent, so it goes out once.
ALTER TABLE love_letters ADD COLUMN unlock_notified_at TIMESTAMPTZ;
-- Letters that were already unlocked before this migration must not trigger a late push.
UPDATE love_letters SET unlock_notified_at = now() WHERE unlock_at IS NOT NULL AND unlock_at <= now();
CREATE INDEX love_letters_due_idx ON love_letters (unlock_at) WHERE unlock_at IS NOT NULL AND unlock_notified_at IS NULL AND opened_at IS NULL;
