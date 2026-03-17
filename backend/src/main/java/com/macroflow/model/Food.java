package com.macroflow.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * JPA entity for the {@code food} table.
 *
 * <p>Represents a food item that can originate from three sources:
 * <ul>
 *   <li>{@code CUSTOM} — created by a specific user; {@code ownerId} is set.</li>
 *   <li>{@code OFF} — sourced from Open Food Facts; {@code ownerId} is null.</li>
 *   <li>{@code USDA} — sourced from USDA FoodData Central; {@code ownerId} is null.</li>
 * </ul>
 *
 * <p><strong>Delete semantics:</strong> when a user deletes a custom food, the row is
 * NOT hard-deleted because {@code meal_entry} holds a FK to {@code food(id)} and we
 * must preserve historical macro snapshots. Instead, the service layer "disowns" the
 * food by setting {@code ownerId = null} and {@code isCustom = false}. The food row
 * remains in the DB but is no longer visible as a custom food to any user.
 */
@Entity
@Table(name = "food")
public class Food {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * The user who created this food. {@code null} for global/shared foods (OFF, USDA).
     * Set to {@code null} when a custom food is "disowned" on delete.
     */
    @Column(name = "owner_id")
    private UUID ownerId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "calories", nullable = false, precision = 7, scale = 2)
    private BigDecimal calories;

    @Column(name = "protein_g", nullable = false, precision = 6, scale = 2)
    private BigDecimal proteinG;

    @Column(name = "carbs_g", nullable = false, precision = 6, scale = 2)
    private BigDecimal carbsG;

    @Column(name = "fat_g", nullable = false, precision = 6, scale = 2)
    private BigDecimal fatG;

    @Column(name = "serving_g", nullable = false, precision = 6, scale = 2)
    private BigDecimal servingG;

    /** {@code true} while the food is owned by a user; set to {@code false} on disown. */
    @Column(name = "is_custom", nullable = false)
    private Boolean isCustom;

    /** One of "CUSTOM", "OFF", or "USDA". */
    @Column(name = "source", nullable = false)
    private String source;

    /** Original ID from the source API (barcode for OFF, fdcId for USDA). Null for custom foods. */
    @Column(name = "external_id")
    private String externalId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public Food() {}

    @PrePersist
    private void prePersist() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }

    // --- Getters ---

    public UUID getId() { return id; }
    public UUID getOwnerId() { return ownerId; }
    public String getName() { return name; }
    public BigDecimal getCalories() { return calories; }
    public BigDecimal getProteinG() { return proteinG; }
    public BigDecimal getCarbsG() { return carbsG; }
    public BigDecimal getFatG() { return fatG; }
    public BigDecimal getServingG() { return servingG; }
    public Boolean getIsCustom() { return isCustom; }
    public String getSource() { return source; }
    public String getExternalId() { return externalId; }
    public OffsetDateTime getCreatedAt() { return createdAt; }

    // --- Setters (used by service layer) ---

    public void setOwnerId(UUID ownerId) { this.ownerId = ownerId; }
    public void setName(String name) { this.name = name; }
    public void setCalories(BigDecimal calories) { this.calories = calories; }
    public void setProteinG(BigDecimal proteinG) { this.proteinG = proteinG; }
    public void setCarbsG(BigDecimal carbsG) { this.carbsG = carbsG; }
    public void setFatG(BigDecimal fatG) { this.fatG = fatG; }
    public void setServingG(BigDecimal servingG) { this.servingG = servingG; }
    public void setIsCustom(Boolean isCustom) { this.isCustom = isCustom; }
    public void setSource(String source) { this.source = source; }
    public void setExternalId(String externalId) { this.externalId = externalId; }
}
