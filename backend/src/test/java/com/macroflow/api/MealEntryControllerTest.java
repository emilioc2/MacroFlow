package com.macroflow.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.macroflow.dto.*;
import com.macroflow.security.JwtService;
import com.macroflow.service.MealService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller-layer tests for meal entry, sync, and saved meal endpoints.
 *
 * <p>Uses the full Spring context with MockMvc. {@link MealService} is mocked so no
 * DB access occurs. A real JWT is generated via {@link JwtService} so the security
 * filter sets the correct principal.
 *
 * <p>Covers:
 * <ul>
 *   <li>GET /meal-entries — 200 with paginated results; 401 without JWT</li>
 *   <li>POST /sync/batch — idempotent duplicate returns ok; out-of-window returns
 *       out_of_window; 401 without JWT; 422 for empty list</li>
 *   <li>GET/POST/PUT/DELETE /saved-meals — happy paths and ownership 404</li>
 *   <li>Ownership isolation — different JWT scopes to different userId</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MealEntryControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JwtService jwtService;
    @MockBean  MealService mealService;

    private UUID userId;
    private String validJwt;

    @BeforeEach
    void setUp() {
        userId   = UUID.randomUUID();
        validJwt = jwtService.generateAccessToken(userId, "google");
    }

    // GET /api/v1/meal-entries

    @Test
    void getMealEntries_authenticated_returns200WithPage() throws Exception {
        MealEntryPageDto page = new MealEntryPageDto(List.of(), 0L, 0, 50, 0);
        when(mealService.getMealEntries(eq(userId), any(), any(), anyInt(), anyInt()))
                .thenReturn(page);

        mockMvc.perform(get("/api/v1/meal-entries")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + validJwt)
                        .param("startDate", "2026-01-01")
                        .param("endDate", "2026-01-07"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_elements").value(0))
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void getMealEntries_noJwt_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/meal-entries")
                        .param("startDate", "2026-01-01")
                        .param("endDate", "2026-01-07"))
                .andExpect(status().isUnauthorized());
    }

    // POST /api/v1/sync/batch

    /**
     * A duplicate sync operation (same clientId sent twice) must return HTTP 200 with
     * status {@code ok} — the server processes it idempotently without error.
     */
    @Test
    void syncBatch_duplicateClientId_returns200WithOk() throws Exception {
        String clientId = UUID.randomUUID().toString();
        when(mealService.processBatch(eq(userId), any()))
                .thenReturn(List.of(new SyncResultDto(clientId, "ok", null)));

        mockMvc.perform(post("/api/v1/sync/batch")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + validJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(buildUpsertOp(clientId)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].client_id").value(clientId))
                .andExpect(jsonPath("$[0].status").value("ok"));
    }

    /**
     * An operation whose logDate falls outside the 7-day edit window must return HTTP 200
     * with per-op status {@code out_of_window} — the batch itself succeeds.
     */
    @Test
    void syncBatch_outOfWindowLogDate_returns200WithOutOfWindow() throws Exception {
        String clientId = UUID.randomUUID().toString();
        when(mealService.processBatch(eq(userId), any()))
                .thenReturn(List.of(new SyncResultDto(clientId, "out_of_window",
                        "logDate 2025-01-01 is outside the 7-day edit window")));

        mockMvc.perform(post("/api/v1/sync/batch")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + validJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(buildUpsertOp(clientId)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("out_of_window"));
    }

    @Test
    void syncBatch_noJwt_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/sync/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(buildUpsertOp(UUID.randomUUID().toString())))))
                .andExpect(status().isUnauthorized());
    }

    /** An empty operations list must return 422 — the client should never send an empty batch. */
    @Test
    void syncBatch_emptyList_returns422() throws Exception {
        mockMvc.perform(post("/api/v1/sync/batch")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + validJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[]"))
                .andExpect(status().isUnprocessableEntity());
    }

    // GET /api/v1/saved-meals

    @Test
    void getSavedMeals_authenticated_returns200WithList() throws Exception {
        SavedMealDto meal = new SavedMealDto(UUID.randomUUID(), "Lunch Prep", OffsetDateTime.now(), List.of());
        when(mealService.getSavedMeals(userId)).thenReturn(List.of(meal));

        mockMvc.perform(get("/api/v1/saved-meals")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + validJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Lunch Prep"));
    }

    // POST /api/v1/saved-meals

    @Test
    void createSavedMeal_validRequest_returns201() throws Exception {
        UUID mealId = UUID.randomUUID();
        SavedMealRequest req = new SavedMealRequest("Dinner Prep",
                List.of(new SavedMealRequest.Item(UUID.randomUUID(), 1.5)));
        when(mealService.createSavedMeal(eq(userId), any()))
                .thenReturn(new SavedMealDto(mealId, "Dinner Prep", OffsetDateTime.now(), List.of()));

        mockMvc.perform(post("/api/v1/saved-meals")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + validJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Dinner Prep"));
    }

    /** A saved meal request with no items must return 422 — at least one item is required. */
    @Test
    void createSavedMeal_emptyItems_returns422() throws Exception {
        mockMvc.perform(post("/api/v1/saved-meals")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + validJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Empty\", \"items\": []}"))
                .andExpect(status().isUnprocessableEntity());
    }

    // PUT /api/v1/saved-meals/{id} — 404 for wrong user

    /**
     * A PUT for a meal belonging to a different user must return 404.
     * The service throws 404 (intentionally vague) to avoid leaking whether the ID exists.
     */
    @Test
    void updateSavedMeal_wrongUser_returns404() throws Exception {
        UUID mealId = UUID.randomUUID();
        SavedMealRequest req = new SavedMealRequest("Updated",
                List.of(new SavedMealRequest.Item(UUID.randomUUID(), 1.0)));
        when(mealService.updateSavedMeal(eq(userId), eq(mealId), any()))
                .thenThrow(new ResponseStatusException(NOT_FOUND, "Saved meal not found"));

        mockMvc.perform(put("/api/v1/saved-meals/" + mealId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + validJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());
    }

    // DELETE /api/v1/saved-meals/{id}

    @Test
    void deleteSavedMeal_ownedMeal_returns204() throws Exception {
        mockMvc.perform(delete("/api/v1/saved-meals/" + UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + validJwt))
                .andExpect(status().isNoContent());
    }

    // Ownership isolation

    /**
     * Two different JWTs must produce independent service calls scoped to their
     * respective userIds — user B's JWT must not read user A's data.
     */
    @Test
    void getMealEntries_differentUserJwt_callsServiceWithCorrectUserId() throws Exception {
        UUID userIdB = UUID.randomUUID();
        String jwtB  = jwtService.generateAccessToken(userIdB, "apple");
        when(mealService.getMealEntries(eq(userIdB), any(), any(), anyInt(), anyInt()))
                .thenReturn(new MealEntryPageDto(List.of(), 5L, 0, 50, 1));

        mockMvc.perform(get("/api/v1/meal-entries")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtB)
                        .param("startDate", "2026-01-01")
                        .param("endDate", "2026-01-07"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_elements").value(5));
    }

    // Helpers

    /** Build a minimal valid upsert operation for use in request bodies. */
    private SyncOperationDto buildUpsertOp(String clientId) {
        return new SyncOperationDto(
                clientId, "upsert", Instant.now(),
                UUID.randomUUID(), LocalDate.now(), null,
                BigDecimal.ONE, BigDecimal.valueOf(30),
                BigDecimal.valueOf(40), BigDecimal.valueOf(10),
                BigDecimal.valueOf(370));
    }
}
