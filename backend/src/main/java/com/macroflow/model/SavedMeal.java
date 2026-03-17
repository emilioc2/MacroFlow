package com.macroflow.model;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * JPA entity for the {@code saved_meal} table.
 *
 * <p>A saved meal is a named collection of food items that a user can quickly re-log.
 * The items are stored in {@link SavedMealItem}; this entity holds only the meal's
 * metadata (name, owner, creation timestamp).
 *
 * <p>Ownership is enforced in the service layer — a user can only read, update, or
 * delete their own saved meals.
 *
 * <p>Deleting a saved meal cascades to its items (ON DELETE CASCADE in the schema),
 * but does NOT affect any previously logged {@code meal_entry} rows.
 */
@Entity
@Table(name = "saved_meal")
public class SavedMeal {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** The user who owns this saved meal. */
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    /**
     * Items belonging to this saved meal. Loaded eagerly because the list is always
     * needed when returning a saved meal to the client — avoids N+1 queries.
     */
    @OneToMany(mappedBy = "savedMeal", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<SavedMealItem> items = new ArrayList<>();

    public SavedMeal() {}

    @PrePersist
    private void prePersist() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }

    // --- Getters ---

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getName() { return name; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public List<SavedMealItem> getItems() { return items; }

    // --- Setters ---

    public void setUserId(UUID userId) { this.userId = userId; }
    public void setName(String name) { this.name = name; }
    public void setItems(List<SavedMealItem> items) { this.items = items; }
}
