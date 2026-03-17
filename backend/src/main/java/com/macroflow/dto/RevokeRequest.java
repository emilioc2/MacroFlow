package com.macroflow.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for DELETE /auth/session.
 * The client sends the refresh token to revoke on sign-out.
 * The access token is short-lived and memory-only, so only the refresh token
 * needs explicit server-side revocation.
 */
public record RevokeRequest(
    @NotBlank(message = "refreshToken must not be blank")
    String refreshToken
) {}
