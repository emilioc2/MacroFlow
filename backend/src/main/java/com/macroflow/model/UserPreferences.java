package com.macroflow.model;

import jakarta.persistence.*;

import java.util.UUID;

/**
 * JPA entity for the {@code user_preferences} table.
 *
 * <p>The primary key ({@code user_id}) is also a foreign key to {@code app_user(id)}.
 * One preferences row per user; created on first PUT and updated on subsequent PUTs
 * (upsert semantics in the service layer).
 *
 * <p>Note: the {@code user_preferences} table has no {@code updated_at} column —
 * the schema tracks only the three preference values.
 */
@Entity
@Table(name = "user_preferences")
public class UserPreferences {

    /**
     * Same UUID as the owning {@code app_user.id}.
     * Plain UUID PK — FK constraint is enforced at the DB level by Flyway.
     */
    @Id
    @Column(name = "user_id", updatable = false, nullable = false)
    private UUID userId;

    /** Maximum number of recently-logged foods to retain; default 10. */
    @Column(name = "recently_logged_max", nullable = false)
    private Integer recentlyLoggedMax;

    /** Whether the onboarding tutorial has been shown; default false. */
    @Column(name = "tutorial_shown", nullable = false)
    private Boolean tutorialShown;

    /** UI theme — one of: light | dark | system; default "system". */
    @Column(name = "theme", nullable = false)
    private String theme;

    protected UserPreferences() {}

    public UserPreferences(UUID userId) {
        this.userId = userId;
    }

    // --- Getters ---

    public UUID getUserId() { return userId; }
    public Integer getRecentlyLoggedMax() { return recentlyLoggedMax; }
    public Boolean getTutorialShown() { return tutorialShown; }
    public String getTheme() { return theme; }

    // --- Setters (used by service layer during upsert) ---

    public void setRecentlyLoggedMax(Integer recentlyLoggedMax) { this.recentlyLoggedMax = recentlyLoggedMax; }
    public void setTutorialShown(Boolean tutorialShown) { this.tutorialShown = tutorialShown; }
    public void setTheme(String theme) { this.theme = theme; }
}
