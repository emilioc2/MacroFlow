package com.macroflow.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

/**
 * Immutable DTO representing a food item in API responses.
 *
 * <p>Used for both external search results (source = "OFF" or "USDA") and
 * user-created custom foods (source = "CUSTOM"). The {@code id} field is the
 * internal UUID assigned by the backend; {@code externalId} preserves the
 * original identifier from the source API for deduplication purposes.
 *
 * <p>All macro values are per-serving (i.e. for {@code servingG} grams).
 */
public record FoodDto(
        UUID id,
        String name,
        @JsonProperty("calories") double calories,
        @JsonProperty("protein_g") double proteinG,
        @JsonProperty("carbs_g") double carbsG,
        @JsonProperty("fat_g") double fatG,
        @JsonProperty("serving_g") double servingG,
        /** One of "OFF" (Open Food Facts), "USDA", or "CUSTOM". */
        String source,
        @JsonProperty("external_id") String externalId
) {}
