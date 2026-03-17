package com.macroflow.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * JPA entity for the {@code daily_targets} table.
 *
 * <p>Stores the user's macro and calorie targets. These are either auto-calculated
 * from the TDEE formula (when {@code isOverride = false}) or manually set by the
 * user (when {@code isOverride = true}).
 *
 * <p>The analytics service reads these targets to compute adherence percentages.
 * If no targets row exists for a user, adherence is reported as 0%.
 */
@Entity
@Table(name = "daily_targets")
public class DailyTargets {

    /**
     * Same UUID as the owning {@code app_user.id}.
     * Plain UUID PK — FK constraint enforced at the DB level by Flyway.
     */
    @Id
    @Column(name = "user_id", updatable = false, nullable = false)
    private UUID userId;

    @Column(name = "calories_kcal", nullable = false, precision = 7, scale = 2)
    private BigDecimal caloriesKcal;

    @Column(name = "protein_g", nullable = false, precision = 6, scale = 2)
    private BigDecimal proteinG;

    @Column(name = "carbs_g", nullable = false, precision = 6, scale = 2)
    private BigDecimal carbsG;

    @Column(name = "fat_g", nullable = false, precision = 6, scale = 2)
    private BigDecimal fatG;

    /**
     * {@code true} when the user has manually overridden the auto-calculated targets.
     * {@code false} means the values were derived from the TDEE formula.
     */
    @Column(name = "is_override", nullable = false)
    private boolean isOverride = false;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected DailyTargets() {}

    public DailyTargets(UUID userId) {
        this.userId = userId;
    }

    // --- Getters ---

    public UUID getUserId() { return userId; }
    public BigDecimal getCaloriesKcal() { return caloriesKcal; }
    public BigDecimal getProteinG() { return proteinG; }
    public BigDecimal getCarbsG() { return carbsG; }
    public BigDecimal getFatG() { return fatG; }
    public boolean isOverride() { return isOverride; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }

    // --- Setters ---

    public void setCaloriesKcal(BigDecimal caloriesKcal) { this.caloriesKcal = caloriesKcal; }
    public void setProteinG(BigDecimal proteinG) { this.proteinG = proteinG; }
    public void setCarbsG(BigDecimal carbsG) { this.carbsG = carbsG; }
    public void setFatG(BigDecimal fatG) { this.fatG = fatG; }
    public void setOverride(boolean override) { isOverride = override; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
