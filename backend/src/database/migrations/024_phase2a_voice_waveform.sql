-- Phase 2A (voice messages): a compact amplitude waveform for audio attachments.
-- Stored as base64 text of a small byte array (the client sends ~48 samples, each
-- 0-255), so a received voice message renders its real waveform without the
-- server decoding any audio. Nullable: voice messages sent by older app versions
-- (and every non-audio attachment) simply have none, and the client falls back
-- to a neutral waveform. Base64 is capped at 256 chars (192 raw bytes) by the DTO.
ALTER TABLE attachments ADD COLUMN IF NOT EXISTS waveform TEXT;
