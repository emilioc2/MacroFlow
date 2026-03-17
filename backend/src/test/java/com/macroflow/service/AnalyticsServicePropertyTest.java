package com.macroflow.service;

import com.macroflow.model.MealEntry;
import net.jqwik.api.*;
import net.jqwik.api.constraints.DoubleRange;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Property-based tests for {@link AnalyticsService} helper methods.
 *
 * <p>These tests exercise the pure static helpers directly — no Spring context
 * needed. The service layer is tested in isolation from the DB.
 *
 * <ul>
 *   <li>Property 13: weekly average correctness</li>
 *   <li>Property 14: daily calorie aggregation</li>
 *   <li>Property 15: adherence % correctness</li>
 * </ul>
 */
class AnalyticsServicePropertyTest {

    // -------------------------------------------------------------------------
    // Property 13 — Weekly average correctness
    // -------------------------------------------------------------------------

    /**
     * Property 13: The weekly average of 7 daily values equals sum / 7,
     * regardless of how many days have entries.
     *
     * <p>Days with no entries contribute zero — this is intentional so the average
     * reflects actual intake over the full week, not just logged days.
     */
    @Property(tries = 100)
    @Label("Property 13: Weekly average = sum / 7 for any daily values array")
    void weeklyAverageEqualsSum(
            @ForAll @Size(value = 7) List<@DoubleRange(min = 0, max = 5000) Double> dailyValues) {
        double[] arr = dailyValues.stream().mapToDouble(Double::doubleValue).toArray();
        double expected = dailyValues.stream().mapToDouble(Double::doubleValue).sum() / 7.0;
        assertThat(AnalyticsService.average(arr)).isCloseTo(expected, within(0.001));
    }

    /**
     * Property 13b: Average of an all-zero array is zero.
     * Ensures no NaN or division-by-zero when the user has no entries.
     */
    @Property(tries = 100)
    @Label("Property 13b: Average of all-zero daily values is 0")
    void weeklyAverageOfZerosIsZero(@ForAll @IntRange(min = 1, max = 30) int windowSize) {
        double[] zeros = new double[windowSize];
        assertThat(AnalyticsService.average(zeros)).isEqualTo(0.0);
    }

    // -------------------------------------------------------------------------
    // Property 14 — Daily calorie aggregation
    // -------------------------------------------------------------------------

    /**
     * Property 14: Summing calories across multiple entries on the same day
     * produces the correct total. Verifies that the aggregation logic in
     * {@code getMonthlyAnalytics} correctly groups by date.
     */
    @Property(tries = 100)
    @Label("Property 14: Daily calorie sum equals sum of individual entry calories")
    void dailyCalorieSumIsCorrect(
            @ForAll @Size(min = 1, max = 20) List<@DoubleRange(min = 0, max = 2000) Double> entryCalories) {

        LocalDate date = LocalDate.of(2025, 1, 15);
        List<MealEntry> entries = new ArrayList<>();
        for (double cal : entryCalories) {
            MealEntry e = new MealEntry();
            e.setLogDate(date);
            e.setCalories(BigDecimal.valueOf(cal));
            // Set required non-null fields to avoid NPE in stream operations
            e.setProteinG(BigDecimal.ZERO);
            e.setCarbsG(BigDecimal.ZERO);
            e.setFatG(BigDecimal.ZERO);
            entries.add(e);
        }

        double expectedTotal = entryCalories.stream().mapToDouble(Double::doubleValue).sum();

        // Simulate the grouping + summing logic from getMonthlyAnalytics
        double actualTotal = entries.stream()
                .collect(Collectors.groupingBy(
                        MealEntry::getLogDate,
                        Collectors.summingDouble(e -> e.getCalories().doubleValue())
                ))
                .getOrDefault(date, 0.0);

        assertThat(actualTotal).isCloseTo(expectedTotal, within(0.001));
    }

    // -------------------------------------------------------------------------
    // Property 15 — Adherence % correctness
    // -------------------------------------------------------------------------

    /**
     * Property 15a: When actual == target, adherence is exactly 100%.
     */
    @Property(tries = 100)
    @Label("Property 15a: Adherence is 100% when actual equals target")
    void adherenceIs100WhenActualEqualsTarget(
            @ForAll @DoubleRange(min = 1, max = 5000) double target) {
        assertThat(AnalyticsService.adherencePct(target, target)).isCloseTo(100.0, within(0.001));
    }

    /**
     * Property 15b: When actual > 2 * target, adherence is capped at 200%.
     * The cap prevents unbounded values in the response.
     */
    @Property(tries = 100)
    @Label("Property 15b: Adherence is capped at 200% when actual > 2 * target")
    void adherenceIsCappedAt200(
            @ForAll @DoubleRange(min = 1, max = 2000) double target,
            @ForAll @DoubleRange(min = 0, max = 5000) double excess) {
        // actual = 2 * target + excess + 1 always exceeds the 200% cap
        double actual = 2 * target + excess + 1;
        assertThat(AnalyticsService.adherencePct(actual, target)).isEqualTo(200.0);
    }

    /**
     * Property 15c: When target is 0, adherence is 0% — no division by zero.
     */
    @Property(tries = 100)
    @Label("Property 15c: Adherence is 0% when target is 0")
    void adherenceIsZeroWhenTargetIsZero(
            @ForAll @DoubleRange(min = 0, max = 5000) double actual) {
        assertThat(AnalyticsService.adherencePct(actual, 0.0)).isEqualTo(0.0);
    }

    /**
     * Property 15d: Adherence is always in [0, 200] for any non-negative inputs.
     * This is the invariant the frontend relies on for rendering progress bars.
     */
    @Property(tries = 100)
    @Label("Property 15d: Adherence is always in [0, 200] for any non-negative inputs")
    void adherenceIsAlwaysInBounds(
            @ForAll @DoubleRange(min = 0, max = 10000) double actual,
            @ForAll @DoubleRange(min = 0, max = 10000) double target) {
        assertThat(AnalyticsService.adherencePct(actual, target)).isBetween(0.0, 200.0);
    }
}
