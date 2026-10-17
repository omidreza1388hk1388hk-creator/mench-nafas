-- Phase 9: "today's mood" — one emoji (+ short text) per member, shown to
-- the partner for the day it was set. Per-member columns, like mute/pin.
ALTER TABLE conversation_members ADD COLUMN mood_emoji TEXT;
ALTER TABLE conversation_members ADD COLUMN mood_text TEXT;
ALTER TABLE conversation_members ADD COLUMN mood_set_at TIMESTAMPTZ;
