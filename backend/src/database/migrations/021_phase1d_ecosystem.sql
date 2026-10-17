-- Phase 1D: moderation, referrals + rewards, MENCH Plus entitlements, sticker store,
-- saved messages (personal cloud), reminders / tasks / events.

-- ===== Moderation =====
CREATE TABLE reports (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reporter_id      UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    target_user_id   UUID REFERENCES users(id) ON DELETE SET NULL,
    conversation_id  UUID REFERENCES conversations(id) ON DELETE SET NULL,
    message_id       UUID REFERENCES messages(id) ON DELETE SET NULL,
    category         TEXT NOT NULL CHECK (category IN ('spam','harassment','violence','sexual','scam','impersonation','other')),
    details          TEXT,
    status           TEXT NOT NULL DEFAULT 'open' CHECK (status IN ('open','reviewing','actioned','dismissed')),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved_at      TIMESTAMPTZ,
    resolved_by      TEXT
);
CREATE INDEX idx_reports_status ON reports(status, created_at DESC);
CREATE INDEX idx_reports_target ON reports(target_user_id);
-- One open report per (reporter, message) prevents report-spamming a single message.
CREATE UNIQUE INDEX idx_reports_dedupe ON reports(reporter_id, message_id) WHERE message_id IS NOT NULL;

CREATE TABLE report_notes (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    report_id  UUID NOT NULL REFERENCES reports(id) ON DELETE CASCADE,
    author     TEXT NOT NULL,
    note       TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE user_restrictions (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    kind        TEXT NOT NULL CHECK (kind IN ('mute','suspend')),
    reason      TEXT NOT NULL,
    -- 'manual:<actor>' or 'rule:<rule key>' so every automatic action is explainable.
    source      TEXT NOT NULL,
    confidence  REAL,
    expires_at  TIMESTAMPTZ,
    lifted_at   TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_restrictions_user ON user_restrictions(user_id, created_at DESC);

CREATE TABLE appeals (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    restriction_id UUID NOT NULL REFERENCES user_restrictions(id) ON DELETE CASCADE,
    user_id        UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    message        TEXT NOT NULL,
    status         TEXT NOT NULL DEFAULT 'open' CHECK (status IN ('open','upheld','overturned')),
    resolved_by    TEXT,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved_at    TIMESTAMPTZ
);
CREATE UNIQUE INDEX idx_appeals_one_open ON appeals(restriction_id) WHERE status = 'open';

CREATE TABLE moderation_rules (
    key              TEXT PRIMARY KEY,
    enabled          BOOLEAN NOT NULL DEFAULT TRUE,
    threshold        INTEGER NOT NULL,
    window_seconds   INTEGER NOT NULL,
    action_hours     INTEGER NOT NULL DEFAULT 1,
    description      TEXT
);
INSERT INTO moderation_rules (key, enabled, threshold, window_seconds, action_hours, description) VALUES
  ('flood',          TRUE, 30, 60, 1, 'More than 30 messages in 60 seconds'),
  ('repeat_message', TRUE, 6,  300, 2, 'The same message text sent 6 times in 5 minutes');

CREATE TABLE moderation_audit (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor       TEXT NOT NULL,
    action      TEXT NOT NULL,
    target_type TEXT NOT NULL,
    target_id   TEXT,
    detail      JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_created ON moderation_audit(created_at DESC);

-- ===== Referrals =====
CREATE TABLE referral_config (
    id                  BOOLEAN PRIMARY KEY DEFAULT TRUE CHECK (id),
    enabled             BOOLEAN NOT NULL DEFAULT FALSE,
    qualify_messages    INTEGER NOT NULL DEFAULT 20,
    qualify_conversations INTEGER NOT NULL DEFAULT 2,
    reward_plus_days    INTEGER NOT NULL DEFAULT 7,
    max_rewards_per_user INTEGER NOT NULL DEFAULT 10,
    max_account_age_days INTEGER NOT NULL DEFAULT 7
);
INSERT INTO referral_config (id) VALUES (TRUE);

CREATE TABLE referrals (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    inviter_id   UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    invitee_id   UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    status       TEXT NOT NULL DEFAULT 'pending' CHECK (status IN ('pending','qualified','rewarded','rejected')),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    qualified_at TIMESTAMPTZ
);
CREATE INDEX idx_referrals_inviter ON referrals(inviter_id);

-- ===== MENCH Plus =====
CREATE TABLE entitlements (
    user_id    UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    plan       TEXT NOT NULL DEFAULT 'plus',
    expires_at TIMESTAMPTZ NOT NULL,
    source     TEXT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE redeem_codes (
    code        TEXT PRIMARY KEY,
    days        INTEGER NOT NULL CHECK (days BETWEEN 1 AND 3650),
    max_uses    INTEGER NOT NULL DEFAULT 1,
    used_count  INTEGER NOT NULL DEFAULT 0,
    expires_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE redeem_uses (
    code     TEXT NOT NULL REFERENCES redeem_codes(code) ON DELETE CASCADE,
    user_id  UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    used_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (code, user_id)
);

CREATE TABLE sticker_packs (
    id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name      TEXT NOT NULL,
    premium   BOOLEAN NOT NULL DEFAULT FALSE,
    active    BOOLEAN NOT NULL DEFAULT TRUE,
    sort      INTEGER NOT NULL DEFAULT 0,
    -- Stickers are glyph strings (large emoji / emoji combos) until an image pipeline exists.
    glyphs    TEXT[] NOT NULL DEFAULT '{}'
);

-- ===== Saved messages (personal cloud) =====
CREATE TABLE saved_items (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id              UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    kind                 TEXT NOT NULL CHECK (kind IN ('text','image','file','audio','video','link','note')),
    body                 TEXT,
    attachment_id        UUID REFERENCES attachments(id) ON DELETE SET NULL,
    source_conversation_id UUID REFERENCES conversations(id) ON DELETE SET NULL,
    source_message_id    UUID REFERENCES messages(id) ON DELETE SET NULL,
    source_sender_name   TEXT,
    source_created_at    TIMESTAMPTZ,
    folder               TEXT,
    tags                 TEXT[] NOT NULL DEFAULT '{}',
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_saved_user ON saved_items(user_id, created_at DESC);
CREATE UNIQUE INDEX idx_saved_dedupe ON saved_items(user_id, source_message_id) WHERE source_message_id IS NOT NULL;

-- ===== Reminders / tasks / events =====
CREATE TABLE planner_items (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    kind              TEXT NOT NULL DEFAULT 'reminder' CHECK (kind IN ('reminder','task','event')),
    title             TEXT NOT NULL,
    note              TEXT,
    due_at            TIMESTAMPTZ,
    repeat            TEXT NOT NULL DEFAULT 'none' CHECK (repeat IN ('none','daily','weekly','monthly')),
    remind_minutes_before INTEGER NOT NULL DEFAULT 0,
    location          TEXT,
    checklist         JSONB NOT NULL DEFAULT '[]'::jsonb,
    conversation_id   UUID REFERENCES conversations(id) ON DELETE SET NULL,
    message_id        UUID REFERENCES messages(id) ON DELETE SET NULL,
    completed_at      TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_planner_user_due ON planner_items(user_id, due_at);
