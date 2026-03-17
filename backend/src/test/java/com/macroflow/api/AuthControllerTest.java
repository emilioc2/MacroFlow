package com.macroflow.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.macroflow.dto.AuthResponse;
import com.macroflow.dto.RefreshRequest;
import com.macroflow.dto.RevokeRequest;
import com.macroflow.dto.TokenRequest;
import com.macroflow.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration-style controller tests using the full Spring context with MockMvc.
 *
 * AuthService is mocked so no real provider verification or DB access occurs.
 * The test profile supplies H2 + JWT config so no external services are needed.
 *
 * Using @SpringBootTest + @AutoConfigureMockMvc rather than @WebMvcTest because
 * the controller lives in a different package from the main application class,
 * which causes @WebMvcTest component-scan issues.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    // Mock AuthService so tests don't hit the DB or real provider APIs
    @MockBean
    AuthService authService;

    // -------------------------------------------------------------------------
    // POST /api/v1/auth/token — happy path
    // -------------------------------------------------------------------------

    @Test
    void tokenExchange_happyPath_returns200WithTokens() throws Exception {
        AuthResponse fakeResponse = new AuthResponse("access.jwt.token", "refresh-uuid");
        when(authService.exchangeProviderToken(eq("google"), any())).thenReturn(fakeResponse);

        TokenRequest body = new TokenRequest("google", "google-id-token-value");

        mockMvc.perform(post("/api/v1/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access.jwt.token"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-uuid"));
    }

    // -------------------------------------------------------------------------
    // POST /api/v1/auth/token — 422 when provider field is blank
    // -------------------------------------------------------------------------

    @Test
    void tokenExchange_blankProvider_returns422WithFieldError() throws Exception {
        // provider is blank — Bean Validation should reject with 422
        TokenRequest body = new TokenRequest("", "some-token");

        mockMvc.perform(post("/api/v1/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.fields[0].field").value("provider"));
    }

    // -------------------------------------------------------------------------
    // POST /api/v1/auth/refresh — 401 when token is invalid/expired
    // -------------------------------------------------------------------------

    @Test
    void refresh_invalidToken_returns401() throws Exception {
        // BadCredentialsException is a Spring Security AuthenticationException subclass;
        // GlobalExceptionHandler maps it to 401.
        when(authService.refreshTokens(any()))
                .thenThrow(new BadCredentialsException("Refresh token is expired or revoked"));

        RefreshRequest body = new RefreshRequest("expired-or-invalid-token");

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized());
    }

    // -------------------------------------------------------------------------
    // DELETE /api/v1/auth/session — 204 on successful revocation
    // -------------------------------------------------------------------------

    @Test
    void revokeSession_validToken_returns204() throws Exception {
        doNothing().when(authService).revokeRefreshToken(any());

        RevokeRequest body = new RevokeRequest("some-refresh-token");

        mockMvc.perform(delete("/api/v1/auth/session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isNoContent());
    }

    // -------------------------------------------------------------------------
    // DELETE /api/v1/auth/account — 401 when no JWT is present
    // -------------------------------------------------------------------------

    @Test
    void deleteAccount_noJwt_returns401() throws Exception {
        // No Authorization header — JwtAuthenticationFilter sets no principal
        // Spring Security rejects the request with 401
        mockMvc.perform(delete("/api/v1/auth/account"))
                .andExpect(status().isUnauthorized());
    }
}
