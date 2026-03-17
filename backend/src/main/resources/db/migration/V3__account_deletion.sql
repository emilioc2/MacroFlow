-- V3__account_deletion.sql
-- Adds soft-delete scheduling to app_user (Task 3.4).
-- NULL means no deletion is scheduled; a non-null timestamp means deletion is pending within 30 days.
-- Actual data removal is handled by a background job — this column marks the intent only.

ALTER TABLE app_user
    ADD COLUMN deletion_scheduled_at TIMESTAMPTZ;
