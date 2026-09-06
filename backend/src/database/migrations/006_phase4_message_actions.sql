-- Phase 4: message edit / delete / forward / reactions.
--
-- messages.edited_at and messages.deleted_at already existed since
-- Phase 1's schema (see 001_init.sql's comment on why those columns were
-- added early) — nothing to add there. This migration adds:
--   1. forwarded_from_message_id, so a forwarded message can point back
--      at its origin (client renders a "Forwarded" label).
--   2. message_reactions, a normalized table rather than a JSON column on
--      messages — reactions are added/removed far more often than a
--      message body is edited, and a normalized table lets concurrent
--      reactors write without racing on a single row's JSON blob.

ALTER TABLE messages
    ADD COLUMN forwarded_from_message_id UUID REFERENCES messages(id);

-- One active reaction per (message, user) — matches the product
-- decision in the client UI (tapping a new emoji replaces your previous
-- reaction on that message rather than stacking). Aggregation into
-- per-emoji counts happens at query time (see
-- MessagesRepository.listReactionsForMessages).
CREATE TABLE message_reactions (
    message_id      UUID NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    emoji           TEXT NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (message_id, user_id)
);
CREATE INDEX idx_message_reactions_message_id ON message_reactions(message_id);
