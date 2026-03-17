package com.macroflow.service;

import com.macroflow.dto.*;
import com.macroflow.model.Food;
import com.macroflow.model.MealEntry;
import com.macroflow.model.SavedMeal;
import com.macroflow.model.SavedMealItem;
import com.macroflow.repository.FoodRepository;
import com.macroflow.repository.MealEntryRepository;
import com.macroflow.repository.SavedMealRepository;
import com.macroflow.repository.UserProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Business logic for meal entries, batch sync, and saved meals.
 *
 * <h2>Sync semantics</h2>
 * <p>The batch sync endpoint is idempotent: each operation is keyed by a
 * client-generated {@code clientId}. If an entry with the same {@code clientId}
 * already exists, the operation with the later {@code clientTs} wins
 * (last-write-wins). Operations with an older {@code clientTs} return
 * {@code conflict_ignored}.
 *
 * <h2>7-day edit window</h2>
 * <p>Upsert and delete operations are rejected with {@code out_of_window} if the
 * entry's {@code logDate} falls outside the rolling 7-day window in the user's
 * stored timezone. This prevents accidental edits to old history.
 *
 * <h2>Saved meal delete semantics</h2>
 * <p>Deleting a saved meal cascades to {@code saved_meal_item} but does NOT affect
 * previously logged {@code meal_entry} rows — those hold a direct FK to {@code food}.
 */
@Service
public class MealService {

    private static final Logger log = LoggerFactory.getLogger(MealService.class);

    /** Maximum number of days back a user can edit or delete a meal entry. */
    private static final int EDIT_WINDOW_DAYS = 7;

    private final MealEntryRepository mealEntryRepository;
    private final SavedMealRepository savedMealRepository;
    private final FoodRepository foodRepository;
    private final UserProfileRepository userProfileRepository;

    public MealService(MealEntryRepository mealEntryRepository,
                       SavedMealRepository savedMealRepository,
                       FoodRepository foodRepository,
                       UserProfileRepository userProfileRepository) {
        this.mealEntryRepository = mealEntryRepository;
        this.savedMealRepository = savedMealRepository;
        this.foodRepository = foodRepository;
        this.userProfileRepository = userProfileRepository;
    }

    // -------------------------------------------------------------------------
    // 6.1 — GET /meal-entries (paginated, date-range)
    // -------------------------------------------------------------------------

    /**
     * Return a paginated page of non-deleted meal entries for the given user within
     * the inclusive date range.
     *
     * @param userId    the authenticated user's UUID (from JWT principal)
     * @param startDate inclusive start date
     * @param endDate   inclusive end date
     * @param page      zero-based page number
     * @param pageSize  entries per page; capped at 100 to prevent unbounded queries
     */
    @Transactional(readOnly = true)
    public MealEntryPageDto getMealEntries(UUID userId, LocalDate startDate, LocalDate endDate,
                                           int page, int pageSize) {
        int cappedSize = Math.min(pageSize, 100);
        Page<MealEntry> result = mealEntryRepository.findByUserIdAndDateRange(
                userId, startDate, endDate, PageRequest.of(page, cappedSize));
        List<MealEntryDto> content = result.getContent().stream()
                .map(this::toMealEntryDto)
                .toList();
        return new MealEntryPageDto(content, result.getTotalElements(),
                result.getNumber(), cappedSize, result.getTotalPages());
    }

    // -------------------------------------------------------------------------
    // 6.2 — POST /sync/batch (idempotent batch upsert/delete)
    // -------------------------------------------------------------------------

    /**
     * Process a batch of sync operations in order, returning one result per operation.
     *
     * <p>Conflict resolution (last-write-wins per {@code clientId}):
     * <ul>
     *   <li>If no entry exists for the {@code clientId}, the operation is applied.</li>
     *   <li>If an entry exists and the incoming {@code clientTs} is strictly later, the
     *       entry is updated.</li>
     *   <li>If the incoming {@code clientTs} is equal to or earlier than the stored value,
     *       the operation is skipped and {@code conflict_ignored} is returned.</li>
     * </ul>
     *
     * <p>7-day edit window: upsert and delete operations whose {@code logDate} falls
     * outside the rolling 7-day window (in the user's stored timezone) return
     * {@code out_of_window}. The window is computed once per batch call.
     *
     * @param userId     the authenticated user's UUID (from JWT principal)
     * @param operations ordered list of sync operations from the client
     */
    @Transactional
    public List<SyncResultDto> processBatch(UUID userId, List<SyncOperationDto> operations) {
        // Resolve the user's timezone once; fall back to UTC if profile is absent
        ZoneId userZone = userProfileRepository.findByUserId(userId)
                .map(p -> {
                    try { return ZoneId.of(p.getTimezone()); }
                    catch (Exception e) { return ZoneId.of("UTC"); }
                })
                .orElse(ZoneId.of("UTC"));

        LocalDate today = LocalDate.now(userZone);
        LocalDate windowStart = today.minusDays(EDIT_WINDOW_DAYS - 1);

        List<SyncResultDto> results = new ArrayList<>(operations.size());

        for (SyncOperationDto op : operations) {
            try {
                results.add(processOne(userId, op, windowStart, today));
            } catch (Exception e) {
                log.error("Unexpected error processing sync op clientId={}: {}", op.clientId(), e.getMessage(), e);
                results.add(new SyncResultDto(op.clientId(), "error", e.getMessage()));
            }
        }
        return results;
    }

    /**
     * Process a single sync operation and return its result.
     *
     * <p>Separated from the batch loop so each operation's exception is isolated —
     * one bad op does not abort the rest of the batch.
     */
    private SyncResultDto processOne(UUID userId, SyncOperationDto op,
                                     LocalDate windowStart, LocalDate today) {
        String clientId = op.clientId();

        // Validate op type early
        if (!"upsert".equals(op.op()) && !"delete".equals(op.op())) {
            return new SyncResultDto(clientId, "error", "Unknown op: " + op.op());
        }

        // 7-day window check (applies to both upsert and delete)
        LocalDate logDate = op.logDate();
        if (logDate != null && (logDate.isBefore(windowStart) || logDate.isAfter(today))) {
            log.debug("Out-of-window op clientId={} logDate={} window=[{},{}]",
                    clientId, logDate, windowStart, today);
            return new SyncResultDto(clientId, "out_of_window",
                    "logDate " + logDate + " is outside the 7-day edit window");
        }

        MealEntry existing = mealEntryRepository.findByClientId(clientId).orElse(null);

        if ("delete".equals(op.op())) {
            return processDelete(userId, op, existing);
        } else {
            return processUpsert(userId, op, existing);
        }
    }

    /**
     * Apply an upsert operation.
     *
     * <p>If no entry exists, a new one is created. If one exists, last-write-wins
     * by {@code clientTs}: newer wins, equal-or-older is ignored.
     */
    private SyncResultDto processUpsert(UUID userId, SyncOperationDto op, MealEntry existing) {
        String clientId = op.clientId();

        if (existing != null) {
            // Conflict resolution: only update if incoming ts is strictly later
            if (!op.clientTs().isAfter(existing.getClientTs())) {
                log.debug("Conflict ignored clientId={} stored={} incoming={}",
                        clientId, existing.getClientTs(), op.clientTs());
                return new SyncResultDto(clientId, "conflict_ignored", null);
            }
            // Incoming is newer — update in place
            applyUpsertFields(existing, op);
            mealEntryRepository.save(existing);
        } else {
            // New entry
            MealEntry entry = new MealEntry();
            entry.setUserId(userId);
            entry.setClientId(clientId);
            applyUpsertFields(entry, op);
            mealEntryRepository.save(entry);
        }
        return new SyncResultDto(clientId, "ok", null);
    }

    /**
     * Apply a delete operation (soft-delete).
     *
     * <p>If no entry exists, the delete is treated as a no-op (idempotent).
     * Conflict resolution applies: a delete with an older {@code clientTs} is ignored.
     */
    private SyncResultDto processDelete(UUID userId, SyncOperationDto op, MealEntry existing) {
        String clientId = op.clientId();

        if (existing == null) {
            // Nothing to delete — idempotent no-op
            return new SyncResultDto(clientId, "ok", null);
        }
        if (!op.clientTs().isAfter(existing.getClientTs())) {
            return new SyncResultDto(clientId, "conflict_ignored", null);
        }
        existing.setDeleted(true);
        existing.setClientTs(op.clientTs());
        mealEntryRepository.save(existing);
        return new SyncResultDto(clientId, "ok", null);
    }

    /**
     * Copy upsert fields from the DTO onto the entity.
     * Called for both new entries and conflict-winning updates.
     */
    private void applyUpsertFields(MealEntry entry, SyncOperationDto op) {
        entry.setFoodId(op.foodId());
        entry.setLogDate(op.logDate());
        entry.setMealName(op.mealName());
        entry.setServings(op.servings());
        entry.setProteinG(op.proteinG());
        entry.setCarbsG(op.carbsG());
        entry.setFatG(op.fatG());
        entry.setCalories(op.calories());
        entry.setClientTs(op.clientTs());
        entry.setDeleted(false);
    }

    // -------------------------------------------------------------------------
    // 6.3 — Saved meals CRUD
    // -------------------------------------------------------------------------

    /**
     * Return all saved meals owned by the authenticated user.
     *
     * @param userId the authenticated user's UUID (from JWT principal)
     */
    @Transactional(readOnly = true)
    public List<SavedMealDto> getSavedMeals(UUID userId) {
        return savedMealRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toSavedMealDto)
                .toList();
    }

    /**
     * Create a new saved meal owned by the authenticated user.
     *
     * @param userId the authenticated user's UUID (from JWT principal)
     * @param req    validated request body
     */
    @Transactional
    public SavedMealDto createSavedMeal(UUID userId, SavedMealRequest req) {
        SavedMeal meal = new SavedMeal();
        meal.setUserId(userId);
        meal.setName(req.name());
        meal.setItems(buildItems(meal, req.items()));
        return toSavedMealDto(savedMealRepository.save(meal));
    }

    /**
     * Update an existing saved meal. Ownership is enforced — 404 is returned if the
     * meal doesn't exist or belongs to a different user (intentionally vague).
     *
     * <p>The item list is fully replaced on every update — no partial patching.
     *
     * @param userId the authenticated user's UUID (from JWT principal)
     * @param mealId the saved meal's UUID
     * @param req    validated request body
     */
    @Transactional
    public SavedMealDto updateSavedMeal(UUID userId, UUID mealId, SavedMealRequest req) {
        SavedMeal meal = savedMealRepository.findByIdAndUserId(mealId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Saved meal not found"));

        meal.setName(req.name());
        // Clear and replace — orphanRemoval on the entity handles deletion of old items
        meal.getItems().clear();
        meal.getItems().addAll(buildItems(meal, req.items()));
        return toSavedMealDto(savedMealRepository.save(meal));
    }

    /**
     * Delete a saved meal. Ownership is enforced.
     *
     * <p>Deletion cascades to {@code saved_meal_item} rows but does NOT affect any
     * previously logged {@code meal_entry} rows — those hold a direct FK to {@code food}.
     *
     * @param userId the authenticated user's UUID (from JWT principal)
     * @param mealId the saved meal's UUID
     */
    @Transactional
    public void deleteSavedMeal(UUID userId, UUID mealId) {
        SavedMeal meal = savedMealRepository.findByIdAndUserId(mealId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Saved meal not found"));
        savedMealRepository.delete(meal);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Build the item entity list from request items, linking each back to the parent meal. */
    private List<SavedMealItem> buildItems(SavedMeal meal, List<SavedMealRequest.Item> reqItems) {
        return reqItems.stream().map(i -> {
            SavedMealItem item = new SavedMealItem();
            item.setSavedMeal(meal);
            item.setFoodId(i.foodId());
            item.setServings(BigDecimal.valueOf(i.servings()));
            return item;
        }).toList();
    }

    /**
     * Map a {@link MealEntry} to a {@link MealEntryDto}.
     *
     * <p>The food name is fetched for display convenience. If the food has been
     * disowned (owner_id = null), the name is still available because food rows
     * are never hard-deleted.
     */
    private MealEntryDto toMealEntryDto(MealEntry entry) {
        String foodName = foodRepository.findById(entry.getFoodId())
                .map(Food::getName)
                .orElse("Unknown food");

        return new MealEntryDto(
                entry.getId(),
                entry.getFoodId(),
                foodName,
                entry.getLogDate(),
                entry.getLoggedAt(),
                entry.getMealName(),
                entry.getServings().doubleValue(),
                entry.getProteinG().doubleValue(),
                entry.getCarbsG().doubleValue(),
                entry.getFatG().doubleValue(),
                entry.getCalories().doubleValue(),
                entry.getClientId(),
                entry.getClientTs(),
                entry.isDeleted()
        );
    }

    /** Map a {@link SavedMeal} entity to a {@link SavedMealDto}. */
    private SavedMealDto toSavedMealDto(SavedMeal meal) {
        List<SavedMealItemDto> items = meal.getItems().stream()
                .map(i -> new SavedMealItemDto(i.getId(), i.getFoodId(), i.getServings().doubleValue()))
                .toList();
        return new SavedMealDto(meal.getId(), meal.getName(), meal.getCreatedAt(), items);
    }
}
