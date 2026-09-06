-- Phase 6: push notifications + group realtime completion.
--
-- Push tokens live on `devices` (one row per device already exists —
-- see 001_init.sql) rather than a separate table: a device re-registering
-- its token is just an overwrite of its own row, and there is exactly one
-- current token per device, never more. No new table needed for that.
--
-- notification_privacy_mode lives on `users` (no separate settings table
-- exists yet in this schema) and controls how much a push notification's
-- payload reveals when the recipient's device is locked/backgrounded —
-- see NotificationsService.buildPayload for where this is read.

ALTER TABLE devices
    ADD COLUMN push_token             TEXT,
    ADD COLUMN push_provider          TEXT CHECK (push_provider IN ('fcm')),
    ADD COLUMN push_token_updated_at  TIMESTAMPTZ;

ALTER TABLE users
    ADD COLUMN notification_privacy_mode TEXT
        NOT NULL DEFAULT 'full_content'
        CHECK (notification_privacy_mode IN ('full_content', 'sender_only', 'hide_content'));
