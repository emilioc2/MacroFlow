package com.macroflow.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * JPA entity for the {@code user_profile} table.
 *
 * <p>The primary key ({@code user_id}) is also a foreign key to {@code app_user(id)}.
 * There is a 1-to-1 relationship: one profile per user, created on first PUT and
 * updated on subsequent PUTs (upsert semantics in the service layer).
 *
 * <p>{@code updatedAt} is set explicitly in the service layer on every upsert so the
 * returned DTO always reflects the current server time without requiring a re-fetch.
 */
@Entity
@Table(name = "user_profile")
public class UserProfile {

    /**
     * Same UUID as the owning {@code app_user.id}.
     * We use a plain UUID PK rather than a {@code @OneToOne} association to keep
     * the mapping simple — the FK constraint is enforced at the DB level by Flyway.
     */
    @Id
    @Column(name = "user_id", updatable = false, nullable = false)
    private UUID userId;

    @Column(name = "sex", nullable = false)
    private String sex;

    /**
     * Date of birth stored as a DATE column.
     * Age is derived at calculation time — never stored as a stale integer.
     */
    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;

    @Column(name = "height_cm", nullable = false, precision = 5, scale = 2)
    private BigDecimal heightCm;

    @Column(name = "weight_kg", nullable = false, precision = 5, scale = 2)
    private BigDecimal weightKg;

    /** One of: sedentary | light | moderate | active | very_active */
    @Column(name = "activity", nullable = false)
    private String activity;

    /** One of: lose | maintain | gain */
    @Column(name = "goal", nullable = false)
    private String goal;

    /** IANA timezone identifier (e.g. {@code America/New_York}); defaults to {@code UTC}. */
    @Column(name = "timezone", nullable = false)
    private String timezone;

    /** Server-managed timestamp; updated on every upsert by the service layer. */
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected UserProfile() {}

    public UserProfile(UUID userId) {
        this.userId = userId;
    }

    // --- Getters ---

    public UUID getUserId() { return userId; }
    public String getSex() { return sex; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public BigDecimal getHeightCm() { return heightCm; }
    public BigDecimal getWeightKg() { return weightKg; }
    public String getActivity() { return activity; }
    public String getGoal() { return goal; }
    public String getTimezone() { return timezone; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }

    // --- Setters (used by service layer during upsert) ---

    public void setSex(String sex) { this.sex = sex; }
    public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }
    public void setHeightCm(BigDecimal heightCm) { this.heightCm = heightCm; }
    public void setWeightKg(BigDecimal weightKg) { this.weightKg = weightKg; }
    public void setActivity(String activity) { this.activity = activity; }
    public void setGoal(String goal) { this.goal = goal; }
    public void setTimezone(String timezone) { this.timezone = timezone; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
