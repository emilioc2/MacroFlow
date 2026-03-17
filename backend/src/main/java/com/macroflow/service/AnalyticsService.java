package com.macroflow.service;

import com.macroflow.dto.DailyCaloriesDto;
import com.macroflow.dto.WeeklyAnalyticsDto;
import com.macroflow.model.MealEntry;
import com.macroflow.repository.DailyTargetsRepository;
import com.macroflow.repository.MealEntryRepository;
import com.macroflow.repository.UserProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Business logic for the analytics endpoints.
 *
 * <h2>Weekly analytics</h2>
 * <p>Averages are computed over the most recent 7-day window ending today (in the
 * user's stored timezone). Days with no entries contribute zero — this reflects
 * actual intake rather than only days the user logged.
 *
 * <h2>Monthly analytics</h2>
 * <p>Returns a daily calorie series for the most recent 30 days. Dates with no
 * entries are omitted; the client fills gaps with zero when rendering the chart.
 *
 * <h2>Adherence</h2>
 * <p>Adherence % = {@code (avg / target) * 100}, capped at 200 to prevent
 * unbounded values. Returns 0 when no targets row exists for the user.
 */
@Service
public class AnalyticsService {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsService.class);

    /** Number of days in the weekly analytics window. */
    private static final int WEEKLY_WINDOW_DAYS = 7;

    /** Number of days in the monthly analytics window. */
    private static final int MONTHLY_WINDOW_DAYS = 30;

    /** Adherence is capped at 200% to prevent unbounded values in the response. */
    private static final double ADHERENCE_CAP = 200.0;

    private final MealEntryRepository mealEntryRepository;
    private final DailyTargetsRepository dailyTargetsRepository;
    private final UserProfileRepository userProfileRepository;

    public AnalyticsService(MealEntryRepository mealEntryRepository,
                            DailyTargetsRepository dailyTargetsRepository,
                            UserProfileRepository userProfileRepository) {
        this.mealEntryRepository = mealEntryRepository;
        this.dailyTargetsRepository = dailyTargetsRepository;
        this.userProfileRepository = userProfileRepository;
    }

    // -------------------------------------------------------------------------
    // 7.1 — GET /analytics/weekly
    // -------------------------------------------------------------------------

    /**
     * Compute weekly analytics for the authenticated user.
     *
     * <p>Fetches all non-deleted entries in the 7-day window, aggregates daily
     * totals, then computes averages and adherence against the user's stored targets.
     *
     * @param userId the authenticated user's UUID (from JWT principal)
     * @return weekly averages, adherence percentages, and streak count
     */
    @Transactional(readOnly = true)
    public WeeklyAnalyticsDto getWeeklyAnalytics(UUID userId) {
        ZoneId zone = resolveTimezone(userId);
        LocalDate today = LocalDate.now(zone);
        LocalDate windowStart = today.minusDays(WEEKLY_WINDOW_DAYS - 1);

        List<MealEntry> entries = mealEntryRepository
                .findAllByUserIdAndDateRange(userId, windowStart, today);

        // Aggregate per-day totals; days with no entries contribute zero
        double[] dailyCalories = new double[WEEKLY_WINDOW_DAYS];
        double[] dailyProtein  = new double[WEEKLY_WINDOW_DAYS];
        double[] dailyCarbs    = new double[WEEKLY_WINDOW_DAYS];
        double[] dailyFat      = new double[WEEKLY_WINDOW_DAYS];

        for (MealEntry e : entries) {
            int idx = (int) (e.getLogDate().toEpochDay() - windowStart.toEpochDay());
            if (idx >= 0 && idx < WEEKLY_WINDOW_DAYS) {
                dailyCalories[idx] += e.getCalories().doubleValue();
                dailyProtein[idx]  += e.getProteinG().doubleValue();
                dailyCarbs[idx]    += e.getCarbsG().doubleValue();
                dailyFat[idx]      += e.getFatG().doubleValue();
            }
        }

        double avgCalories = average(dailyCalories);
        double avgProtein  = average(dailyProtein);
        double avgCarbs    = average(dailyCarbs);
        double avgFat      = average(dailyFat);

        // Fetch targets for adherence calculation; default to 0 if not set
        double targetCalories = 0, targetProtein = 0, targetCarbs = 0, targetFat = 0;
        var targetsOpt = dailyTargetsRepository.findByUserId(userId);
        if (targetsOpt.isPresent()) {
            var t = targetsOpt.get();
            targetCalories = t.getCaloriesKcal().doubleValue();
            targetProtein  = t.getProteinG().doubleValue();
            targetCarbs    = t.getCarbsG().doubleValue();
            targetFat      = t.getFatG().doubleValue();
        } else {
            log.debug("No daily targets found for userId={}, adherence will be 0", userId);
        }

        int streak = computeStreak(entries, today, zone);

        return new WeeklyAnalyticsDto(
                round2(avgCalories),
                round2(avgProtein),
                round2(avgCarbs),
                round2(avgFat),
                round2(adherencePct(avgCalories, targetCalories)),
                round2(adherencePct(avgProtein,  targetProtein)),
                round2(adherencePct(avgCarbs,    targetCarbs)),
                round2(adherencePct(avgFat,      targetFat)),
                streak
        );
    }

    // -------------------------------------------------------------------------
    // 7.2 — GET /analytics/monthly
    // -------------------------------------------------------------------------

    /**
     * Return the daily calorie series for the most recent 30-day window.
     *
     * <p>Only dates with at least one logged entry are included. The client is
     * responsible for filling gaps with zero when rendering the chart.
     *
     * @param userId the authenticated user's UUID (from JWT principal)
     * @return list of (date, totalCalories) pairs, ordered by date ascending
     */
    @Transactional(readOnly = true)
    public List<DailyCaloriesDto> getMonthlyAnalytics(UUID userId) {
        ZoneId zone = resolveTimezone(userId);
        LocalDate today = LocalDate.now(zone);
        LocalDate windowStart = today.minusDays(MONTHLY_WINDOW_DAYS - 1);

        List<MealEntry> entries = mealEntryRepository
                .findAllByUserIdAndDateRange(userId, windowStart, today);

        // Group entries by date and sum calories per day
        Map<LocalDate, Double> dailyTotals = entries.stream()
                .collect(Collectors.groupingBy(
                        MealEntry::getLogDate,
                        Collectors.summingDouble(e -> e.getCalories().doubleValue())
                ));

        // Return sorted list — only dates that have entries
        return dailyTotals.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new DailyCaloriesDto(entry.getKey(), round2(entry.getValue())))
                .collect(Collectors.toList());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Resolve the user's stored timezone, falling back to UTC if the profile is
     * absent or the stored timezone string is invalid.
     */
    private ZoneId resolveTimezone(UUID userId) {
        return userProfileRepository.findByUserId(userId)
                .map(p -> {
                    try {
                        return ZoneId.of(p.getTimezone());
                    } catch (Exception e) {
                        log.warn("Invalid timezone '{}' for userId={}, falling back to UTC",
                                p.getTimezone(), userId);
                        return ZoneId.of("UTC");
                    }
                })
                .orElse(ZoneId.of("UTC"));
    }

    /**
     * Compute the simple arithmetic mean of an array of daily values.
     * The divisor is always the full window length (7), so days with no entries
     * contribute zero — this reflects actual intake, not just logged days.
     */
    static double average(double[] dailyValues) {
        if (dailyValues.length == 0) return 0.0;
        double sum = 0;
        for (double v : dailyValues) sum += v;
        return sum / dailyValues.length;
    }

    /**
     * Compute adherence as {@code (actual / target) * 100}, capped at 200%.
     * Returns 0 when target is zero or negative to avoid division by zero.
     */
    static double adherencePct(double actual, double target) {
        if (target <= 0) return 0.0;
        return Math.min((actual / target) * 100.0, ADHERENCE_CAP);
    }

    /**
     * Count the number of consecutive days ending today on which the user logged
     * at least one meal entry.
     *
     * <p>Iterates backwards from today; stops at the first day with no entries.
     */
    static int computeStreak(List<MealEntry> entries, LocalDate today, ZoneId zone) {
        // Build a set of dates that have at least one entry for O(1) lookup
        var datesWithEntries = entries.stream()
                .map(MealEntry::getLogDate)
                .collect(Collectors.toSet());

        int streak = 0;
        LocalDate cursor = today;
        // Walk backwards until we find a day with no entries
        while (datesWithEntries.contains(cursor)) {
            streak++;
            cursor = cursor.minusDays(1);
        }
        return streak;
    }

    /** Round a double to 2 decimal places for clean JSON output. */
    static double round2(double value) {
        return BigDecimal.valueOf(value)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
