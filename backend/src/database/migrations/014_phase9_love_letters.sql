-- Phase 9: "open when..." letters. The title (e.g. "open when you miss me")
-- is visible to the recipient from the start; the body is only returned by
-- the API after the recipient opens it (and never before unlock_at).
CREATE TABLE love_letters (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    author_id       UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title           TEXT NOT NULL,
    body            TEXT NOT NULL,
    unlock_at       TIMESTAMPTZ,
    opened_at       TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX love_letters_conversation_idx ON love_letters (conversation_id, created_at DESC);
