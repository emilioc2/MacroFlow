package com.macroflow.repository;

import com.macroflow.model.UserPreferences;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link UserPreferences}.
 *
 * <p>The PK is the user's UUID (same as {@code app_user.id}), so the standard
 * {@code findById} works for ownership-scoped lookups. The named method is provided
 * for readability at the call site.
 */
public interface UserPreferencesRepository extends JpaRepository<UserPreferences, UUID> {

    /**
     * Find a user's preferences by their UUID.
     *
     * @param userId the authenticated user's UUID from the JWT principal
     * @return the preferences row, or empty if the user has not yet saved preferences
     */
    Optional<UserPreferences> findByUserId(UUID userId);
}
