package com.macroflow.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for {@code app_user}.
 *
 * Users are identified by their provider + provider_sub pair — no username/password.
 * The {@code deletion_scheduled_at} column is set when the user requests account deletion;
 * actual removal happens within 30 days via a background job.
 */
@Entity
@Table(name = "app_user")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** Authentication provider: {@code apple} or {@code google}. */
    @Column(name = "provider", nullable = false)
    private String provider;

    /** Subject claim from the provider's ID token — unique per provider. */
    @Column(name = "provider_sub", nullable = false)
    private String providerSub;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    /**
     * Non-null when the user has requested account deletion.
     * The background job checks this column and removes data within 30 days.
     */
    @Column(name = "deletion_scheduled_at")
    private Instant deletionScheduledAt;

    protected AppUser() {}

    public AppUser(String provider, String providerSub) {
        this.provider = provider;
        this.providerSub = providerSub;
    }

    public UUID getId() { return id; }
    public String getProvider() { return provider; }
    public String getProviderSub() { return providerSub; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getDeletionScheduledAt() { return deletionScheduledAt; }
    public void setDeletionScheduledAt(Instant deletionScheduledAt) {
        this.deletionScheduledAt = deletionScheduledAt;
    }
}
