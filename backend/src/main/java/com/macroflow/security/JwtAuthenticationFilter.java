package com.macroflow.security;

import io.jsonwebtoken.Claims;
import io.sentry.Sentry;
import io.sentry.protocol.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.UUID;

/**
 * Extracts and validates the JWT from the Authorization: Bearer header.
 *
 * On a valid token, sets a {@link UsernamePasswordAuthenticationToken} in the
 * {@link SecurityContextHolder} with the userId (UUID) as the principal.
 * On an invalid or missing token, does nothing — Spring Security's access control
 * will return 401 for protected endpoints.
 *
 * This filter never throws; all failures are silent so the filter chain continues.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            String token = authHeader.substring(BEARER_PREFIX.length());
            Claims claims = jwtService.validateAndExtractClaims(token);

            // Only set the security context if the token is valid and no auth is already present
            if (claims != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UUID userId = jwtService.extractUserId(claims);

                // Principal is the userId UUID — controllers access it via @AuthenticationPrincipal
                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(userId, null, Collections.emptyList());
                SecurityContextHolder.getContext().setAuthentication(auth);

                // Attach an opaque userId to Sentry so errors are traceable without PII —
                // the UUID is not linked to any personal data in Sentry's dashboard
                User sentryUser = new User();
                sentryUser.setId(userId.toString());
                Sentry.setUser(sentryUser);
            }
        }

        filterChain.doFilter(request, response);
    }
}
