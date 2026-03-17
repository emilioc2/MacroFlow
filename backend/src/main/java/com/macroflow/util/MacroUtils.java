package com.macroflow.util;

import com.macroflow.model.UserProfile;

import java.time.LocalDate;
import java.time.Period;

/**
 * Pure static utility class for TDEE and macro split calculations.
 *
 * <p>No Spring beans, no state — all methods are stateless and side-effect-free.
 *
 * <p>TDEE uses the Mifflin-St Jeor BMR formula, scaled by an activity multiplier,
 * then adjusted by a goal offset (−500 / 0 / +300 kcal/day for lose/maintain/gain).
 * Age is derived from {@code profile.getDateOfBirth()} at call time so it is never stale.
 *
 * <p>Macro split allocates 30% protein, 40% carbs, 30% fat of total calories,
 * converting each percentage to grams using the standard calorie-per-gram coefficients
 * (protein: 4 kcal/g, carbs: 4 kcal/g, fat: 9 kcal/g).
 */
public final class MacroUtils {

    private MacroUtils() {
        // utility class — no instantiation
    }

    // -------------------------------------------------------------------------
    // Activity multipliers (Mifflin-St Jeor standard values)
    // -------------------------------------------------------------------------

    private static final double MULTIPLIER_SEDENTARY   = 1.2;
    private static final double MULTIPLIER_LIGHT       = 1.375;
    private static final double MULTIPLIER_MODERATE    = 1.55;
    private static final double MULTIPLIER_ACTIVE      = 1.725;
    private static final double MULTIPLIER_VERY_ACTIVE = 1.9;

    // -------------------------------------------------------------------------
    // Goal adjustments (kcal/day applied after activity scaling)
    // -------------------------------------------------------------------------

    private static final double GOAL_LOSE     = -500.0;
    private static final double GOAL_MAINTAIN =    0.0;
    private static final double GOAL_GAIN     =  300.0;

    // -------------------------------------------------------------------------
    // Macro split ratios and calorie-per-gram coefficients
    // -------------------------------------------------------------------------

    private static final double PROTEIN_RATIO = 0.30;
    private static final double CARBS_RATIO   = 0.40;
    private static final double FAT_RATIO     = 0.30;

    private static final double KCAL_PER_G_PROTEIN = 4.0;
    private static final double KCAL_PER_G_CARBS   = 4.0;
    private static final double KCAL_PER_G_FAT     = 9.0;

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Calculates the adjusted Total Daily Energy Expenditure (TDEE) for the given profile.
     *
     * <p>Mifflin-St Jeor BMR:
     * <ul>
     *   <li>Male:   BMR = (10 × weight_kg) + (6.25 × height_cm) − (5 × age) + 5</li>
     *   <li>Female: BMR = (10 × weight_kg) + (6.25 × height_cm) − (5 × age) − 161</li>
     * </ul>
     * TDEE = BMR × activity_multiplier + goal_adjustment
     *
     * <p>Age is derived from {@code profile.getDateOfBirth()} at call time using
     * {@link LocalDate#now()} — never a stored integer, so it stays accurate as the
     * user ages without requiring a profile update.
     *
     * @param profile the user's profile; must not be {@code null}
     * @return adjusted TDEE in kcal/day, rounded to the nearest integer
     * @throws IllegalArgumentException if {@code profile.getActivity()} or
     *                                  {@code profile.getGoal()} is not a recognised value
     */
    public static double calculateTdee(UserProfile profile) {
        double weightKg = profile.getWeightKg().doubleValue();
        double heightCm = profile.getHeightCm().doubleValue();
        // Derive age at call time — never use a stored integer that could be stale
        int age = Period.between(profile.getDateOfBirth(), LocalDate.now()).getYears();

        double bmr = (10.0 * weightKg)
                   + (6.25 * heightCm)
                   - (5.0  * age)
                   + sexOffset(profile.getSex());

        double tdee = bmr * activityMultiplier(profile.getActivity())
                    + goalAdjustment(profile.getGoal());

        return Math.round(tdee);
    }

    /**
     * Calculates the default macro split for the given total calorie target.
     *
     * <p>Split: 30% protein, 40% carbs, 30% fat of {@code totalCalories}.
     * Each macro is converted to grams using standard calorie-per-gram coefficients
     * and rounded to one decimal place.
     *
     * @param totalCalories total daily calorie target in kcal
     * @return a {@link MacroSplit} record with protein, carbs, and fat in grams
     */
    public static MacroSplit calculateMacroSplit(double totalCalories) {
        double proteinG = round1dp((totalCalories * PROTEIN_RATIO) / KCAL_PER_G_PROTEIN);
        double carbsG   = round1dp((totalCalories * CARBS_RATIO)   / KCAL_PER_G_CARBS);
        double fatG     = round1dp((totalCalories * FAT_RATIO)      / KCAL_PER_G_FAT);
        return new MacroSplit(proteinG, carbsG, fatG);
    }

    // -------------------------------------------------------------------------
    // Nested record — public so callers can reference the type directly
    // -------------------------------------------------------------------------

    /**
     * Immutable value object holding the three macro targets in grams.
     *
     * @param proteinG grams of protein
     * @param carbsG   grams of carbohydrates
     * @param fatG     grams of fat
     */
    public record MacroSplit(double proteinG, double carbsG, double fatG) {}

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Returns the Mifflin-St Jeor sex constant.
     * Male: +5, Female: −161. Any unrecognised value is treated as female (−161)
     * to avoid throwing on minor casing differences; callers should validate upstream.
     */
    private static double sexOffset(String sex) {
        return "male".equalsIgnoreCase(sex) ? 5.0 : -161.0;
    }

    /** Maps the activity string to its Mifflin-St Jeor multiplier. */
    private static double activityMultiplier(String activity) {
        return switch (activity.toLowerCase()) {
            case "sedentary"   -> MULTIPLIER_SEDENTARY;
            case "light"       -> MULTIPLIER_LIGHT;
            case "moderate"    -> MULTIPLIER_MODERATE;
            case "active"      -> MULTIPLIER_ACTIVE;
            case "very_active" -> MULTIPLIER_VERY_ACTIVE;
            default -> throw new IllegalArgumentException(
                    "Unknown activity level: '" + activity + "'. " +
                    "Expected one of: sedentary, light, moderate, active, very_active");
        };
    }

    /** Maps the goal string to its daily kcal adjustment. */
    private static double goalAdjustment(String goal) {
        return switch (goal.toLowerCase()) {
            case "lose"     -> GOAL_LOSE;
            case "maintain" -> GOAL_MAINTAIN;
            case "gain"     -> GOAL_GAIN;
            default -> throw new IllegalArgumentException(
                    "Unknown goal: '" + goal + "'. Expected one of: lose, maintain, gain");
        };
    }

    /** Rounds {@code value} to one decimal place. */
    private static double round1dp(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
