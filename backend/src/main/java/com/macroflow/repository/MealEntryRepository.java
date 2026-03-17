package com.macroflow.repository;

import com.macroflow.model.MealEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link MealEntry} persistence.
 *
 * <p>All queries filter on {@code user_id} to enforce ownership at the data layer.
 * Soft-deleted entries ({@code deleted = true}) are excluded from read queries but
 * retained in the DB to preserve historical macro snapshots.
 */
public interface MealEntryRepository extends JpaRepository<MealEntry, UUID> {

    /**
     * Paginated list of non-deleted entries for a user within an inclusive date range.
     * Uses the composite index {@code idx_meal_entry_user_date} for efficient filtering.
     */
    @Query("SELECT e FROM MealEntry e WHERE e.userId = :userId " +
           "AND e.logDate BETWEEN :startDate AND :endDate " +
           "AND e.deleted = false " +
           "ORDER BY e.logDate ASC, e.loggedAt ASC")
    Page<MealEntry> findByUserIdAndDateRange(
            @Param("userId") UUID userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            Pageable pageable);

    /**
     * Look up an entry by its client-generated idempotency key.
     * Used by the sync batch endpoint to detect existing entries before upsert.
     */
    Optional<MealEntry> findByClientId(String clientId);

    /**
     * Return all non-deleted entries for a user within an inclusive date range,
     * ordered by date ascending. Used by the analytics service to aggregate
     * daily totals without pagination overhead.
     */
    @Query("SELECT e FROM MealEntry e WHERE e.userId = :userId " +
           "AND e.logDate BETWEEN :startDate AND :endDate " +
           "AND e.deleted = false " +
           "ORDER BY e.logDate ASC")
    List<MealEntry> findAllByUserIdAndDateRange(
            @Param("userId") UUID userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);
}
