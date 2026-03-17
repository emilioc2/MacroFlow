package com.macroflow.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Read-only DTO returned by {@code GET /api/v1/meal-entries}.
 *
 * <p>Macro values are snapshots captured at log time — they do not change if the
 * underlying food is later edited or disowned. The {@code clientId} is included so
 * the mobile client can reconcile server responses with its local SQLite state.
 */
public record MealEntryDto(
        UUID id,

        @JsonProperty("food_id")
        UUID foodId,

        /** Food name denormalised from the food row at query time for display convenience. */
        @JsonProperty("food_name")
        String foodName,

        @JsonProperty("log_date")
        LocalDate logDate,

        @JsonProperty("logged_at")
        Instant loggedAt,

        @JsonProperty("meal_name")
        String mealName,

        double servings,

        @JsonProperty("protein_g")
        double proteinG,

        @JsonProperty("carbs_g")
        double carbsG,

        @JsonProperty("fat_g")
        double fatG,

        double calories,

        /** Client-generated idempotency key; used by the mobile client for local reconciliation. */
        @JsonProperty("client_id")
        String clientId,

        @JsonProperty("client_ts")
        Instant clientTs,

        boolean deleted
) {}
