package com.macroflow.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Request/response DTO for {@code GET /users/me/preferences} and
 * {@code PUT /users/me/preferences}.
 *
 * <p>All fields are required on PUT. When no preferences row exists yet, the service
 * returns a DTO with defaults ({@code recentlyLoggedMax=10, tutorialShown=false,
 * theme="system"}) without writing to the DB.
 */
public record UserPreferencesDto(

        /**
         * Maximum number of recently-logged foods to retain.
         * Must be between 5 and 20 inclusive.
         * Serialised as {@code recently_logged_max} in JSON.
         */
        @NotNull
        @Min(5)
        @Max(20)
        @JsonProperty("recently_logged_max")
        Integer recentlyLoggedMax,

        /**
         * Whether the onboarding tutorial has been shown to the user.
         * Serialised as {@code tutorial_shown} in JSON.
         */
        @NotNull
        @JsonProperty("tutorial_shown")
        Boolean tutorialShown,

        /**
         * UI theme preference — one of: {@code light}, {@code dark}, {@code system}.
         */
        @NotNull
        String theme
) {}
