package com.macroflow.repository;

import com.macroflow.model.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link UserProfile}.
 *
 * <p>The PK is the user's UUID (same as {@code app_user.id}), so the standard
 * {@code findById} works for ownership-scoped lookups. The named method is provided
 * for readability at the call site.
 */
public interface UserProfileRepository extends JpaRepository<UserProfile, UUID> {

    /**
     * Find a user's profile by their UUID.
     *
     * @param userId the authenticated user's UUID from the JWT principal
     * @return the profile, or empty if the user has not yet created one
     */
    Optional<UserProfile> findByUserId(UUID userId);
}
