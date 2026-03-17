package com.macroflow.dto;

import java.util.List;

/**
 * Response envelope for {@code GET /api/v1/foods?q=}.
 *
 * <p>The {@code partial} flag is set to {@code true} when one or both external
 * food APIs (Open Food Facts, USDA FoodData Central) failed or timed out during
 * the fan-out search. In that case, {@code foods} contains results from whichever
 * source(s) responded successfully. The mobile client should surface a subtle
 * indicator when {@code partial = true} so the user knows results may be incomplete.
 */
public record FoodSearchResponseDto(
        List<FoodDto> foods,
        /** {@code true} when at least one external API failed or timed out. */
        boolean partial
) {}
