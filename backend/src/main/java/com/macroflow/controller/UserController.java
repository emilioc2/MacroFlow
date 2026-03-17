package com.macroflow.controller;

import com.macroflow.dto.UserPreferencesDto;
import com.macroflow.dto.UserProfileDto;
import com.macroflow.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Thin controller for user profile endpoints.
 *
 * <p>All business logic lives in {@link UserService}. This controller only handles
 * request validation and principal extraction — it never touches the repository or
 * JPA entities directly.
 *
 * <p>Ownership is enforced implicitly: the {@code userId} is always extracted from the
 * JWT principal set by {@link com.macroflow.security.JwtAuthenticationFilter}. It is
 * never read from the request body or path, so a user can only access their own profile.
 *
 * <p>Base path: /api/v1/users
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * GET /api/v1/users/me
     *
     * <p>Returns the authenticated user's profile. Returns 404 if the user has not
     * yet submitted a profile via PUT.
     *
     * @param userId the authenticated user's UUID, injected from the JWT principal
     */
    @GetMapping("/me")
    public ResponseEntity<UserProfileDto> getProfile(@AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(userService.getProfile(userId));
    }

    /**
     * PUT /api/v1/users/me
     *
     * <p>Creates or updates the authenticated user's profile (upsert). The request body
     * is validated with {@code @Valid} before reaching the service layer; any constraint
     * violation returns 422 via the global exception handler.
     *
     * @param userId the authenticated user's UUID, injected from the JWT principal
     * @param dto    the validated profile payload from the request body
     */
    @PutMapping("/me")
    public ResponseEntity<UserProfileDto> upsertProfile(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody UserProfileDto dto) {
        return ResponseEntity.ok(userService.upsertProfile(userId, dto));
    }

    /**
     * GET /api/v1/users/me/preferences
     *
     * <p>Returns the authenticated user's preferences. Always returns 200 — if no
     * preferences row exists yet, defaults are returned without creating a DB row.
     *
     * @param userId the authenticated user's UUID, injected from the JWT principal
     */
    @GetMapping("/me/preferences")
    public ResponseEntity<UserPreferencesDto> getPreferences(@AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(userService.getPreferences(userId));
    }

    /**
     * PUT /api/v1/users/me/preferences
     *
     * <p>Creates or updates the authenticated user's preferences (upsert). The request
     * body is validated with {@code @Valid}; any constraint violation returns 422 via
     * the global exception handler.
     *
     * @param userId the authenticated user's UUID, injected from the JWT principal
     * @param dto    the validated preferences payload from the request body
     */
    @PutMapping("/me/preferences")
    public ResponseEntity<UserPreferencesDto> upsertPreferences(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody UserPreferencesDto dto) {
        return ResponseEntity.ok(userService.upsertPreferences(userId, dto));
    }
}
