package com.macroflow.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO for a saved meal and its items.
 * Returned by all {@code /api/v1/saved-meals} endpoints.
 */
public record SavedMealDto(
        UUID id,
        String name,

        @JsonProperty("created_at")
        OffsetDateTime createdAt,

        List<SavedMealItemDto> items
) {}
