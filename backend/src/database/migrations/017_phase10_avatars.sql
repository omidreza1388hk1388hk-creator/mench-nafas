-- Phase 10: profile photos. The image itself lives in the StorageProvider at
-- avatars/<user_id>.jpg (overwritten on change); avatar_version is a
-- millisecond timestamp clients put in the URL (?v=) so a new photo busts
-- their image cache. NULL = no photo.
ALTER TABLE users ADD COLUMN avatar_version BIGINT;
