package com.macroflow.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;

/**
 * A single data point in the monthly calorie series returned by
 * {@code GET /api/v1/analytics/monthly}.
 *
 * <p>Each entry represents the total calories logged on a given date.
 * Dates with no entries are omitted from the series — the client is
 * responsible for filling gaps with zero when rendering the chart.
 */
public record DailyCaloriesDto(

        /** The calendar date this data point represents. */
        @JsonProperty("log_date")
        LocalDate logDate,

        /** Total calories logged across all entries on this date. */
        @JsonProperty("total_calories")
        double totalCalories
) {}
