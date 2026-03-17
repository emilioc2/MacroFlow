package com.macroflow.service;

import com.macroflow.dto.UserPreferencesDto;
import com.macroflow.dto.UserProfileDto;
import com.macroflow.model.UserPreferences;
import com.macroflow.model.UserProfile;
import com.macroflow.repository.UserPreferencesRepository;
import com.macroflow.repository.UserProfileRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Business logic for user profile operations.
 *
 * <p>Ownership is enforced implicitly: every method accepts the {@code userId} extracted
 * from the JWT principal by the controller. The userId is never taken from the request
 * body, so a user can only ever read or modify their own profile.
 */
@Service
public class UserService {

    private final UserProfileRepository userProfileRepository;
    private final UserPreferencesRepository userPreferencesRepository;

    public UserService(UserProfileRepository userProfileRepository,
                       UserPreferencesRepository userPreferencesRepository) {
        this.userProfileRepository = userProfileRepository;
        this.userPreferencesRepository = userPreferencesRepository;
    }

    /**
     * Retrieve the authenticated user's profile.
     *
     * @param userId the authenticated user's UUID (from JWT principal)
     * @return the user's profile as a DTO
     * @throws ResponseStatusException 404 if the user has not yet created a profile
     */
    @Transactional(readOnly = true)
    public UserProfileDto getProfile(UUID userId) {
        UserProfile profile = userProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Profile not found"));
        return toDto(profile);
    }

    /**
     * Create or update the authenticated user's profile (upsert semantics).
     *
     * <p>If no profile exists for this user, a new one is created. If one already
     * exists, all fields are overwritten with the values from the DTO.
     *
     * <p>Ownership is guaranteed because {@code userId} always comes from the JWT
     * principal — never from the request body.
     *
     * @param userId the authenticated user's UUID (from JWT principal)
     * @param dto    the validated profile data from the request body
     * @return the saved profile as a DTO
     */
    @Transactional
    public UserProfileDto upsertProfile(UUID userId, UserProfileDto dto) {
        // Load existing profile or create a new one for this user
        UserProfile profile = userProfileRepository.findByUserId(userId)
                .orElseGet(() -> new UserProfile(userId));

        // Apply all fields from the DTO; updated_at is always set server-side
        profile.setSex(dto.sex());
        profile.setDateOfBirth(dto.dateOfBirth());
        profile.setHeightCm(dto.heightCm());
        profile.setWeightKg(dto.weightKg());
        profile.setActivity(dto.activity());
        profile.setGoal(dto.goal());
        // Default to UTC if the client somehow sends null despite @NotNull (defensive)
        profile.setTimezone(dto.timezone() != null ? dto.timezone() : "UTC");
        profile.setUpdatedAt(OffsetDateTime.now());

        UserProfile saved = userProfileRepository.save(profile);
        return toDto(saved);
    }

    /**
     * Retrieve the authenticated user's preferences.
     *
     * <p>If no preferences row exists yet, returns defaults without writing to the DB.
     * This keeps GET idempotent and avoids creating orphaned rows for users who never
     * explicitly set preferences.
     *
     * @param userId the authenticated user's UUID (from JWT principal)
     * @return the user's preferences as a DTO, or defaults if not yet set
     */
    @Transactional(readOnly = true)
    public UserPreferencesDto getPreferences(UUID userId) {
        return userPreferencesRepository.findByUserId(userId)
                .map(this::toPreferencesDto)
                .orElse(defaultPreferences());
    }

    /**
     * Create or update the authenticated user's preferences (upsert semantics).
     *
     * <p>Ownership is guaranteed because {@code userId} always comes from the JWT
     * principal — never from the request body.
     *
     * @param userId the authenticated user's UUID (from JWT principal)
     * @param dto    the validated preferences data from the request body
     * @return the saved preferences as a DTO
     */
    @Transactional
    public UserPreferencesDto upsertPreferences(UUID userId, UserPreferencesDto dto) {
        UserPreferences prefs = userPreferencesRepository.findByUserId(userId)
                .orElseGet(() -> new UserPreferences(userId));

        prefs.setRecentlyLoggedMax(dto.recentlyLoggedMax());
        prefs.setTutorialShown(dto.tutorialShown());
        prefs.setTheme(dto.theme());

        return toPreferencesDto(userPreferencesRepository.save(prefs));
    }

    /**
     * Map a {@link UserProfile} entity to a {@link UserProfileDto}.
     * Entities are never returned directly from the service layer.
     */
    private UserProfileDto toDto(UserProfile profile) {
        return new UserProfileDto(
                profile.getSex(),
                profile.getDateOfBirth(),
                profile.getHeightCm(),
                profile.getWeightKg(),
                profile.getActivity(),
                profile.getGoal(),
                profile.getTimezone()
        );
    }

    /**
     * Map a {@link UserPreferences} entity to a {@link UserPreferencesDto}.
     * Entities are never returned directly from the service layer.
     */
    private UserPreferencesDto toPreferencesDto(UserPreferences prefs) {
        return new UserPreferencesDto(
                prefs.getRecentlyLoggedMax(),
                prefs.getTutorialShown(),
                prefs.getTheme()
        );
    }

    /** Returns the default preferences DTO used when no row exists yet. */
    private UserPreferencesDto defaultPreferences() {
        return new UserPreferencesDto(10, false, "system");
    }
}
