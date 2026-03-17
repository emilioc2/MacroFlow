package com.macroflow.service;

import com.macroflow.dto.AuthResponse;
import com.macroflow.model.AppUser;
import com.macroflow.model.RefreshToken;
import com.macroflow.repository.AppUserRepository;
import com.macroflow.repository.RefreshTokenRepository;
import com.macroflow.security.JwtService;
import com.macroflow.security.ProviderTokenVerifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Business logic for all auth operations: token exchange, refresh, revocation,
 * and account deletion scheduling.
 *
 * This service assumes the caller has already validated request DTOs. It never
 * returns JPA entities — all responses use DTOs.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final AppUserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final Map<String, ProviderTokenVerifier> verifiers;
    private final long refreshTokenExpirySeconds;

    public AuthService(
            AppUserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            JwtService jwtService,
            List<ProviderTokenVerifier> verifierList,
            @Value("${app.jwt.refresh-token-expiry-seconds}") long refreshTokenExpirySeconds) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtService = jwtService;
        // Index verifiers by provider name for O(1) lookup
        this.verifiers = verifierList.stream()
                .collect(Collectors.toMap(ProviderTokenVerifier::provider, Function.identity()));
        this.refreshTokenExpirySeconds = refreshTokenExpirySeconds;
    }

    /**
     * Exchange a provider ID token for a MacroFlow access + refresh token pair.
     *
     * Verifies the provider token, creates or looks up the app_user by
     * (provider, providerSub), then issues tokens.
     *
     * @throws BadCredentialsException if the provider is unsupported or the token is invalid
     */
    @Transactional
    public AuthResponse exchangeProviderToken(String provider, String providerToken) {
        ProviderTokenVerifier verifier = verifiers.get(provider.toLowerCase());
        if (verifier == null) {
            throw new BadCredentialsException("Unsupported provider: " + provider);
        }

        ProviderTokenVerifier.ProviderClaims claims;
        try {
            claims = verifier.verify(providerToken);
        } catch (ProviderTokenVerifier.ProviderVerificationException e) {
            throw new BadCredentialsException("Provider token verification failed", e);
        }

        // Create or look up the user — idempotent on repeated sign-ins
        AppUser user = userRepository
                .findByProviderAndProviderSub(provider.toLowerCase(), claims.sub())
                .orElseGet(() -> {
                    log.info("Creating new user for provider={}", provider);
                    return userRepository.save(new AppUser(provider.toLowerCase(), claims.sub()));
                });

        return issueTokenPair(user);
    }

    /**
     * Validate a refresh token, rotate it, and return a new access + refresh pair.
     *
     * Rotation: the old token is revoked and a new one is persisted atomically.
     *
     * @throws BadCredentialsException if the token is not found, expired, or revoked
     */
    @Transactional
    public AuthResponse refreshTokens(String rawRefreshToken) {
        RefreshToken stored = refreshTokenRepository.findById(rawRefreshToken)
                .orElseThrow(() -> new BadCredentialsException("Refresh token not found"));

        if (stored.isRevoked() || stored.isExpired()) {
            throw new BadCredentialsException("Refresh token is expired or revoked");
        }

        // Revoke the old token before issuing a new one (rotation)
        stored.setRevoked(true);
        refreshTokenRepository.save(stored);

        return issueTokenPair(stored.getUser());
    }

    /**
     * Revoke a refresh token — called on sign-out.
     * No-op if the token is already revoked or not found (idempotent).
     */
    @Transactional
    public void revokeRefreshToken(String rawRefreshToken) {
        refreshTokenRepository.findById(rawRefreshToken).ifPresent(token -> {
            token.setRevoked(true);
            refreshTokenRepository.save(token);
        });
    }

    /**
     * Schedule account deletion for the authenticated user.
     * Sets {@code deletion_scheduled_at} to now; actual removal happens within 30 days.
     * Also revokes all active refresh tokens to terminate all sessions immediately.
     */
    @Transactional
    public void scheduleAccountDeletion(UUID userId) {
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("User not found: " + userId));

        user.setDeletionScheduledAt(Instant.now());
        userRepository.save(user);

        // Terminate all active sessions immediately
        refreshTokenRepository.revokeAllByUserId(userId);
        log.info("Account deletion scheduled for userId={}", userId);
    }

    /** Issue a fresh access JWT and a new opaque refresh token for the given user. */
    private AuthResponse issueTokenPair(AppUser user) {
        String accessToken = jwtService.generateAccessToken(user.getId(), user.getProvider());

        String rawRefreshToken = UUID.randomUUID().toString();
        Instant expiresAt = Instant.now().plusSeconds(refreshTokenExpirySeconds);
        refreshTokenRepository.save(new RefreshToken(rawRefreshToken, user, expiresAt));

        return new AuthResponse(accessToken, rawRefreshToken);
    }
}
