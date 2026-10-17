-- Phase 1D: QR-code login for Web / Desktop clients.
-- The QR carries (id, nonce); the polling page keeps (secret), which never appears in the QR, so
-- photographing a QR code is not enough to steal the session. Only hashes are stored.
CREATE TABLE web_login_challenges (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    secret_hash   TEXT NOT NULL,
    nonce_hash    TEXT NOT NULL,
    platform      TEXT NOT NULL CHECK (platform IN ('web', 'desktop')),
    device_name   TEXT NOT NULL,
    status        TEXT NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'approved', 'rejected', 'consumed')),
    user_id       UUID REFERENCES users(id) ON DELETE CASCADE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at    TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_web_login_expires ON web_login_challenges(expires_at);
