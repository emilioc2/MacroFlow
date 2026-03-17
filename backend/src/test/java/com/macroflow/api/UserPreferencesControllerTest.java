package com.macroflow.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.macroflow.dto.UserPreferencesDto;
import com.macroflow.security.JwtService;
import com.macroflow.service.UserService;
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

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller-layer tests for GET /api/v1/users/me/preferences and
 * PUT /api/v1/users/me/preferences.
 *
 * UserService is mocked so no DB access occurs. A real JWT is generated via
 * JwtService so the security filter sets the correct principal.
 *
 * Covers: GET 200 defaults, GET 200 saved, GET 401, PUT 200, PUT 401,
 * PUT 422 out-of-range recently_logged_max, PUT 422 null required fields.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserPreferencesControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JwtService jwtService;

    @MockBean UserService userService;

    private UUID userId;
    private String validJwt;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        validJwt = jwtService.generateAccessToken(userId, "google");
    }

    // GET — returns defaults when no preferences row exists yet
    @Test
    void getPreferences_noRowExists_returns200WithDefaults() throws Exception {
        when(userService.getPreferences(userId)).thenReturn(new UserPreferencesDto(10, false, "system"));

        mockMvc.perform(get("/api/v1/users/me/preferences")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + validJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recently_logged_max").value(10))
                .andExpect(jsonPath("$.tutorial_shown").value(false))
                .andExpect(jsonPath("$.theme").value("system"));
    }

    // GET — returns saved preferences when a row exists
    @Test
    void getPreferences_rowExists_returns200WithSavedValues() throws Exception {
        when(userService.getPreferences(userId)).thenReturn(new UserPreferencesDto(15, true, "dark"));

        mockMvc.perform(get("/api/v1/users/me/preferences")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + validJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recently_logged_max").value(15))
                .andExpect(jsonPath("$.tutorial_shown").value(true))
                .andExpect(jsonPath("$.theme").value("dark"));
    }

    // GET — 401 when no JWT is present
    @Test
    void getPreferences_noJwt_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/users/me/preferences"))
                .andExpect(status().isUnauthorized());
    }

    // PUT — valid DTO saves and returns the saved values
    @Test
    void upsertPreferences_validDto_returns200WithSavedValues() throws Exception {
        UserPreferencesDto dto = new UserPreferencesDto(12, true, "light");
        when(userService.upsertPreferences(eq(userId), any(UserPreferencesDto.class))).thenReturn(dto);

        mockMvc.perform(put("/api/v1/users/me/preferences")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + validJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recently_logged_max").value(12))
                .andExpect(jsonPath("$.tutorial_shown").value(true))
                .andExpect(jsonPath("$.theme").value("light"));
    }

    // PUT — 401 when no JWT is present
    @Test
    void upsertPreferences_noJwt_returns401() throws Exception {
        mockMvc.perform(put("/api/v1/users/me/preferences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UserPreferencesDto(10, false, "system"))))
                .andExpect(status().isUnauthorized());
    }

    // PUT — 422 when recently_logged_max is below @Min(5)
    @Test
    void upsertPreferences_recentlyLoggedMaxTooLow_returns422() throws Exception {
        mockMvc.perform(put("/api/v1/users/me/preferences")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + validJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recently_logged_max\": 3, \"tutorial_shown\": false, \"theme\": \"system\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.fields").isArray());
    }

    // PUT — 422 when recently_logged_max is above @Max(20)
    @Test
    void upsertPreferences_recentlyLoggedMaxTooHigh_returns422() throws Exception {
        mockMvc.perform(put("/api/v1/users/me/preferences")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + validJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recently_logged_max\": 25, \"tutorial_shown\": false, \"theme\": \"system\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.fields").isArray());
    }

    // PUT — 422 when all required fields are null
    @Test
    void upsertPreferences_nullRequiredFields_returns422WithFieldErrors() throws Exception {
        mockMvc.perform(put("/api/v1/users/me/preferences")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + validJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.fields").isArray());
    }
}
