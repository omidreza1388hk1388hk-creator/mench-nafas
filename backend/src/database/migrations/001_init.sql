-- MENCH Phase 1 schema.
-- Designed so later phases (reactions, attachments, calls, read receipts,
-- presence, sync cursors) can be added via new migrations without
-- altering these primary keys or relationships.

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    phone_e164      TEXT NOT NULL UNIQUE,
    display_name    TEXT,
    username        TEXT UNIQUE,
    avatar_url      TEXT,
    bio             TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE devices (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    device_name     TEXT NOT NULL,
    platform        TEXT NOT NULL CHECK (platform IN ('android', 'ios', 'web', 'desktop')),
    public_key      TEXT,              -- reserved for Phase-5 E2EE key registration
    last_seen_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    revoked_at      TIMESTAMPTZ
);
CREATE INDEX idx_devices_user_id ON devices(user_id);

CREATE TABLE sessions (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    device_id           UUID NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    refresh_token_hash  TEXT NOT NULL,
    issued_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at          TIMESTAMPTZ NOT NULL,
    revoked_at          TIMESTAMPTZ,
    replaced_by_id      UUID REFERENCES sessions(id)
);
CREATE INDEX idx_sessions_user_id ON sessions(user_id);
CREATE INDEX idx_sessions_device_id ON sessions(device_id);
CREATE UNIQUE INDEX idx_sessions_token_hash ON sessions(refresh_token_hash);

CREATE TABLE otp_challenges (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    phone_e164          TEXT NOT NULL,
    code_hash           TEXT NOT NULL,
    attempt_count       INT NOT NULL DEFAULT 0,
    max_attempts        INT NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at          TIMESTAMPTZ NOT NULL,
    consumed_at         TIMESTAMPTZ
);
CREATE INDEX idx_otp_phone ON otp_challenges(phone_e164, created_at DESC);

CREATE TABLE conversations (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    kind            TEXT NOT NULL CHECK (kind IN ('direct', 'group')) DEFAULT 'direct',
    created_by      UUID REFERENCES users(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE conversation_members (
    conversation_id UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role            TEXT NOT NULL CHECK (role IN ('owner', 'member')) DEFAULT 'member',
    joined_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (conversation_id, user_id)
);

-- messages table exists in Phase 1 schema so the FK/index shape is fixed
-- early, but no message-sending endpoints are implemented until Phase 2.
CREATE TABLE messages (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    sender_id       UUID NOT NULL REFERENCES users(id),
    client_msg_id   UUID NOT NULL,        -- for idempotent send / duplicate protection
    kind            TEXT NOT NULL DEFAULT 'text',
    body            TEXT,
    sequence        BIGSERIAL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    edited_at       TIMESTAMPTZ,
    deleted_at      TIMESTAMPTZ
);
CREATE INDEX idx_messages_conversation_id ON messages(conversation_id, sequence);
CREATE UNIQUE INDEX idx_messages_dedupe ON messages(conversation_id, sender_id, client_msg_id);
