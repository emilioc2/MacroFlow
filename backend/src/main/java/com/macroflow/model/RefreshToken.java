package com.macroflow.model;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * JPA entity for {@code refresh_token}.
 *
 * Refresh tokens are opaque UUIDs. They are rotated on every use: the old token
 * is marked revoked and a new one is issued atomically. Also revoked on sign-out
 * and account deletion.
 */
@Entity
@Table(name = "refresh_token")
public class RefreshToken {

    /** The opaque token value — a UUID string used as the primary key. */
    @Id
    @Column(name = "token", nullable = false, updatable = false)
    private String token;

    /** The user this token belongs to. Cascade delete keeps the table clean. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private AppUser user;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    /** True once the token has been used (rotation) or the user has signed out. */
    @Column(name = "revoked", nullable = false)
    private boolean revoked = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected RefreshToken() {}

    public RefreshToken(String token, AppUser user, Instant expiresAt) {
        this.token = token;
        this.user = user;
        this.expiresAt = expiresAt;
    }

    public String getToken() { return token; }
    public AppUser getUser() { return user; }
    public Instant getExpiresAt() { return expiresAt; }
    public boolean isRevoked() { return revoked; }
    public void setRevoked(boolean revoked) { this.revoked = revoked; }
    public Instant getCreatedAt() { return createdAt; }

    /** True when the token is past its expiry time. */
    public boolean isExpired() { return Instant.now().isAfter(expiresAt); }
}
