-- Phase 3: the couple's private vault. The server only ever stores an opaque encrypted blob ("v1:" + base64) that the two phones
-- encrypt and decrypt with a passphrase the server never sees. Losing the passphrase means the notes cannot be recovered.
CREATE TABLE couple_vault_items (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    author_id       UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    blob            TEXT NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX couple_vault_items_conversation_idx ON couple_vault_items (conversation_id, created_at);
