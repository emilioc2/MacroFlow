package com.macroflow.controller;

import com.macroflow.dto.MealEntryPageDto;
import com.macroflow.dto.SyncOperationDto;
import com.macroflow.dto.SyncResultDto;
import com.macroflow.service.MealService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Thin controller for meal entry and sync endpoints.
 *
 * <p>All business logic lives in {@link MealService}. This controller only handles
 * request validation, parameter binding, and principal extraction.
 *
 * <p>Ownership is enforced implicitly: {@code userId} is always extracted from the
 * JWT principal — never from the request body or path.
 *
 * <p>Base paths: {@code /api/v1/meal-entries}, {@code /api/v1/sync}
 */
@RestController
public class MealEntryController {

    private final MealService mealService;

    public MealEntryController(MealService mealService) {
        this.mealService = mealService;
    }

    /**
     * GET /api/v1/meal-entries?startDate=&endDate=&page=&pageSize=
     *
     * <p>Returns paginated non-deleted entries for the authenticated user within the
     * given inclusive date range. Dates must be in ISO-8601 format (yyyy-MM-dd).
     *
     * @param userId    the authenticated user's UUID, injected from the JWT principal
     * @param startDate inclusive start date
     * @param endDate   inclusive end date
     * @param page      zero-based page number (default 0)
     * @param pageSize  entries per page (default 50; capped at 100 in the service)
     */
    @GetMapping("/api/v1/meal-entries")
    public ResponseEntity<MealEntryPageDto> getMealEntries(
            @AuthenticationPrincipal UUID userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int pageSize) {
        return ResponseEntity.ok(mealService.getMealEntries(userId, startDate, endDate, page, pageSize));
    }

    /**
     * POST /api/v1/sync/batch
     *
     * <p>Idempotent batch upsert/delete for meal entries. Operations are processed in
     * the order they appear in the request body. Each operation must have a
     * client-generated {@code clientId} as its idempotency key.
     *
     * <p>Returns one result per operation in the same order. HTTP 200 is always
     * returned — per-operation errors are reported in the result body, not as HTTP errors.
     *
     * @param userId     the authenticated user's UUID, injected from the JWT principal
     * @param operations the ordered list of sync operations
     */
    @PostMapping("/api/v1/sync/batch")
    public ResponseEntity<List<SyncResultDto>> syncBatch(
            @AuthenticationPrincipal UUID userId,
            @RequestBody @NotEmpty @Valid List<SyncOperationDto> operations) {
        return ResponseEntity.ok(mealService.processBatch(userId, operations));
    }
}
