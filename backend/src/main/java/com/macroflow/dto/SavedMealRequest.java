package com.macroflow.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;
import java.util.UUID;

/**
 * Request body for {@code POST /api/v1/saved-meals} and {@code PUT /api/v1/saved-meals/{id}}.
 *
 * <p>At least one item is required — a saved meal with no foods is not useful.
 * Each item's {@code servings} must be positive to prevent zero-calorie log entries.
 */
public record SavedMealRequest(
        @NotBlank
        String name,

        @NotEmpty
        @Valid
        List<Item> items
) {
    /**
     * A single food item within the saved meal request.
     *
     * @param foodId   the UUID of the food to include
     * @param servings number of servings; must be positive
     */
    public record Item(
            @NotNull
            UUID foodId,

            @NotNull
            @Positive
            double servings
    ) {}
}
