-- Phase 2A: polls. A poll lives in its own tables; the chat message that shows it is an ordinary
-- text message whose body is the marker "poll:v1:<poll id>" (sent through the normal outbox path).
CREATE TABLE polls (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id  UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    creator_id       UUID NOT NULL REFERENCES users(id),
    question         TEXT NOT NULL,
    multiple         BOOLEAN NOT NULL DEFAULT FALSE,
    closed_at        TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX polls_conversation_idx ON polls (conversation_id);

CREATE TABLE poll_options (
    id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    poll_id   UUID NOT NULL REFERENCES polls(id) ON DELETE CASCADE,
    position  INTEGER NOT NULL,
    text      TEXT NOT NULL,
    UNIQUE (poll_id, position)
);

-- One row per (option, voter). Single-choice polls are enforced in PollsService (replace-all per poll).
CREATE TABLE poll_votes (
    poll_id    UUID NOT NULL REFERENCES polls(id) ON DELETE CASCADE,
    option_id  UUID NOT NULL REFERENCES poll_options(id) ON DELETE CASCADE,
    user_id    UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (option_id, user_id)
);
CREATE INDEX poll_votes_poll_user_idx ON poll_votes (poll_id, user_id);
