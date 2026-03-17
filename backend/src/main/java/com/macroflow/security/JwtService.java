package com.macroflow.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Handles signing and validation of MacroFlow access tokens (JWTs).
 *
 * Access tokens are short-lived (15 min by default) and signed with HMAC-SHA256
 * using the secret from {@code app.jwt.secret}. They carry the userId as the
 * {@code sub} claim and the auth provider as a custom claim.
 *
 * This service does NOT handle refresh tokens — those are opaque UUIDs managed
 * by {@link com.macroflow.service.AuthService}.
 */
@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    private final SecretKey signingKey;
    private final long accessTokenExpirySeconds;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.access-token-expiry-seconds}") long accessTokenExpirySeconds) {
        // Derive a consistent HMAC-SHA256 key from the configured secret string.
        // The secret must be at least 256 bits (32 chars) for HS256.
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpirySeconds = accessTokenExpirySeconds;
    }

    /**
     * Issue a signed access JWT for the given user.
     *
     * @param userId   the user's UUID — stored as the {@code sub} claim
     * @param provider the auth provider ({@code apple} or {@code google})
     * @return a compact JWT string
     */
    public String generateAccessToken(UUID userId, String provider) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                .claim("provider", provider)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(accessTokenExpirySeconds)))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Validate a JWT and extract its claims.
     *
     * @param token the compact JWT string from the Authorization header
     * @return the parsed claims, or {@code null} if the token is invalid or expired
     */
    public Claims validateAndExtractClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException e) {
            // Log at debug — invalid tokens are common (expired, tampered) and not errors
            log.debug("JWT validation failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Extract the userId (UUID) from a previously validated token's claims.
     */
    public UUID extractUserId(Claims claims) {
        return UUID.fromString(claims.getSubject());
    }
}
