-- Phase 2: real-time messaging support.
-- Adds what Phase 1's schema deliberately left out until there was an
-- actual messaging feature to design against: a denormalized
-- last-message timestamp for ordering the conversation list without an
-- extra join, and per-user read cursors.

ALTER TABLE conversations ADD COLUMN last_message_at TIMESTAMPTZ;

-- Phase 1's conversation_members PK is (conversation_id, user_id), which
-- doesn't help "find all conversations for this user" — that query scans
-- by user_id, so it needs its own index.
CREATE INDEX idx_conversation_members_user_id ON conversation_members(user_id);

-- One row per (conversation, user): the highest message `sequence` that
-- user has read. Simpler and cheaper than a per-message read-receipt row
-- per recipient, and sufficient for Phase 2's read state (a single
-- "read up to here" cursor, same model most messengers use for 1:1 chats).
CREATE TABLE read_receipts (
    conversation_id     UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    user_id             UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    last_read_sequence  BIGINT NOT NULL DEFAULT 0,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (conversation_id, user_id)
);
