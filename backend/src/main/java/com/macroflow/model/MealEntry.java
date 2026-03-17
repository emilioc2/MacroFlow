package com.macroflow.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * JPA entity for the {@code meal_entry} table.
 *
 * <p>Each row represents a single food item logged by a user on a given date.
 * Macro values (protein_g, carbs_g, fat_g, calories) are stored as a snapshot
 * at log time so that edits to the underlying {@link Food} row do not retroactively
 * alter historical entries.
 *
 * <p><strong>Soft delete:</strong> entries are never hard-deleted. The {@code deleted}
 * flag is set to {@code true} on removal; the sync batch endpoint propagates this to
 * the server. Queries always filter {@code deleted = false}.
 *
 * <p><strong>Idempotency:</strong> {@code client_id} is a client-generated UUID used
 * as an idempotency key for the batch sync endpoint. The UNIQUE constraint on
 * {@code client_id} ensures duplicate sync operations are safe to replay.
 *
 * <p><strong>Conflict resolution:</strong> when two sync operations target the same
 * {@code client_id}, the one with the later {@code client_ts} wins.
 */
@Entity
@Table(name = "meal_entry")
public class MealEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** The user who owns this entry. Ownership is enforced in the service layer. */
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** FK to the food item that was logged. Never null — even if the food is later disowned. */
    @Column(name = "food_id", nullable = false)
    private UUID foodId;

    /** The calendar date the user logged this entry against (in the user's local timezone). */
    @Column(name = "log_date", nullable = false)
    private LocalDate logDate;

    /** Server-side timestamp of when the entry was persisted. */
    @Column(name = "logged_at", nullable = false)
    private Instant loggedAt;

    /** Optional meal slot label (e.g. "Breakfast", "Lunch"). Null means unspecified. */
    @Column(name = "meal_name")
    private String mealName;

    /** Number of servings logged; multiplied against the food's per-serving macros. */
    @Column(name = "servings", nullable = false, precision = 6, scale = 2)
    private BigDecimal servings;

    /** Macro snapshot at log time — independent of any future edits to the food row. */
    @Column(name = "protein_g", nullable = false, precision = 6, scale = 2)
    private BigDecimal proteinG;

    @Column(name = "carbs_g", nullable = false, precision = 6, scale = 2)
    private BigDecimal carbsG;

    @Column(name = "fat_g", nullable = false, precision = 6, scale = 2)
    private BigDecimal fatG;

    @Column(name = "calories", nullable = false, precision = 7, scale = 2)
    private BigDecimal calories;

    /**
     * Client-generated UUID used as an idempotency key for the sync batch endpoint.
     * The UNIQUE constraint on this column makes duplicate sync replays safe.
     */
    @Column(name = "client_id", nullable = false, unique = true)
    private String clientId;

    /**
     * Client-side timestamp used for conflict resolution: when two operations target
     * the same {@code client_id}, the one with the later {@code client_ts} wins.
     */
    @Column(name = "client_ts", nullable = false)
    private Instant clientTs;

    /**
     * Soft-delete flag. {@code true} means the entry has been removed by the user.
     * All queries must filter {@code deleted = false} to exclude removed entries.
     */
    @Column(name = "deleted", nullable = false)
    private boolean deleted = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public MealEntry() {}

    @PrePersist
    private void prePersist() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (loggedAt == null) loggedAt = Instant.now();
    }

    @PreUpdate
    private void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    // --- Getters ---

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public UUID getFoodId() { return foodId; }
    public LocalDate getLogDate() { return logDate; }
    public Instant getLoggedAt() { return loggedAt; }
    public String getMealName() { return mealName; }
    public BigDecimal getServings() { return servings; }
    public BigDecimal getProteinG() { return proteinG; }
    public BigDecimal getCarbsG() { return carbsG; }
    public BigDecimal getFatG() { return fatG; }
    public BigDecimal getCalories() { return calories; }
    public String getClientId() { return clientId; }
    public Instant getClientTs() { return clientTs; }
    public boolean isDeleted() { return deleted; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }

    // --- Setters ---

    public void setUserId(UUID userId) { this.userId = userId; }
    public void setFoodId(UUID foodId) { this.foodId = foodId; }
    public void setLogDate(LocalDate logDate) { this.logDate = logDate; }
    public void setLoggedAt(Instant loggedAt) { this.loggedAt = loggedAt; }
    public void setMealName(String mealName) { this.mealName = mealName; }
    public void setServings(BigDecimal servings) { this.servings = servings; }
    public void setProteinG(BigDecimal proteinG) { this.proteinG = proteinG; }
    public void setCarbsG(BigDecimal carbsG) { this.carbsG = carbsG; }
    public void setFatG(BigDecimal fatG) { this.fatG = fatG; }
    public void setCalories(BigDecimal calories) { this.calories = calories; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    public void setClientTs(Instant clientTs) { this.clientTs = clientTs; }
    public void setDeleted(boolean deleted) { this.deleted = deleted; }
}
