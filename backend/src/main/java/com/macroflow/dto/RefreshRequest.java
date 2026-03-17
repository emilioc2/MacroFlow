package com.macroflow.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for POST /auth/refresh.
 * The client sends the opaque refresh token stored in expo-secure-store.
 * The backend validates, rotates, and returns a new access + refresh pair.
 */
public record RefreshRequest(
    @NotBlank(message = "refreshToken must not be blank")
    String refreshToken
) {}
