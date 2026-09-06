-- MENCH Phase 6 schema: real-time voice/video calls (WebRTC signaling).
--
-- Scope: 1:1 calls only, over an existing DIRECT conversation. Group
-- calling is explicitly out of scope for this phase (see
-- CallsService.initiate's guard) — call_participants is still a separate
-- table rather than two nullable columns on `calls`, so a future group-
-- calling phase can add rows to it without a schema migration, matching
-- the spec's CallParticipants entity (section 8) even though only two
-- rows are ever written today.
--
-- No SDP offers/answers/ICE candidates are stored anywhere: that data is
-- purely ephemeral signaling, relayed live over the /ws gateway
-- (see ChatGateway's call.offer/call.answer/call.ice-candidate handling)
-- and never persisted, matching the media/attachments precedent of never
-- storing more than is needed.

CREATE TABLE calls (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    caller_id       UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    callee_id       UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    call_type       TEXT NOT NULL CHECK (call_type IN ('voice', 'video')),
    -- ringing  -> caller has initiated, callee has not yet responded
    -- active   -> callee accepted; answered_at is set
    -- ended    -> was active, then hung up by either party
    -- declined -> callee explicitly rejected while ringing
    -- missed   -> was still ringing when it ended (caller cancelled, or
    --             it timed out client-side) — never answered
    status          TEXT NOT NULL DEFAULT 'ringing'
                        CHECK (status IN ('ringing', 'active', 'ended', 'declined', 'missed')),
    end_reason      TEXT,
    started_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    answered_at     TIMESTAMPTZ,
    ended_at        TIMESTAMPTZ,

    CONSTRAINT calls_caller_not_callee CHECK (caller_id != callee_id)
);
CREATE INDEX idx_calls_conversation_id ON calls(conversation_id);
CREATE INDEX idx_calls_caller_id ON calls(caller_id);
CREATE INDEX idx_calls_callee_id ON calls(callee_id);
-- Call history is always "calls involving me", ordered newest first —
-- this composite index serves CallsRepository.listHistoryForUser's UNION
-- of caller_id/callee_id without a sequential scan as history grows.
CREATE INDEX idx_calls_caller_started ON calls(caller_id, started_at DESC);
CREATE INDEX idx_calls_callee_started ON calls(callee_id, started_at DESC);

CREATE TABLE call_participants (
    call_id     UUID NOT NULL REFERENCES calls(id) ON DELETE CASCADE,
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    joined_at   TIMESTAMPTZ,
    left_at     TIMESTAMPTZ,
    PRIMARY KEY (call_id, user_id)
);
