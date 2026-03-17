package com.macroflow.controller;

import com.macroflow.dto.*;
import com.macroflow.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Thin controller for auth endpoints. All business logic lives in {@link AuthService}.
 *
 * Base path: /api/v1/auth
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * POST /api/v1/auth/token
     * Exchange an Apple or Google provider token for a MacroFlow access + refresh token pair.
     */
    @PostMapping("/token")
    public ResponseEntity<AuthResponse> token(@Valid @RequestBody TokenRequest request) {
        AuthResponse response = authService.exchangeProviderToken(
                request.provider(), request.providerToken());
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/v1/auth/refresh
     * Validate and rotate a refresh token; return a new access + refresh pair.
     */
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        AuthResponse response = authService.refreshTokens(request.refreshToken());
        return ResponseEntity.ok(response);
    }

    /**
     * DELETE /api/v1/auth/session
     * Revoke a refresh token on sign-out. Returns 204 No Content.
     */
    @DeleteMapping("/session")
    public ResponseEntity<Void> revokeSession(@Valid @RequestBody RevokeRequest request) {
        authService.revokeRefreshToken(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    /**
     * DELETE /api/v1/auth/account
     * Schedule account deletion for the authenticated user. Returns 202 Accepted.
     * Requires a valid JWT — the userId is extracted from the security context.
     */
    @DeleteMapping("/account")
    public ResponseEntity<AccountDeletionResponse> deleteAccount(
            @AuthenticationPrincipal UUID userId) {
        authService.scheduleAccountDeletion(userId);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new AccountDeletionResponse(
                        "Account deletion scheduled. Your data will be removed within 30 days."));
    }
}
