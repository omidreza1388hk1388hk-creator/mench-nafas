-- Phase 2A: group roles, permissions, slow mode and invite links.
-- A "channel" is simply a group whose send_permission is 'admins' (broadcast mode); no new conversation kind.

-- Members can now be 'admin' besides 'owner' / 'member'. The original CHECK was unnamed, so find and replace it.
DO $$
DECLARE c RECORD;
BEGIN
  FOR c IN
    SELECT conname FROM pg_constraint
    WHERE conrelid = 'conversation_members'::regclass AND contype = 'c' AND pg_get_constraintdef(oid) LIKE '%role%'
  LOOP
    EXECUTE format('ALTER TABLE conversation_members DROP CONSTRAINT %I', c.conname);
  END LOOP;
END $$;
ALTER TABLE conversation_members ADD CONSTRAINT conversation_members_role_check CHECK (role IN ('owner', 'admin', 'member'));

ALTER TABLE conversations
    ADD COLUMN send_permission        TEXT NOT NULL DEFAULT 'all' CHECK (send_permission IN ('all', 'admins')),
    ADD COLUMN add_members_permission TEXT NOT NULL DEFAULT 'all' CHECK (add_members_permission IN ('all', 'admins')),
    ADD COLUMN edit_info_permission   TEXT NOT NULL DEFAULT 'all' CHECK (edit_info_permission IN ('all', 'admins')),
    ADD COLUMN pin_permission         TEXT NOT NULL DEFAULT 'all' CHECK (pin_permission IN ('all', 'admins')),
    ADD COLUMN slow_mode_seconds      INTEGER NOT NULL DEFAULT 0 CHECK (slow_mode_seconds IN (0, 10, 30, 60, 300, 900, 3600));

CREATE TABLE conversation_invites (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id  UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    code             TEXT NOT NULL UNIQUE,
    created_by       UUID NOT NULL REFERENCES users(id),
    expires_at       TIMESTAMPTZ,
    max_uses         INTEGER CHECK (max_uses IS NULL OR max_uses > 0),
    uses             INTEGER NOT NULL DEFAULT 0,
    revoked_at       TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX conversation_invites_conv_idx ON conversation_invites (conversation_id);
