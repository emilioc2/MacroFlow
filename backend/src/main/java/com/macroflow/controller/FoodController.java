package com.macroflow.controller;

import com.macroflow.dto.CustomFoodRequest;
import com.macroflow.dto.FoodDto;
import com.macroflow.dto.FoodSearchResponseDto;
import com.macroflow.service.FoodSearchService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Thin controller for food search and custom food CRUD endpoints.
 *
 * <p>All business logic — fan-out search, deduplication, ownership checks, and
 * custom food persistence — lives in {@link FoodSearchService}. This controller
 * only handles request validation and principal extraction.
 *
 * <p>Ownership is enforced implicitly: the {@code userId} is always extracted from
 * the JWT principal set by the security filter. It is never read from the request
 * body or path, so a user can only mutate their own custom foods.
 *
 * <p>Base path: /api/v1/foods
 */
@RestController
@RequestMapping("/api/v1/foods")
@Validated
public class FoodController {

    private final FoodSearchService foodSearchService;

    public FoodController(FoodSearchService foodSearchService) {
        this.foodSearchService = foodSearchService;
    }

    /**
     * GET /api/v1/foods?q={query}
     *
     * <p>Fans out to Open Food Facts and USDA FoodData Central in parallel, normalises
     * both result sets to {@link FoodDto}, deduplicates, and returns a unified list.
     * If either external API fails or times out, results from the responding source are
     * returned with {@code partial: true}.
     *
     * @param q      the search term; must not be blank
     * @param userId the authenticated user's UUID, injected from the JWT principal
     */
    @GetMapping
    public ResponseEntity<FoodSearchResponseDto> searchFoods(
            @RequestParam @NotBlank(message = "q must not be blank") String q,
            @AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(foodSearchService.searchFoods(q, userId));
    }

    /**
     * POST /api/v1/foods/custom
     *
     * <p>Creates a new custom food owned by the authenticated user.
     * Returns 201 Created with the saved {@link FoodDto}.
     *
     * @param req    validated request body
     * @param userId the authenticated user's UUID, injected from the JWT principal
     */
    @PostMapping("/custom")
    public ResponseEntity<FoodDto> createCustomFood(
            @Valid @RequestBody CustomFoodRequest req,
            @AuthenticationPrincipal UUID userId) {
        FoodDto created = foodSearchService.createCustomFood(userId, req);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * PUT /api/v1/foods/custom/{id}
     *
     * <p>Updates an existing custom food. Returns 404 if the food does not exist or
     * belongs to a different user (ownership enforced in the service layer).
     *
     * @param id     the food's UUID from the path
     * @param req    validated request body
     * @param userId the authenticated user's UUID, injected from the JWT principal
     */
    @PutMapping("/custom/{id}")
    public ResponseEntity<FoodDto> updateCustomFood(
            @PathVariable UUID id,
            @Valid @RequestBody CustomFoodRequest req,
            @AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(foodSearchService.updateCustomFood(userId, id, req));
    }

    /**
     * DELETE /api/v1/foods/custom/{id}
     *
     * <p>Disowns the custom food (sets owner_id = null, is_custom = false) rather than
     * hard-deleting the row, to preserve FK integrity with meal_entry. Returns 204 No Content.
     * Returns 404 if the food does not exist or belongs to a different user.
     *
     * @param id     the food's UUID from the path
     * @param userId the authenticated user's UUID, injected from the JWT principal
     */
    @DeleteMapping("/custom/{id}")
    public ResponseEntity<Void> deleteCustomFood(
            @PathVariable UUID id,
            @AuthenticationPrincipal UUID userId) {
        foodSearchService.deleteCustomFood(userId, id);
        return ResponseEntity.noContent().build();
    }
}
