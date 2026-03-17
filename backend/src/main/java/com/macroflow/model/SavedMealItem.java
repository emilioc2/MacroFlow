package com.macroflow.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * JPA entity for the {@code saved_meal_item} table.
 *
 * <p>Each row represents one food item within a {@link SavedMeal}, along with the
 * number of servings to use when the saved meal is re-logged. The food reference is
 * intentionally a plain UUID column (not a JPA {@code @ManyToOne}) to avoid eager
 * loading the full {@link Food} graph when only the ID is needed for sync.
 */
@Entity
@Table(name = "saved_meal_item")
public class SavedMealItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * Back-reference to the owning {@link SavedMeal}. Mapped by the {@code saved_meal_id}
     * FK column; cascade and orphan removal are managed on the parent side.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "saved_meal_id", nullable = false)
    private SavedMeal savedMeal;

    /** FK to the food item. Stored as a plain UUID to avoid loading the full Food entity. */
    @Column(name = "food_id", nullable = false)
    private UUID foodId;

    @Column(name = "servings", nullable = false, precision = 6, scale = 2)
    private BigDecimal servings;

    public SavedMealItem() {}

    // --- Getters ---

    public UUID getId() { return id; }
    public SavedMeal getSavedMeal() { return savedMeal; }
    public UUID getFoodId() { return foodId; }
    public BigDecimal getServings() { return servings; }

    // --- Setters ---

    public void setSavedMeal(SavedMeal savedMeal) { this.savedMeal = savedMeal; }
    public void setFoodId(UUID foodId) { this.foodId = foodId; }
    public void setServings(BigDecimal servings) { this.servings = servings; }
}
