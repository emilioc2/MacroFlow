package com.macroflow.controller;

import com.macroflow.dto.SavedMealDto;
import com.macroflow.dto.SavedMealRequest;
import com.macroflow.service.MealService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Thin controller for saved meal CRUD endpoints.
 *
 * <p>All business logic lives in {@link MealService}. This controller only handles
 * request validation and principal extraction.
 *
 * <p>Ownership is enforced implicitly: {@code userId} is always extracted from the
 * JWT principal — never from the request body or path.
 *
 * <p>Base path: {@code /api/v1/saved-meals}
 */
@RestController
@RequestMapping("/api/v1/saved-meals")
public class SavedMealController {

    private final MealService mealService;

    public SavedMealController(MealService mealService) {
        this.mealService = mealService;
    }

    /**
     * GET /api/v1/saved-meals
     *
     * <p>Returns all saved meals owned by the authenticated user, ordered by creation
     * time descending (most recently created first).
     *
     * @param userId the authenticated user's UUID, injected from the JWT principal
     */
    @GetMapping
    public ResponseEntity<List<SavedMealDto>> getSavedMeals(@AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(mealService.getSavedMeals(userId));
    }

    /**
     * POST /api/v1/saved-meals
     *
     * <p>Creates a new saved meal owned by the authenticated user. The request body
     * is validated with {@code @Valid}; any constraint violation returns 422.
     *
     * @param userId the authenticated user's UUID, injected from the JWT principal
     * @param req    the validated saved meal payload
     */
    @PostMapping
    public ResponseEntity<SavedMealDto> createSavedMeal(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody SavedMealRequest req) {
        return ResponseEntity.status(201).body(mealService.createSavedMeal(userId, req));
    }

    /**
     * PUT /api/v1/saved-meals/{id}
     *
     * <p>Updates an existing saved meal. Returns 404 if the meal doesn't exist or
     * belongs to a different user (intentionally vague to avoid leaking IDs).
     *
     * @param userId the authenticated user's UUID, injected from the JWT principal
     * @param id     the saved meal's UUID
     * @param req    the validated updated payload
     */
    @PutMapping("/{id}")
    public ResponseEntity<SavedMealDto> updateSavedMeal(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID id,
            @Valid @RequestBody SavedMealRequest req) {
        return ResponseEntity.ok(mealService.updateSavedMeal(userId, id, req));
    }

    /**
     * DELETE /api/v1/saved-meals/{id}
     *
     * <p>Deletes a saved meal and its items. Previously logged meal entries are
     * unaffected. Returns 404 if the meal doesn't exist or belongs to a different user.
     *
     * @param userId the authenticated user's UUID, injected from the JWT principal
     * @param id     the saved meal's UUID
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSavedMeal(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID id) {
        mealService.deleteSavedMeal(userId, id);
        return ResponseEntity.noContent().build();
    }
}
