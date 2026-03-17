package com.macroflow.repository;

import com.macroflow.model.Food;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for the {@code food} table.
 *
 * <p>The ownership-scoped lookup ({@code findByOwnerIdAndId}) is the primary guard
 * for custom food mutations — the service layer uses it to ensure a user can only
 * update or delete foods they own. If the food exists but belongs to a different
 * user, the query returns empty and the service throws 404 (intentionally vague
 * to avoid leaking whether the food ID exists at all).
 */
public interface FoodRepository extends JpaRepository<Food, UUID> {

    /**
     * Find a food that is owned by the given user.
     *
     * <p>Used by update and delete operations to enforce ownership. Returns
     * {@code Optional.empty()} if the food does not exist OR belongs to a
     * different user — the caller should treat both cases as 404.
     *
     * @param ownerId the authenticated user's UUID
     * @param id      the food's UUID
     */
    Optional<Food> findByOwnerIdAndId(UUID ownerId, UUID id);
}
