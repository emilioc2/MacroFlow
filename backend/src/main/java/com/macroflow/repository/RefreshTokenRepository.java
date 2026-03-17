package com.macroflow.repository;

import com.macroflow.model.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.UUID;

/**
 * Spring Data JPA repository for {@link RefreshToken}.
 */
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, String> {

    /**
     * Bulk-revoke all active tokens for a user.
     * Used on account deletion to invalidate all sessions immediately.
     */
    @Modifying
    @Query("UPDATE RefreshToken rt SET rt.revoked = true WHERE rt.user.id = :userId AND rt.revoked = false")
    void revokeAllByUserId(UUID userId);
}
