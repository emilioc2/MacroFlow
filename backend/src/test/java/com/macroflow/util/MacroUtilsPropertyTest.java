package com.macroflow.util;

import com.macroflow.model.UserProfile;
import net.jqwik.api.*;
import net.jqwik.api.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Property-based tests for {@link MacroUtils} using jqwik.
 *
 * <p><b>Property 8 — TDEE correctness:</b>
 * For any valid profile, the adjusted TDEE must be a positive finite number and must
 * equal the manually re-derived Mifflin-St Jeor value (±1 kcal tolerance for
 * {@code Math.round}). Additional monotonicity properties verify that higher activity
 * produces higher TDEE and that goal adjustments are ordered lose &lt; maintain &lt; gain.
 *
 * <p><b>Property 9 — Macro split correctness:</b>
 * For any positive calorie total, the macro split must satisfy the 30/40/30 formula
 * (converted to grams, rounded to 1 dp). All gram values must be non-negative, the
 * calorie reconstruction must be within 2% of the original total, and the split must
 * scale linearly when the calorie input is doubled.
 */
class MacroUtilsPropertyTest {

    // Valid activity strings and their corresponding Mifflin-St Jeor multipliers,
    // kept in the same order so index-based lookup is safe.
    private static final String[] ACTIVITIES           = { "sedentary", "light", "moderate", "active", "very_active" };
    private static final double[] ACTIVITY_MULTIPLIERS = { 1.2, 1.375, 1.55, 1.725, 1.9 };

    // Valid goal strings and their kcal/day adjustments, same index ordering.
    private static final String[] GOALS            = { "lose", "maintain", "gain" };
    private static final double[] GOAL_ADJUSTMENTS = { -500.0, 0.0, 300.0 };

    // -------------------------------------------------------------------------
    // Property 8 — TDEE correctness
    // -------------------------------------------------------------------------

    /**
     * For any valid profile, {@code calculateTdee} must return a positive finite value
     * that matches the manually computed Mifflin-St Jeor result (±1 kcal for rounding).
     *
     * <p>Expected formula:
     * <pre>
     *   BMR  = (10 × weight) + (6.25 × height) − (5 × age) + sexOffset
     *   TDEE = round(BMR × activityMultiplier + goalAdjustment)
     * </pre>
     */
    @Property(tries = 100)
    @Label("Property 8: TDEE is positive and matches Mifflin-St Jeor formula")
    void tdeeIsPositiveAndMatchesFormula(
            @ForAll @DoubleRange(min = 40.0, max = 200.0) double weightKg,
            @ForAll @DoubleRange(min = 140.0, max = 220.0) double heightCm,
            @ForAll @IntRange(min = 18, max = 80) int age,
            @ForAll @IntRange(min = 0, max = 1) int sexIndex,
            @ForAll @IntRange(min = 0, max = 4) int activityIndex,
            @ForAll @IntRange(min = 0, max = 2) int goalIndex) {

        String sex      = sexIndex == 0 ? "male" : "female";
        String activity = ACTIVITIES[activityIndex];
        String goal     = GOALS[goalIndex];

        UserProfile profile = buildProfile(weightKg, heightCm, age, sex, activity, goal);
        double tdee = MacroUtils.calculateTdee(profile);

        assertThat(tdee).isFinite().isPositive();

        // Re-derive expected value using the same formula to verify correctness
        double sexOffset  = "male".equalsIgnoreCase(sex) ? 5.0 : -161.0;
        double bmr        = (10.0 * weightKg) + (6.25 * heightCm) - (5.0 * age) + sexOffset;
        double expected   = Math.round(bmr * ACTIVITY_MULTIPLIERS[activityIndex] + GOAL_ADJUSTMENTS[goalIndex]);

        // ±1 kcal tolerance accounts for floating-point rounding in Math.round
        assertThat(tdee).isCloseTo(expected, within(1.0));
    }

    /**
     * Higher activity level must produce a strictly higher TDEE for the same profile,
     * because each successive multiplier (1.2 → 1.375 → 1.55 → 1.725 → 1.9) is larger.
     */
    @Property(tries = 100)
    @Label("Property 8b: Higher activity level produces higher TDEE")
    void higherActivityProducesHigherTdee(
            @ForAll @DoubleRange(min = 50.0, max = 150.0) double weightKg,
            @ForAll @DoubleRange(min = 150.0, max = 200.0) double heightCm,
            @ForAll @IntRange(min = 20, max = 60) int age,
            @ForAll @IntRange(min = 0, max = 3) int lowerActivityIndex) {

        // higherActivityIndex is always valid because lowerActivityIndex max is 3 (→ 4)
        int higherActivityIndex = lowerActivityIndex + 1;

        UserProfile lower  = buildProfile(weightKg, heightCm, age, "male", ACTIVITIES[lowerActivityIndex],  "maintain");
        UserProfile higher = buildProfile(weightKg, heightCm, age, "male", ACTIVITIES[higherActivityIndex], "maintain");

        assertThat(MacroUtils.calculateTdee(higher))
                .isGreaterThan(MacroUtils.calculateTdee(lower));
    }

    /**
     * Goal adjustments must be strictly ordered: lose (−500) &lt; maintain (0) &lt; gain (+300).
     * This holds for any valid base profile.
     */
    @Property(tries = 100)
    @Label("Property 8c: Goal adjustments are ordered lose < maintain < gain")
    void goalAdjustmentsAreOrdered(
            @ForAll @DoubleRange(min = 50.0, max = 150.0) double weightKg,
            @ForAll @DoubleRange(min = 150.0, max = 200.0) double heightCm,
            @ForAll @IntRange(min = 20, max = 60) int age) {

        UserProfile lose     = buildProfile(weightKg, heightCm, age, "female", "moderate", "lose");
        UserProfile maintain = buildProfile(weightKg, heightCm, age, "female", "moderate", "maintain");
        UserProfile gain     = buildProfile(weightKg, heightCm, age, "female", "moderate", "gain");

        assertThat(MacroUtils.calculateTdee(lose))
                .isLessThan(MacroUtils.calculateTdee(maintain));
        assertThat(MacroUtils.calculateTdee(maintain))
                .isLessThan(MacroUtils.calculateTdee(gain));
    }

    // -------------------------------------------------------------------------
    // Property 9 — Macro split correctness
    // -------------------------------------------------------------------------

    /**
     * For any positive calorie total, the macro split must exactly match the 30/40/30
     * formula (each percentage converted to grams and rounded to 1 dp), and all gram
     * values must be non-negative.
     */
    @Property(tries = 100)
    @Label("Property 9: Macro split matches 30/40/30 formula and all values are non-negative")
    void macroSplitMatchesFormulaAndIsNonNegative(
            @ForAll @DoubleRange(min = 500.0, max = 5000.0) double calories) {

        MacroUtils.MacroSplit split = MacroUtils.calculateMacroSplit(calories);

        assertThat(split.proteinG()).isGreaterThanOrEqualTo(0.0);
        assertThat(split.carbsG()).isGreaterThanOrEqualTo(0.0);
        assertThat(split.fatG()).isGreaterThanOrEqualTo(0.0);

        // Verify each value matches the formula exactly
        assertThat(split.proteinG()).isEqualTo(round1dp((calories * 0.30) / 4.0));
        assertThat(split.carbsG()).isEqualTo(round1dp((calories * 0.40) / 4.0));
        assertThat(split.fatG()).isEqualTo(round1dp((calories * 0.30) / 9.0));
    }

    /**
     * The calorie reconstruction from the split (protein×4 + carbs×4 + fat×9) must be
     * within 2% of the original total. Rounding each macro to 1 dp introduces small
     * drift; 2% is a generous but meaningful correctness bound.
     */
    @Property(tries = 100)
    @Label("Property 9b: Calorie reconstruction from macro split is within 2% of original")
    void calorieReconstructionIsWithinTolerance(
            @ForAll @DoubleRange(min = 500.0, max = 5000.0) double calories) {

        MacroUtils.MacroSplit split = MacroUtils.calculateMacroSplit(calories);

        double reconstructed = (split.proteinG() * 4.0)
                             + (split.carbsG()   * 4.0)
                             + (split.fatG()      * 9.0);

        assertThat(reconstructed).isCloseTo(calories, within(calories * 0.02));
    }

    /**
     * Doubling the calorie input must approximately double each macro gram value.
     * Tolerance of ±0.2g accounts for independent 1-dp rounding on each value.
     */
    @Property(tries = 100)
    @Label("Property 9c: Macro split scales linearly with calorie input")
    void macroSplitScalesLinearly(
            @ForAll @DoubleRange(min = 500.0, max = 2000.0) double calories) {

        MacroUtils.MacroSplit single  = MacroUtils.calculateMacroSplit(calories);
        MacroUtils.MacroSplit doubled = MacroUtils.calculateMacroSplit(calories * 2.0);

        assertThat(doubled.proteinG()).isCloseTo(single.proteinG() * 2.0, within(0.2));
        assertThat(doubled.carbsG()).isCloseTo(single.carbsG()   * 2.0, within(0.2));
        assertThat(doubled.fatG()).isCloseTo(single.fatG()    * 2.0, within(0.2));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Builds a {@link UserProfile} with the given parameters.
     * {@code dateOfBirth} is derived from {@code age} relative to today so the profile
     * is always consistent with the current date at test execution time — matching the
     * behaviour of {@code MacroUtils.calculateTdee} which calls {@code LocalDate.now()}.
     */
    private UserProfile buildProfile(double weightKg, double heightCm, int age,
                                     String sex, String activity, String goal) {
        UserProfile p = new UserProfile(UUID.randomUUID());
        p.setWeightKg(BigDecimal.valueOf(weightKg));
        p.setHeightCm(BigDecimal.valueOf(heightCm));
        p.setDateOfBirth(LocalDate.now().minusYears(age));
        p.setSex(sex);
        p.setActivity(activity);
        p.setGoal(goal);
        p.setTimezone("UTC");
        p.setUpdatedAt(OffsetDateTime.now());
        return p;
    }

    /**
     * Mirrors the private {@code round1dp} helper in {@link MacroUtils} so expected
     * values in property assertions are computed identically.
     */
    private static double round1dp(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
