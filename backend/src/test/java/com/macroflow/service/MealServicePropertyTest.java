package com.macroflow.service;

import com.macroflow.dto.SyncOperationDto;
import com.macroflow.dto.SyncResultDto;
import com.macroflow.model.MealEntry;
import com.macroflow.repository.FoodRepository;
import com.macroflow.repository.MealEntryRepository;
import com.macroflow.repository.SavedMealRepository;
import com.macroflow.repository.UserProfileRepository;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for {@link MealService} sync conflict resolution using jqwik.
 *
 * <p><b>Property 26 — Conflict resolution by latest timestamp:</b>
 * For any two upsert operations targeting the same {@code clientId}, the one with the
 * later {@code clientTs} must win. Specifically:
 * <ul>
 *   <li>If the incoming op has a strictly later {@code clientTs} than the stored entry,
 *       the entry is updated and the result is {@code ok}.</li>
 *   <li>If the incoming op has an equal or earlier {@code clientTs}, the entry is NOT
 *       updated and the result is {@code conflict_ignored}.</li>
 * </ul>
 *
 * <p>All repository collaborators are mocked so no database is required.
 */
class MealServicePropertyTest {

    // -------------------------------------------------------------------------
    // Property 26 — Conflict resolution by latest timestamp
    // -------------------------------------------------------------------------

    /**
     * When an incoming upsert has a strictly later {@code clientTs} than the stored
     * entry, the result must be {@code ok} and the entry must be saved with the new data.
     *
     * <p>The offset is always positive (1–3600 seconds) so the incoming ts is always newer.
     */
    @Property(tries = 100)
    @Label("Property 26: Upsert with newer clientTs wins and returns ok")
    void upsertWithNewerTimestampWins(@ForAll @IntRange(min = 1, max = 3600) int offsetSeconds) {
        UUID userId     = UUID.randomUUID();
        String clientId = UUID.randomUUID().toString();
        Instant stored  = Instant.parse("2026-01-10T12:00:00Z");
        Instant newer   = stored.plusSeconds(offsetSeconds); // always strictly after stored

        MealEntry existing = buildEntry(userId, clientId, stored);

        MealEntryRepository mealRepo = mock(MealEntryRepository.class);
        when(mealRepo.findByClientId(clientId)).thenReturn(Optional.of(existing));
        when(mealRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        MealService service = buildService(mealRepo);
        SyncOperationDto op = buildUpsertOp(clientId, newer);

        List<SyncResultDto> results = service.processBatch(userId, List.of(op));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).status()).isEqualTo("ok");
        // Entry must have been saved with the new timestamp
        verify(mealRepo).save(argThat(e -> e.getClientTs().equals(newer)));
    }

    /**
     * When an incoming upsert has an equal or earlier {@code clientTs} than the stored
     * entry, the result must be {@code conflict_ignored} and the entry must NOT be saved.
     *
     * <p>The offset is 0–3600 seconds subtracted from the stored ts, so the incoming
     * ts is always equal to or before the stored value.
     */
    @Property(tries = 100)
    @Label("Property 26b: Upsert with older or equal clientTs is ignored")
    void upsertWithOlderTimestampIsIgnored(@ForAll @IntRange(min = 0, max = 3600) int offsetSeconds) {
        UUID userId     = UUID.randomUUID();
        String clientId = UUID.randomUUID().toString();
        Instant stored  = Instant.parse("2026-01-10T12:00:00Z");
        Instant older   = stored.minusSeconds(offsetSeconds); // equal or before stored

        MealEntry existing = buildEntry(userId, clientId, stored);

        MealEntryRepository mealRepo = mock(MealEntryRepository.class);
        when(mealRepo.findByClientId(clientId)).thenReturn(Optional.of(existing));

        MealService service = buildService(mealRepo);
        SyncOperationDto op = buildUpsertOp(clientId, older);

        List<SyncResultDto> results = service.processBatch(userId, List.of(op));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).status()).isEqualTo("conflict_ignored");
        // Entry must NOT have been saved — the stored version is already newer
        verify(mealRepo, never()).save(any());
    }

    /**
     * Operations targeting different {@code clientId}s must be processed independently.
     * The result list must have the same size as the input list and preserve order.
     */
    @Property(tries = 100)
    @Label("Property 26c: Batch results preserve insertion order")
    void batchResultsPreserveOrder(@ForAll @IntRange(min = 1, max = 10) int batchSize) {
        UUID userId = UUID.randomUUID();

        MealEntryRepository mealRepo = mock(MealEntryRepository.class);
        // No existing entries — all ops create new entries
        when(mealRepo.findByClientId(anyString())).thenReturn(Optional.empty());
        when(mealRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        MealService service = buildService(mealRepo);

        List<SyncOperationDto> ops = java.util.stream.IntStream.range(0, batchSize)
                .mapToObj(i -> buildUpsertOp(
                        UUID.randomUUID().toString(),
                        Instant.parse("2026-01-10T12:00:00Z").plusSeconds(i)))
                .toList();

        List<SyncResultDto> results = service.processBatch(userId, ops);

        assertThat(results).hasSize(batchSize);
        // Each result must correspond to the op at the same index (insertion order preserved)
        for (int i = 0; i < batchSize; i++) {
            assertThat(results.get(i).clientId()).isEqualTo(ops.get(i).clientId());
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Build a {@link MealEntry} with the given owner, clientId, and clientTs. */
    private MealEntry buildEntry(UUID userId, String clientId, Instant clientTs) {
        MealEntry e = new MealEntry();
        e.setUserId(userId);
        e.setClientId(clientId);
        e.setClientTs(clientTs);
        e.setFoodId(UUID.randomUUID());
        e.setLogDate(LocalDate.now()); // within 7-day window
        e.setServings(BigDecimal.ONE);
        e.setProteinG(BigDecimal.valueOf(30));
        e.setCarbsG(BigDecimal.valueOf(40));
        e.setFatG(BigDecimal.valueOf(10));
        e.setCalories(BigDecimal.valueOf(370));
        return e;
    }

    /**
     * Build a valid upsert {@link SyncOperationDto}.
     * logDate is set to today so it always falls within the 7-day edit window.
     */
    private SyncOperationDto buildUpsertOp(String clientId, Instant clientTs) {
        return new SyncOperationDto(
                clientId, "upsert", clientTs,
                UUID.randomUUID(),       // foodId
                LocalDate.now(),         // logDate — always within window
                null,                    // mealName
                BigDecimal.ONE,          // servings
                BigDecimal.valueOf(30),  // proteinG
                BigDecimal.valueOf(40),  // carbsG
                BigDecimal.valueOf(10),  // fatG
                BigDecimal.valueOf(370)  // calories
        );
    }

    /**
     * Build a {@link MealService} with the given meal entry repository and no-op mocks
     * for the other collaborators. UserProfileRepository returns empty so timezone
     * defaults to UTC — sufficient for these conflict-resolution tests.
     */
    private MealService buildService(MealEntryRepository mealRepo) {
        SavedMealRepository savedMealRepo = mock(SavedMealRepository.class);
        FoodRepository foodRepo           = mock(FoodRepository.class);
        UserProfileRepository profileRepo = mock(UserProfileRepository.class);
        when(profileRepo.findByUserId(any())).thenReturn(Optional.empty());
        return new MealService(mealRepo, savedMealRepo, foodRepo, profileRepo);
    }
}
