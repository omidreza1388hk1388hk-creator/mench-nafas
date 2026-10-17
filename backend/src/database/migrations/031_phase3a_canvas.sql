-- Phase 3A: one shared drawing canvas per couple. Strokes are appended atomically; clearing removes them all.
CREATE TABLE couple_canvas (
    conversation_id UUID PRIMARY KEY REFERENCES conversations(id) ON DELETE CASCADE,
    strokes         JSONB NOT NULL DEFAULT '[]'::jsonb,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
