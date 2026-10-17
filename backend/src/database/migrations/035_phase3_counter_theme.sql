-- Phase 3A: each counter card can have its own gradient theme (index into the client's palette list).
ALTER TABLE couple_counters ADD COLUMN theme SMALLINT NOT NULL DEFAULT 0 CHECK (theme BETWEEN 0 AND 5);
