-- Phase 9 (romantic layer): shared "us" dates on a direct conversation.
-- Stored as TEXT 'YYYY-MM-DD' (validated by the DTO) rather than DATE so
-- node-postgres never converts them through the server's timezone.
ALTER TABLE conversations ADD COLUMN together_since TEXT;
ALTER TABLE conversations ADD COLUMN next_meet_date TEXT;
