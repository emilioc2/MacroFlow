package com.macroflow.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Per-operation result returned inside the {@code POST /api/v1/sync/batch} response.
 *
 * <p>The batch endpoint processes operations in order and returns one result per
 * operation. The client uses {@code clientId} to correlate results back to its
 * local queue entries.
 *
 * <p>Possible {@code status} values:
 * <ul>
 *   <li>{@code ok} — operation applied successfully</li>
 *   <li>{@code conflict_ignored} — a newer {@code clientTs} already exists; this op was skipped</li>
 *   <li>{@code out_of_window} — the {@code logDate} falls outside the 7-day edit window</li>
 *   <li>{@code error} — unexpected failure; {@code message} contains details</li>
 * </ul>
 */
public record SyncResultDto(
        @JsonProperty("client_id")
        String clientId,

        /** Processing outcome for this operation. */
        String status,

        /** Human-readable detail; non-null only when status is {@code error} or {@code out_of_window}. */
        String message
) {}
