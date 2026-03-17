package com.macroflow.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for {@code POST /api/v1/foods/custom} and {@code PUT /api/v1/foods/custom/{id}}.
 *
 * <p>All fields are required. Macro values must be non-negative; serving size must be
 * at least 1 gram so that per-serving calculations are always meaningful (avoids
 * division-by-zero when scaling macros by portion).
 *
 * <p>Bean Validation annotations are evaluated by the controller's {@code @Valid} binding
 * before the request reaches the service layer.
 */
public record CustomFoodRequest(
        @NotBlank(message = "name must not be blank")
        String name,

        @NotNull(message = "calories is required")
        @Min(value = 0, message = "calories must be >= 0")
        Double calories,

        @NotNull(message = "protein_g is required")
        @Min(value = 0, message = "protein_g must be >= 0")
        @JsonProperty("protein_g") Double proteinG,

        @NotNull(message = "carbs_g is required")
        @Min(value = 0, message = "carbs_g must be >= 0")
        @JsonProperty("carbs_g") Double carbsG,

        @NotNull(message = "fat_g is required")
        @Min(value = 0, message = "fat_g must be >= 0")
        @JsonProperty("fat_g") Double fatG,

        // Minimum 1g so portion-scaling math never divides by zero
        @NotNull(message = "serving_g is required")
        @Min(value = 1, message = "serving_g must be >= 1")
        @JsonProperty("serving_g") Double servingG
) {}
