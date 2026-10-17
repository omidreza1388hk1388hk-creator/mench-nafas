-- Phase 11: pinned message, daily question answers, shared wishlist.

-- One pinned message per conversation (shared by everyone in it).
ALTER TABLE conversations ADD COLUMN pinned_message_id UUID REFERENCES messages(id) ON DELETE SET NULL;

-- "Question of the day": each person answers once per day; the question itself
-- is derived from the date, so only the answers are stored.
CREATE TABLE daily_answers (
    conversation_id UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    question_date   TEXT NOT NULL,
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    answer          TEXT NOT NULL,
    answered_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (conversation_id, question_date, user_id)
);

-- Things to do together.
CREATE TABLE wishlist_items (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    author_id       UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title           TEXT NOT NULL,
    done            BOOLEAN NOT NULL DEFAULT FALSE,
    done_at         TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX wishlist_conversation_idx ON wishlist_items (conversation_id, done, created_at DESC);
