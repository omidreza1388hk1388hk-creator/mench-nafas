-- Phase 4C: end-to-end encryption key directory + view-once media.
-- The server only ever stores the PUBLIC key (X.509, base64). Private keys never leave the phone.
ALTER TABLE users ADD COLUMN IF NOT EXISTS e2ee_public_key TEXT;
-- View-once: set the first time the recipient downloads an attachment whose filename starts with "vo-".
ALTER TABLE attachments ADD COLUMN IF NOT EXISTS consumed_at TIMESTAMPTZ;
