-- Phase 3: shared photo album (with reactions) and optional photos on letters and stories.
-- Photos are ordinary image attachments of the same conversation, served by the existing authenticated attachment endpoints.
CREATE TABLE album_items (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    author_id       UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    attachment_id   UUID NOT NULL REFERENCES attachments(id) ON DELETE CASCADE,
    caption         TEXT NOT NULL DEFAULT '',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX album_items_conversation_idx ON album_items (conversation_id, created_at DESC);

CREATE TABLE album_reactions (
    item_id UUID NOT NULL REFERENCES album_items(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    emoji   TEXT NOT NULL,
    PRIMARY KEY (item_id, user_id)
);

ALTER TABLE love_letters ADD COLUMN attachment_id UUID REFERENCES attachments(id) ON DELETE SET NULL;
ALTER TABLE conversation_stories ADD COLUMN attachment_id UUID REFERENCES attachments(id) ON DELETE SET NULL;
