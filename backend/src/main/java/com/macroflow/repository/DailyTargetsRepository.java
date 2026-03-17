package com.macroflow.repository;

import com.macroflow.model.DailyTargets;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link DailyTargets} persistence.
 *
 * <p>The PK is the user's UUID, so {@code findById} works for ownership-scoped
 * lookups. The named method is provided for readability at the call site.
 */
public interface DailyTargetsRepository extends JpaRepository<DailyTargets, UUID> {

    /**
     * Find a user's daily targets by their UUID.
     *
     * @param userId the authenticated user's UUID from the JWT principal
     * @return the targets, or empty if the user has not yet set them
     */
    Optional<DailyTargets> findByUserId(UUID userId);
}
