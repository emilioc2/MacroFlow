package com.macroflow.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Represents a single operation within a {@code POST /api/v1/sync/batch} request.
 *
 * <p>The {@code op} field drives processing:
 * <ul>
 *   <li>{@code upsert} — create or update the entry identified by {@code clientId}</li>
 *   <li>{@code delete} — soft-delete the entry identified by {@code clientId}</li>
 * </ul>
 *
 * <p>Conflict resolution: if an entry with the same {@code clientId} already exists,
 * the operation with the later {@code clientTs} wins. Operations with an older
 * {@code clientTs} than the stored value are silently accepted (idempotent replay).
 *
 * <p>Macro fields are required for {@code upsert} but ignored for {@code delete}.
 */
public record SyncOperationDto(

        /**
         * Client-generated UUID used as the idempotency key.
         * Must be stable across retries — the same logical entry always uses the same clientId.
         */
        @NotBlank
        @JsonProperty("client_id")
        String clientId,

        /** Operation type: {@code "upsert"} or {@code "delete"}. Validated in the service layer. */
        @NotBlank
        String op,

        /** Client-side timestamp used for conflict resolution (last-write-wins per clientId). */
        @NotNull
        @JsonProperty("client_ts")
        Instant clientTs,

        // Fields required for upsert; ignored for delete

        @JsonProperty("food_id")
        UUID foodId,

        @JsonProperty("log_date")
        LocalDate logDate,

        @JsonProperty("meal_name")
        String mealName,

        BigDecimal servings,

        @JsonProperty("protein_g")
        BigDecimal proteinG,

        @JsonProperty("carbs_g")
        BigDecimal carbsG,

        @JsonProperty("fat_g")
        BigDecimal fatG,

        BigDecimal calories
) {}
