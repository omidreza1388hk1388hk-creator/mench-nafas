-- Phase 9: shared memories album / timeline for a direct conversation.
-- memory_date is the day the memory is ABOUT (TEXT 'YYYY-MM-DD', no timezone
-- conversion); the optional photo is an ordinary image attachment of the
-- same conversation, so the existing authenticated attachment endpoints
-- serve it and membership checks apply unchanged.
CREATE TABLE memories (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    author_id       UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title           TEXT NOT NULL,
    note            TEXT,
    memory_date     TEXT NOT NULL,
    attachment_id   UUID REFERENCES attachments(id) ON DELETE SET NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX memories_conversation_idx ON memories (conversation_id, memory_date DESC, created_at DESC);
