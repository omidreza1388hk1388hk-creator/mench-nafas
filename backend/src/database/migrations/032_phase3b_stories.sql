-- Phase 3B: 24-hour text stories inside a conversation. Expired rows are simply ignored by queries (and can be purged later).
CREATE TABLE conversation_stories (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    author_id       UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    body            TEXT NOT NULL,
    emoji           TEXT NOT NULL DEFAULT '',
    palette         SMALLINT NOT NULL DEFAULT 0 CHECK (palette BETWEEN 0 AND 5),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at      TIMESTAMPTZ NOT NULL DEFAULT (now() + interval '24 hours')
);
CREATE INDEX conversation_stories_active_idx ON conversation_stories (conversation_id, expires_at);

CREATE TABLE conversation_story_views (
    story_id  UUID NOT NULL REFERENCES conversation_stories(id) ON DELETE CASCADE,
    user_id   UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    viewed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (story_id, user_id)
);
