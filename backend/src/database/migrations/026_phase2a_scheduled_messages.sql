-- Phase 2A: "send later". A scheduled message waits here and a worker sends it through the normal
-- MessagesService.send() path at send_at (so it is delivered, notified and numbered like any other message).
CREATE TABLE scheduled_messages (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id  UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    sender_id        UUID NOT NULL REFERENCES users(id),
    client_msg_id    UUID NOT NULL,
    body             TEXT NOT NULL,
    silent           BOOLEAN NOT NULL DEFAULT FALSE,
    send_at          TIMESTAMPTZ NOT NULL,
    status           TEXT NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'sending', 'sent', 'failed')),
    error            TEXT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    sent_at          TIMESTAMPTZ,
    UNIQUE (conversation_id, sender_id, client_msg_id)
);
CREATE INDEX scheduled_messages_due_idx ON scheduled_messages (send_at) WHERE status = 'pending';
CREATE INDEX scheduled_messages_owner_idx ON scheduled_messages (conversation_id, sender_id) WHERE status = 'pending';
