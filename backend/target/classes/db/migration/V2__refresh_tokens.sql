-- V2__refresh_tokens.sql
-- Opaque refresh tokens for JWT rotation (Task 3).
-- Each token is a UUID stored here; the access token is a short-lived JWT held in memory only.
-- Tokens are rotated on every use — the old token is revoked before a new one is issued.

CREATE TABLE refresh_token (
    token       TEXT PRIMARY KEY,
    user_id     UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked     BOOLEAN NOT NULL DEFAULT false,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Supports efficient lookup of all tokens for a given user (e.g. bulk revocation on account deletion)
CREATE INDEX idx_refresh_token_user_id ON refresh_token(user_id);
