package com.macroflow.dto;

/**
 * Response body for POST /auth/token and POST /auth/refresh.
 * The access token is a short-lived JWT (15 min) held in memory only.
 * The refresh token is an opaque UUID (30 days) stored in expo-secure-store.
 */
public record AuthResponse(
    String accessToken,
    String refreshToken
) {}
