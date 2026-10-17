-- Phase 3A: shared counters for a couple.
-- kind 'date'  = days since / until anchor_date (the app picks the direction)
-- kind 'tally' = a number both people can bump (+1 / -1), e.g. "times we hugged"
CREATE TABLE couple_counters (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    author_id       UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title           TEXT NOT NULL,
    emoji           TEXT NOT NULL DEFAULT '💗',
    kind            TEXT NOT NULL CHECK (kind IN ('date', 'tally')),
    anchor_date     TEXT,
    tally_count     INTEGER NOT NULL DEFAULT 0 CHECK (tally_count >= 0),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX couple_counters_conversation_idx ON couple_counters (conversation_id, created_at);
