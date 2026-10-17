-- Phase 4C: two-step verification (an extra password asked after the SMS code).
CREATE TABLE two_step (
    user_id         UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    password_hash   TEXT NOT NULL,                 -- argon2, never the password
    hint            TEXT,
    failed_attempts INTEGER NOT NULL DEFAULT 0,
    locked_until    TIMESTAMPTZ,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Short-lived proof "the SMS code was correct, now give me the password".
-- The SMS code is single-use, so without this the user would need a new SMS for every password typo.
CREATE TABLE two_step_tickets (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    device_name TEXT NOT NULL,
    attempts    INTEGER NOT NULL DEFAULT 0,
    expires_at  TIMESTAMPTZ NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX two_step_tickets_expires_idx ON two_step_tickets (expires_at);
