package com.macroflow.api;

import com.macroflow.dto.DailyCaloriesDto;
import com.macroflow.dto.WeeklyAnalyticsDto;
import com.macroflow.security.JwtService;
import com.macroflow.service.AnalyticsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller-layer tests for analytics endpoints.
 *
 * <p>Uses the full Spring context with MockMvc. {@link AnalyticsService} is mocked
 * so no DB access occurs. A real JWT is generated via {@link JwtService} so the
 * security filter sets the correct principal.
 *
 * <p>Covers:
 * <ul>
 *   <li>GET /analytics/weekly — 200 with JWT; 401 without JWT; full field shape</li>
 *   <li>GET /analytics/monthly — 200 with JWT; 401 without JWT; daily series shape</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AnalyticsControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired JwtService jwtService;
    @MockBean  AnalyticsService analyticsService;

    private String validJwt;

    @BeforeEach
    void setUp() {
        UUID userId = UUID.randomUUID();
        validJwt = jwtService.generateAccessToken(userId, "google");

        // Default stub — zeroed-out weekly DTO for any userId
        when(analyticsService.getWeeklyAnalytics(any())).thenReturn(
                new WeeklyAnalyticsDto(0, 0, 0, 0, 0, 0, 0, 0, 0));

        // Default stub — empty daily series for any userId
        when(analyticsService.getMonthlyAnalytics(any())).thenReturn(List.of());
    }

    // GET /api/v1/analytics/weekly

    @Test
    void getWeeklyAnalytics_withValidJwt_returns200() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/weekly")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + validJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avg_calories").exists())
                .andExpect(jsonPath("$.adherence_calories_pct").exists())
                .andExpect(jsonPath("$.streak_count").exists());
    }

    @Test
    void getWeeklyAnalytics_withoutJwt_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/weekly"))
                .andExpect(status().isUnauthorized());
    }

    /** Verify the response shape includes all expected fields from WeeklyAnalyticsDto. */
    @Test
    void getWeeklyAnalytics_returnsAllFields() throws Exception {
        when(analyticsService.getWeeklyAnalytics(any())).thenReturn(
                new WeeklyAnalyticsDto(2000.0, 150.0, 200.0, 67.0, 95.5, 102.3, 88.0, 91.0, 5));

        mockMvc.perform(get("/api/v1/analytics/weekly")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + validJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avg_calories").value(2000.0))
                .andExpect(jsonPath("$.avg_protein_g").value(150.0))
                .andExpect(jsonPath("$.avg_carbs_g").value(200.0))
                .andExpect(jsonPath("$.avg_fat_g").value(67.0))
                .andExpect(jsonPath("$.adherence_calories_pct").value(95.5))
                .andExpect(jsonPath("$.adherence_protein_pct").value(102.3))
                .andExpect(jsonPath("$.adherence_carbs_pct").value(88.0))
                .andExpect(jsonPath("$.adherence_fat_pct").value(91.0))
                .andExpect(jsonPath("$.streak_count").value(5));
    }

    // GET /api/v1/analytics/monthly

    @Test
    void getMonthlyAnalytics_withValidJwt_returns200() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/monthly")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + validJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void getMonthlyAnalytics_withoutJwt_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/monthly"))
                .andExpect(status().isUnauthorized());
    }

    /** Verify the daily calorie series shape when entries are present. */
    @Test
    void getMonthlyAnalytics_withEntries_returnsCorrectShape() throws Exception {
        when(analyticsService.getMonthlyAnalytics(any())).thenReturn(List.of(
                new DailyCaloriesDto(LocalDate.of(2026, 3, 1), 1850.0),
                new DailyCaloriesDto(LocalDate.of(2026, 3, 2), 2100.5)
        ));

        mockMvc.perform(get("/api/v1/analytics/monthly")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + validJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].log_date").value("2026-03-01"))
                .andExpect(jsonPath("$[0].total_calories").value(1850.0))
                .andExpect(jsonPath("$[1].log_date").value("2026-03-02"))
                .andExpect(jsonPath("$[1].total_calories").value(2100.5));
    }
}
