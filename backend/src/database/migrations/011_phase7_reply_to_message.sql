-- Phase 7: reply-to-message. Mirrors 006_phase4_message_actions.sql's
-- forwarded_from_message_id column exactly (nullable self-reference on
-- messages, no ON DELETE behavior specified — same as that column,
-- consistent with this schema's existing choice to let a delete leave a
-- dangling reference resolved at the application layer rather than at
-- the database layer). A reply is client-rendered as a small quoted
-- preview above the reply message body; the client already caches the
-- full message history locally (Room), so the referenced message's
-- body/sender is looked up there rather than denormalized onto this row.

ALTER TABLE messages
    ADD COLUMN reply_to_message_id UUID REFERENCES messages(id);
