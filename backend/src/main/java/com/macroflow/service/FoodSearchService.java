package com.macroflow.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.macroflow.constants.ExternalApiConstants;
import com.macroflow.dto.CustomFoodRequest;
import com.macroflow.dto.FoodDto;
import com.macroflow.dto.FoodSearchResponseDto;
import com.macroflow.model.Food;
import com.macroflow.repository.FoodRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Core service for food search and custom food CRUD.
 *
 * <h2>Search strategy</h2>
 * <p>Fan-out: Open Food Facts and USDA FoodData Central are queried in parallel via
 * {@link CompletableFuture}. Each call has a 5-second timeout. If either call fails
 * or times out, the service logs a WARN and marks the response {@code partial = true}
 * so the mobile client can surface a subtle "results may be incomplete" indicator.
 *
 * <h2>Deduplication</h2>
 * <p>If the same food name (case-insensitive) appears in both sources, the USDA result
 * takes precedence (more authoritative for whole foods). OFF results are kept for
 * branded/packaged items that don't appear in USDA.
 *
 * <h2>Custom food delete semantics</h2>
 * <p>Custom foods are never hard-deleted because {@code meal_entry} holds a FK to
 * {@code food(id)}. Instead, delete "disowns" the food: sets {@code ownerId = null}
 * and {@code isCustom = false}. The row stays in the DB but is invisible to users.
 */
@Service
public class FoodSearchService {

    private static final Logger log = LoggerFactory.getLogger(FoodSearchService.class);

    // USDA nutrient IDs used for macro extraction
    private static final int NUTRIENT_ENERGY  = 1008;
    private static final int NUTRIENT_PROTEIN = 1003;
    private static final int NUTRIENT_CARBS   = 1005;
    private static final int NUTRIENT_FAT     = 1004;

    /**
     * Pre-compiled pattern for extracting gram values from serving size strings.
     * Matches formats like "30g" or "1 oz (28g)" — captures the numeric part before 'g'.
     * Static to avoid recompiling on every call to {@link #parseServingSize}.
     */
    private static final Pattern SERVING_GRAMS_PATTERN = Pattern.compile("([\\d.]+)\\s*g");
    private final RestTemplate restTemplate;
    private final FoodRepository foodRepository;
    private final ObjectMapper objectMapper;
    private final String usdaApiKey;

    /**
     * Constructor injection keeps all dependencies explicit and makes the service
     * straightforward to unit-test by passing mock collaborators.
     *
     * @param restTemplate   HTTP client; injected so tests can substitute a mock
     * @param foodRepository JPA repository for custom food persistence
     * @param objectMapper   Jackson mapper for parsing external API JSON responses
     * @param usdaApiKey     USDA FoodData Central API key — never hardcoded
     */
    public FoodSearchService(
            RestTemplate restTemplate,
            FoodRepository foodRepository,
            ObjectMapper objectMapper,
            @Value("${app.usda.api-key}") String usdaApiKey) {
        this.restTemplate = restTemplate;
        this.foodRepository = foodRepository;
        this.objectMapper = objectMapper;
        this.usdaApiKey = usdaApiKey;
    }

    // -------------------------------------------------------------------------
    // Fan-out search
    // -------------------------------------------------------------------------

    /**
     * Search Open Food Facts and USDA FoodData Central in parallel, normalise both
     * result sets to {@link FoodDto}, deduplicate, and return a unified response.
     *
     * @param query  the user's search term
     * @param userId the authenticated user's UUID (reserved for future personalisation)
     * @return unified food list; {@code partial = true} if either API failed/timed out
     */
    public FoodSearchResponseDto searchFoods(String query, UUID userId) {
        // Fire both API calls concurrently; each has a 5-second timeout
        CompletableFuture<List<FoodDto>> offFuture = CompletableFuture
                .supplyAsync(() -> fetchFromOpenFoodFacts(query))
                .orTimeout(5, TimeUnit.SECONDS);

        CompletableFuture<List<FoodDto>> usdaFuture = CompletableFuture
                .supplyAsync(() -> fetchFromUsda(query))
                .orTimeout(5, TimeUnit.SECONDS);

        List<FoodDto> offResults = new ArrayList<>();
        List<FoodDto> usdaResults = new ArrayList<>();
        boolean partial = false;

        try {
            offResults = offFuture.join();
        } catch (Exception e) {
            // Timeout or network error — continue with partial results from USDA
            log.warn("Open Food Facts search failed for query '{}': {}", query, e.getMessage());
            partial = true;
        }

        try {
            usdaResults = usdaFuture.join();
        } catch (Exception e) {
            log.warn("USDA search failed for query '{}': {}", query, e.getMessage());
            partial = true;
        }

        List<FoodDto> merged = deduplicate(offResults, usdaResults);
        return new FoodSearchResponseDto(merged, partial);
    }

    /**
     * Deduplicate results from both sources.
     *
     * <p>Strategy: build a set of lower-cased names from USDA results. For each OFF
     * result, include it only if its name does NOT appear in the USDA set (i.e. USDA
     * wins for whole foods; OFF is kept for branded/packaged items not in USDA).
     */
    List<FoodDto> deduplicate(List<FoodDto> offResults, List<FoodDto> usdaResults) {
        // Collect all USDA names (lower-cased) for O(1) lookup
        Set<String> usdaNames = usdaResults.stream()
                .map(f -> f.name().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());

        // Keep OFF results only when USDA doesn't already have the same food name
        List<FoodDto> deduplicatedOff = offResults.stream()
                .filter(f -> !usdaNames.contains(f.name().toLowerCase(Locale.ROOT)))
                .toList();

        List<FoodDto> combined = new ArrayList<>(usdaResults);
        combined.addAll(deduplicatedOff);
        return combined;
    }

    // -------------------------------------------------------------------------
    // Open Food Facts normalisation
    // -------------------------------------------------------------------------

    /**
     * Call Open Food Facts and normalise the response to a list of {@link FoodDto}.
     * Products with a blank name or zero calories are filtered out.
     */
    List<FoodDto> fetchFromOpenFoodFacts(String query) {
        // OFF_SEARCH_URL template: {query} expanded by RestTemplate URI template support
        String json = restTemplate.getForObject(ExternalApiConstants.OFF_SEARCH_URL, String.class, query);
        if (json == null) return List.of();

        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode products = root.path("products");
            if (!products.isArray()) return List.of();

            List<FoodDto> results = new ArrayList<>();
            for (JsonNode product : products) {
                String name = product.path("product_name").asText("").trim();
                if (name.isBlank()) continue;

                JsonNode nutriments = product.path("nutriments");
                double calories = nutriments.path("energy-kcal_100g").asDouble(0);
                if (calories <= 0) continue;  // skip items with no calorie data

                double protein = nutriments.path("proteins_100g").asDouble(0);
                double carbs   = nutriments.path("carbohydrates_100g").asDouble(0);
                double fat     = nutriments.path("fat_100g").asDouble(0);

                // Use serving_size if present; fall back to 100g (values are per 100g)
                double servingG = parseServingSize(product.path("serving_size").asText(""));

                String externalId = product.path("_id").asText(null);

                results.add(new FoodDto(null, name, calories, protein, carbs, fat, servingG, "OFF", externalId));
            }
            return results;
        } catch (Exception e) {
            log.warn("Failed to parse Open Food Facts response: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Parse a serving size string like "30g" or "1 oz (28g)" into grams.
     * Returns 100.0 if the string is absent or unparseable.
     */
    private double parseServingSize(String servingSizeText) {
        if (servingSizeText == null || servingSizeText.isBlank()) return 100.0;
        Matcher m = SERVING_GRAMS_PATTERN.matcher(servingSizeText);
        if (m.find()) {
            try {
                return Double.parseDouble(m.group(1));
            } catch (NumberFormatException ignored) {}
        }
        return 100.0;
    }

    // -------------------------------------------------------------------------
    // USDA FoodData Central normalisation
    // -------------------------------------------------------------------------

    /**
     * Call USDA FoodData Central and normalise the response to a list of {@link FoodDto}.
     * Items with a null description or zero calories are filtered out.
     */
    List<FoodDto> fetchFromUsda(String query) {
        // USDA_SEARCH_URL template: {query} and {apiKey} expanded by RestTemplate URI template support
        String json = restTemplate.getForObject(ExternalApiConstants.USDA_SEARCH_URL, String.class, query, usdaApiKey);
        if (json == null) return List.of();

        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode foods = root.path("foods");
            if (!foods.isArray()) return List.of();

            List<FoodDto> results = new ArrayList<>();
            for (JsonNode food : foods) {
                String name = food.path("description").asText("").trim();
                if (name.isBlank()) continue;

                // Extract macros from the foodNutrients array by nutrientId
                double calories = 0, protein = 0, carbs = 0, fat = 0;
                for (JsonNode nutrient : food.path("foodNutrients")) {
                    int id    = nutrient.path("nutrientId").asInt(0);
                    double val = nutrient.path("value").asDouble(0);
                    if      (id == NUTRIENT_ENERGY)  calories = val;
                    else if (id == NUTRIENT_PROTEIN) protein  = val;
                    else if (id == NUTRIENT_CARBS)   carbs    = val;
                    else if (id == NUTRIENT_FAT)     fat      = val;
                }
                if (calories <= 0) continue;  // skip items with no calorie data

                // Use servingSize + servingSizeUnit if present; default to 100g
                double servingG = 100.0;
                JsonNode servingSizeNode = food.path("servingSize");
                if (!servingSizeNode.isMissingNode() && !servingSizeNode.isNull()) {
                    String unit = food.path("servingSizeUnit").asText("g");
                    if ("g".equalsIgnoreCase(unit)) {
                        servingG = servingSizeNode.asDouble(100.0);
                    }
                    // Non-gram units (oz, ml, etc.) fall back to 100g for simplicity
                }

                String externalId = String.valueOf(food.path("fdcId").asInt());

                results.add(new FoodDto(null, name, calories, protein, carbs, fat, servingG, "USDA", externalId));
            }
            return results;
        } catch (Exception e) {
            log.warn("Failed to parse USDA response: {}", e.getMessage());
            return List.of();
        }
    }

    // -------------------------------------------------------------------------
    // Custom food CRUD
    // -------------------------------------------------------------------------

    /**
     * Create a new custom food owned by the given user.
     *
     * @param userId the authenticated user's UUID
     * @param req    validated request body
     * @return the saved food as a DTO
     */
    @Transactional
    public FoodDto createCustomFood(UUID userId, CustomFoodRequest req) {
        Food food = new Food();
        food.setOwnerId(userId);
        food.setIsCustom(true);
        food.setSource("CUSTOM");
        applyRequest(food, req);
        return toDto(foodRepository.save(food));
    }

    /**
     * Update an existing custom food. Ownership is enforced: if the food does not
     * exist or belongs to a different user, a 404 is thrown (intentionally vague
     * to avoid leaking whether the food ID exists at all).
     *
     * @param userId the authenticated user's UUID
     * @param foodId the food's UUID
     * @param req    validated request body
     * @return the updated food as a DTO
     */
    @Transactional
    public FoodDto updateCustomFood(UUID userId, UUID foodId, CustomFoodRequest req) {
        Food food = foodRepository.findByOwnerIdAndId(userId, foodId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Food not found"));
        applyRequest(food, req);
        return toDto(foodRepository.save(food));
    }

    /**
     * "Delete" a custom food by disowning it rather than hard-deleting the row.
     *
     * <p>Hard deletion is not possible because {@code meal_entry} holds a FK to
     * {@code food(id)} — removing the row would break historical macro snapshots.
     * Disowning sets {@code ownerId = null} and {@code isCustom = false}, making
     * the food invisible to users while preserving FK integrity.
     *
     * @param userId the authenticated user's UUID
     * @param foodId the food's UUID
     */
    @Transactional
    public void deleteCustomFood(UUID userId, UUID foodId) {
        Food food = foodRepository.findByOwnerIdAndId(userId, foodId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Food not found"));

        // Disown rather than delete — preserves FK integrity with meal_entry
        food.setOwnerId(null);
        food.setIsCustom(false);
        foodRepository.save(food);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Apply all mutable fields from a {@link CustomFoodRequest} onto a {@link Food} entity. */
    private void applyRequest(Food food, CustomFoodRequest req) {
        food.setName(req.name());
        food.setCalories(BigDecimal.valueOf(req.calories()));
        food.setProteinG(BigDecimal.valueOf(req.proteinG()));
        food.setCarbsG(BigDecimal.valueOf(req.carbsG()));
        food.setFatG(BigDecimal.valueOf(req.fatG()));
        food.setServingG(BigDecimal.valueOf(req.servingG()));
    }

    /** Map a {@link Food} entity to a {@link FoodDto}. Entities are never returned directly. */
    FoodDto toDto(Food food) {
        return new FoodDto(
                food.getId(),
                food.getName(),
                food.getCalories().doubleValue(),
                food.getProteinG().doubleValue(),
                food.getCarbsG().doubleValue(),
                food.getFatG().doubleValue(),
                food.getServingG().doubleValue(),
                food.getSource(),
                food.getExternalId()
        );
    }
}
