package com.macroflow.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.macroflow.dto.UserProfileDto;
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
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller-layer tests for {@code GET /api/v1/users/me} and {@code PUT /api/v1/users/me}.
 *
 * <p>Uses the full Spring context with MockMvc (same pattern as {@link AuthControllerTest}).
 * {@link UserService} is mocked so no DB access occurs. A real JWT is generated via
 * {@link JwtService} so the {@link com.macroflow.security.JwtAuthenticationFilter} sets
 * the correct principal in the security context.
 *
 * <p>Covers:
 * <ul>
 *   <li>Happy path GET — returns 200 with profile DTO</li>
 *   <li>Happy path PUT — returns 200 with saved profile DTO</li>
 *   <li>GET 404 — profile not yet created</li>
 *   <li>GET 401 — no JWT present (unauthenticated)</li>
 *   <li>PUT 401 — no JWT present (unauthenticated)</li>
 *   <li>PUT 422 — invalid DTO (null required fields)</li>
 *   <li>PUT 422 — invalid date_of_birth format</li>
 *   <li>Ownership isolation — a JWT for user B cannot read or write user A's profile;
 *       the userId is always extracted from the token, never from the request body</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JwtService jwtService;

    // Mock UserService so tests don't hit the DB
    @MockBean
    UserService userService;

    private UUID userId;
    private String validJwt;
    private UserProfileDto sampleProfile;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        // Generate a real JWT so the filter sets the principal correctly
        validJwt = jwtService.generateAccessToken(userId, "google");

        sampleProfile = new UserProfileDto(
                "male",
                LocalDate.of(1990, 6, 15),
                new BigDecimal("175.00"),
                new BigDecimal("80.00"),
                "moderate",
                "maintain",
                "America/New_York"
        );
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/users/me — happy path
    // -------------------------------------------------------------------------

    @Test
    void getProfile_authenticatedUser_returns200WithProfile() throws Exception {
        when(userService.getProfile(userId)).thenReturn(sampleProfile);

        mockMvc.perform(get("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + validJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sex").value("male"))
                .andExpect(jsonPath("$.date_of_birth").value("1990-06-15"))
                .andExpect(jsonPath("$.height_cm").value(175.00))
                .andExpect(jsonPath("$.weight_kg").value(80.00))
                .andExpect(jsonPath("$.activity").value("moderate"))
                .andExpect(jsonPath("$.goal").value("maintain"))
                .andExpect(jsonPath("$.timezone").value("America/New_York"));
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/users/me — 404 when profile not yet created
    // -------------------------------------------------------------------------

    @Test
    void getProfile_profileNotFound_returns404() throws Exception {
        when(userService.getProfile(userId))
                .thenThrow(new ResponseStatusException(NOT_FOUND, "Profile not found"));

        mockMvc.perform(get("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + validJwt))
                .andExpect(status().isNotFound());
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/users/me — 401 when no JWT is present
    // -------------------------------------------------------------------------

    @Test
    void getProfile_noJwt_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized());
    }

    // -------------------------------------------------------------------------
    // PUT /api/v1/users/me — happy path (create or update)
    // -------------------------------------------------------------------------

    @Test
    void upsertProfile_validDto_returns200WithSavedProfile() throws Exception {
        when(userService.upsertProfile(eq(userId), any(UserProfileDto.class)))
                .thenReturn(sampleProfile);

        mockMvc.perform(put("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + validJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleProfile)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sex").value("male"))
                .andExpect(jsonPath("$.date_of_birth").value("1990-06-15"))
                .andExpect(jsonPath("$.activity").value("moderate"))
                .andExpect(jsonPath("$.goal").value("maintain"))
                .andExpect(jsonPath("$.timezone").value("America/New_York"));
    }

    // -------------------------------------------------------------------------
    // PUT /api/v1/users/me — 401 when no JWT is present
    // -------------------------------------------------------------------------

    @Test
    void upsertProfile_noJwt_returns401() throws Exception {
        mockMvc.perform(put("/api/v1/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleProfile)))
                .andExpect(status().isUnauthorized());
    }

    // -------------------------------------------------------------------------
    // PUT /api/v1/users/me — 422 when required fields are null
    // -------------------------------------------------------------------------

    @Test
    void upsertProfile_nullRequiredFields_returns422WithFieldErrors() throws Exception {
        // All fields null — every @NotNull constraint should fire
        String nullBody = "{}";

        mockMvc.perform(put("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + validJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(nullBody))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.fields").isArray());
    }

    // -------------------------------------------------------------------------
    // PUT /api/v1/users/me — 422 when date_of_birth has an invalid format
    // -------------------------------------------------------------------------

    @Test
    void upsertProfile_invalidDateOfBirthFormat_returns422() throws Exception {
        // Jackson cannot deserialise "not-a-date" into LocalDate — results in 422
        String badDateBody = """
                {
                  "sex": "female",
                  "date_of_birth": "not-a-date",
                  "height_cm": 165.00,
                  "weight_kg": 60.00,
                  "activity": "light",
                  "goal": "lose",
                  "timezone": "UTC"
                }
                """;

        mockMvc.perform(put("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + validJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(badDateBody))
                .andExpect(status().isUnprocessableEntity());
    }

    // -------------------------------------------------------------------------
    // Ownership isolation — user B's JWT cannot access user A's data
    //
    // Because the userId is always extracted from the JWT principal (never from the
    // request body or path), there is no HTTP surface for user B to forge a request
    // that reads or writes user A's profile. These tests verify that two different
    // JWTs produce two independent service calls scoped to their respective user IDs.
    // -------------------------------------------------------------------------

    @Test
    void getProfile_differentUserJwt_callsServiceWithCorrectUserId() throws Exception {
        // User B has a completely separate JWT and therefore a separate userId.
        // Only stub userIdB — if the controller mistakenly passes userIdA, the mock
        // returns null and the test will fail on the 200 assertion, proving isolation.
        UUID userIdB = UUID.randomUUID();
        String jwtB  = jwtService.generateAccessToken(userIdB, "apple");

        UserProfileDto profileB = new UserProfileDto(
                "female",
                LocalDate.of(1995, 3, 20),
                new BigDecimal("162.00"),
                new BigDecimal("58.00"),
                "light",
                "lose",
                "Europe/London"
        );

        when(userService.getProfile(userIdB)).thenReturn(profileB);

        mockMvc.perform(get("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sex").value("female"))
                .andExpect(jsonPath("$.timezone").value("Europe/London"));
    }

    @Test
    void upsertProfile_differentUserJwt_callsServiceWithCorrectUserId() throws Exception {
        // Same ownership isolation check for PUT — user B's JWT must scope the upsert
        // to userIdB only; any cross-user call would return null from the mock and fail.
        UUID userIdB = UUID.randomUUID();
        String jwtB  = jwtService.generateAccessToken(userIdB, "apple");

        UserProfileDto profileB = new UserProfileDto(
                "female",
                LocalDate.of(1995, 3, 20),
                new BigDecimal("162.00"),
                new BigDecimal("58.00"),
                "light",
                "lose",
                "Europe/London"
        );

        when(userService.upsertProfile(eq(userIdB), any(UserProfileDto.class))).thenReturn(profileB);

        mockMvc.perform(put("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(profileB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sex").value("female"));
    }
}
