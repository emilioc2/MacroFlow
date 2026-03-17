package com.macroflow.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Request/response DTO for {@code GET /users/me} and {@code PUT /users/me}.
 *
 * <p>All fields are required — the client must supply a complete profile on every PUT.
 * {@code date_of_birth} is stored as a DATE in PostgreSQL; age is derived at calculation
 * time and never stored as a stale integer.
 *
 * <p>JSON property names use snake_case to match the mobile API contract; Java field
 * names use camelCase per Java conventions.
 */
public record UserProfileDto(

        /** Biological sex — expected values: {@code male} or {@code female}. */
        @NotNull
        String sex,

        /**
         * Date of birth in ISO-8601 format (YYYY-MM-DD).
         * Serialised as {@code date_of_birth} in JSON.
         */
        @NotNull
        @JsonProperty("date_of_birth")
        LocalDate dateOfBirth,

        /** Height in centimetres. Serialised as {@code height_cm} in JSON. */
        @NotNull
        @JsonProperty("height_cm")
        BigDecimal heightCm,

        /** Weight in kilograms. Serialised as {@code weight_kg} in JSON. */
        @NotNull
        @JsonProperty("weight_kg")
        BigDecimal weightKg,

        /**
         * Activity level — one of: {@code sedentary}, {@code light}, {@code moderate},
         * {@code active}, {@code very_active}.
         */
        @NotNull
        String activity,

        /**
         * Weight goal — one of: {@code lose}, {@code maintain}, {@code gain}.
         */
        @NotNull
        String goal,

        /**
         * IANA timezone identifier (e.g. {@code America/New_York}).
         * Governs streak resets, reminder scheduling, and daily log boundaries.
         * Defaults to {@code UTC} when not provided by the client.
         */
        @NotNull
        String timezone
) {}
