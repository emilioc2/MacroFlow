package com.macroflow.repository;

import com.macroflow.model.SavedMeal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link SavedMeal} persistence.
 *
 * <p>All queries scope to {@code user_id} so a user can only access their own saved meals.
 * Items are loaded eagerly via the entity mapping — no separate item queries needed.
 */
public interface SavedMealRepository extends JpaRepository<SavedMeal, UUID> {

    /** All saved meals owned by the given user, ordered by creation time descending. */
    List<SavedMeal> findByUserIdOrderByCreatedAtDesc(UUID userId);

    /**
     * Find a saved meal by ID scoped to the given user.
     * Returns empty if the meal doesn't exist or belongs to a different user.
     */
    Optional<SavedMeal> findByIdAndUserId(UUID id, UUID userId);
}
