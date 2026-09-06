-- Phase 6: universal search.
--
-- Message bodies (and titles/names) are mostly Persian, so a Postgres
-- 'english'/'simple' tsvector text-search config (word-stemming, English
-- stopwords) buys nothing and can actively hurt matching. pg_trgm's
-- trigram similarity works purely on substrings, language-agnostic, and
-- backs a plain `ILIKE '%q%'` with a real index instead of a sequential
-- scan — exactly what SearchRepository's queries use (see
-- search.repository.ts).

CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Message body search. Partial index: deleted messages are never
-- searchable (deleted_at IS NOT NULL rows keep body around server-side
-- per messages.deleted_at's existing semantics, but they're excluded from
-- every search query — see SearchRepository.searchMessages), so indexing
-- them would only waste space.
CREATE INDEX idx_messages_body_trgm ON messages
    USING GIN (body gin_trgm_ops)
    WHERE deleted_at IS NULL AND body IS NOT NULL;

-- Group titles ("chats" search scope).
CREATE INDEX idx_conversations_title_trgm ON conversations
    USING GIN (title gin_trgm_ops)
    WHERE title IS NOT NULL;

-- Direct-conversation display names / usernames ("chats" search scope
-- also matches on the other member's name, not just group titles).
CREATE INDEX idx_users_display_name_trgm ON users
    USING GIN (display_name gin_trgm_ops)
    WHERE display_name IS NOT NULL;
CREATE INDEX idx_users_username_trgm ON users
    USING GIN (username gin_trgm_ops)
    WHERE username IS NOT NULL;

-- File name search ("files" search scope).
CREATE INDEX idx_attachments_filename_trgm ON attachments
    USING GIN (original_filename gin_trgm_ops);
