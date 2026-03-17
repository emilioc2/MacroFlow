package com.macroflow.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Paginated response wrapper for {@code GET /api/v1/meal-entries}.
 *
 * <p>Pagination is offset-based — the client queries by date range and page number.
 * {@code totalElements} lets the client know the full result count without fetching
 * all pages upfront.
 */
public record MealEntryPageDto(
        List<MealEntryDto> content,

        @JsonProperty("total_elements")
        long totalElements,

        int page,

        @JsonProperty("page_size")
        int pageSize,

        @JsonProperty("total_pages")
        int totalPages
) {}
