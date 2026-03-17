package com.macroflow.controller;

import com.macroflow.dto.DailyCaloriesDto;
import com.macroflow.dto.WeeklyAnalyticsDto;
import com.macroflow.service.AnalyticsService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for analytics endpoints.
 *
 * <p>All endpoints require a valid JWT. The authenticated user's UUID is injected
 * via {@code @AuthenticationPrincipal} — no user ID is accepted from the request
 * body or path to prevent IDOR.
 *
 * <p>This controller is intentionally thin: it delegates all computation to
 * {@link AnalyticsService}.
 */
@RestController
@RequestMapping("/api/v1/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    /**
     * Return weekly analytics for the authenticated user.
     *
     * <p>Computes averages and adherence over the most recent 7-day window ending
     * today in the user's stored timezone.
     *
     * @param userId injected from the JWT principal
     * @return 200 with {@link WeeklyAnalyticsDto}; 401 if unauthenticated
     */
    @GetMapping("/weekly")
    public ResponseEntity<WeeklyAnalyticsDto> getWeeklyAnalytics(
            @AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(analyticsService.getWeeklyAnalytics(userId));
    }

    /**
     * Return the daily calorie series for the most recent 30-day window.
     *
     * <p>Only dates with at least one logged entry are included in the response.
     * The client fills gaps with zero when rendering the chart.
     *
     * @param userId injected from the JWT principal
     * @return 200 with list of {@link DailyCaloriesDto}; 401 if unauthenticated
     */
    @GetMapping("/monthly")
    public ResponseEntity<List<DailyCaloriesDto>> getMonthlyAnalytics(
            @AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(analyticsService.getMonthlyAnalytics(userId));
    }
}
