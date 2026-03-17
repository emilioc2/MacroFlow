package com.macroflow.config;

import com.macroflow.security.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security configuration for the MacroFlow API.
 *
 * Stateless JWT-based auth:
 * - CSRF disabled (no session cookies — stateless JWT API)
 * - Session creation disabled (STATELESS)
 * - Form login and HTTP Basic disabled
 * - JwtAuthenticationFilter runs before UsernamePasswordAuthenticationFilter
 * - Custom AuthenticationEntryPoint returns 401 JSON (not Spring's default 403)
 *
 * Public endpoints: POST /auth/token, POST /auth/refresh, DELETE /auth/session, /actuator/**
 * All other endpoints require a valid JWT.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Stateless API — no CSRF protection needed
            .csrf(AbstractHttpConfigurer::disable)
            // Never create an HTTP session; each request is authenticated via JWT
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // Disable form login and HTTP Basic — not applicable for a mobile API
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            // Return 401 JSON when an unauthenticated request hits a protected endpoint.
            // Without this, Spring Security defaults to 403 for anonymous requests.
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json");
                    response.getWriter().write(
                        "{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"Authentication required\"}"
                    );
                })
            )
            .authorizeHttpRequests(auth -> auth
                // Token exchange and refresh are public — the client has no JWT yet
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/token").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/refresh").permitAll()
                // Sign-out revokes the refresh token — no JWT required (client may have lost it)
                .requestMatchers(HttpMethod.DELETE, "/api/v1/auth/session").permitAll()
                // Actuator endpoints are public for health checks and metrics scraping
                .requestMatchers("/actuator/**").permitAll()
                // Everything else requires a valid JWT
                .anyRequest().authenticated()
            )
            // Validate JWT before Spring Security's own auth processing
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
