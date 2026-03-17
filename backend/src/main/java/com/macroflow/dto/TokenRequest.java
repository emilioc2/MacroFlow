package com.macroflow.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for POST /auth/token.
 * The client sends the ID token from Apple or Google; the backend verifies it
 * with the provider and issues a MacroFlow access + refresh token pair.
 */
public record TokenRequest(
    @NotBlank(message = "provider must not be blank")
    String provider,
    @NotBlank(message = "providerToken must not be blank")
    String providerToken
) {}
