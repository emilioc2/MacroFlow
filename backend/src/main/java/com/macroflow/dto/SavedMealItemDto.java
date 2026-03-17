package com.macroflow.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

/**
 * DTO for a single item within a saved meal.
 * Returned as part of {@link SavedMealDto}; never exposes the JPA entity directly.
 */
public record SavedMealItemDto(
        UUID id,

        @JsonProperty("food_id")
        UUID foodId,

        double servings
) {}
