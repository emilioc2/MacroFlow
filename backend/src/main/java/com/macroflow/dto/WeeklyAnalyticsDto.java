package com.macroflow.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response DTO for {@code GET /api/v1/analytics/weekly}.
 *
 * <p>All averages are computed over the most recent 7-day period ending today
 * (in the user's stored timezone). Days with no entries contribute zero to the
 * average — this is intentional so the numbers reflect actual intake, not just
 * days the user logged.
 *
 * <p>Adherence is expressed as a percentage: {@code (avg / target) * 100}, capped
 * at 200% to avoid unbounded values. A value of 100 means the user hit their target
 * exactly on average.
 *
 * <p>{@code streakCount} is the number of consecutive days ending today on which
 * the user logged at least one meal entry.
 */
public record WeeklyAnalyticsDto(

        /** Average daily calories over the 7-day window. */
        @JsonProperty("avg_calories")
        double avgCalories,

        /** Average daily protein (g) over the 7-day window. */
        @JsonProperty("avg_protein_g")
        double avgProteinG,

        /** Average daily carbs (g) over the 7-day window. */
        @JsonProperty("avg_carbs_g")
        double avgCarbsG,

        /** Average daily fat (g) over the 7-day window. */
        @JsonProperty("avg_fat_g")
        double avgFatG,

        /** Calorie adherence %: (avgCalories / targetCalories) * 100, capped at 200. */
        @JsonProperty("adherence_calories_pct")
        double adherenceCaloriesPct,

        /** Protein adherence %: (avgProteinG / targetProteinG) * 100, capped at 200. */
        @JsonProperty("adherence_protein_pct")
        double adherenceProteinPct,

        /** Carbs adherence %: (avgCarbsG / targetCarbsG) * 100, capped at 200. */
        @JsonProperty("adherence_carbs_pct")
        double adherenceCarbsPct,

        /** Fat adherence %: (avgFatG / targetFatG) * 100, capped at 200. */
        @JsonProperty("adherence_fat_pct")
        double adherenceFatPct,

        /** Number of consecutive days ending today with at least one logged entry. */
        @JsonProperty("streak_count")
        int streakCount
) {}
