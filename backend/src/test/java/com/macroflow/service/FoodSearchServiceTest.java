package com.macroflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.macroflow.dto.CustomFoodRequest;
import com.macroflow.dto.FoodDto;
import com.macroflow.dto.FoodSearchResponseDto;
import com.macroflow.model.Food;
import com.macroflow.repository.FoodRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link FoodSearchService}.
 *
 * <p>All external collaborators (RestTemplate, FoodRepository) are mocked so these
 * tests run without a database or network.
 *
 * <p>For the partial-result tests we use a Mockito spy on the service and stub the
 * package-private {@code fetchFromOpenFoodFacts} / {@code fetchFromUsda} helpers
 * directly. This sidesteps the {@code RestTemplate.getForObject} varargs/Map overload
 * ambiguity that prevents {@code when(...).thenReturn(...)} stubs from compiling, and
 * also avoids the need to construct valid JSON payloads for every scenario.
 */
@ExtendWith(MockitoExtension.class)
class FoodSearchServiceTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private FoodRepository foodRepository;

    private FoodSearchService service;

    @BeforeEach
    void setUp() {
        // Use a real ObjectMapper — no reason to mock a pure data-transformation utility
        service = new FoodSearchService(restTemplate, foodRepository, new ObjectMapper(), "test-key");
    }

    // -------------------------------------------------------------------------
    // Deduplication
    // -------------------------------------------------------------------------

    /**
     * When USDA and OFF both return a food with the same name (case-insensitive),
     * only the USDA result should appear in the output (USDA wins for whole foods).
     */
    @Test
    void deduplicate_sameNameInBothSources_keepUsdaDiscardOff() {
        FoodDto usdaChicken = foodDto("Chicken Breast", "USDA");
        FoodDto offChicken  = foodDto("chicken breast", "OFF");  // same name, different case
        FoodDto offBrand    = foodDto("BrandedBar XL", "OFF");   // unique to OFF — should be kept

        List<FoodDto> result = service.deduplicate(List.of(offChicken, offBrand), List.of(usdaChicken));

        // USDA chicken is present; OFF chicken is gone; branded bar is kept
        assertThat(result).hasSize(2);
        assertThat(result).extracting(FoodDto::source).containsExactlyInAnyOrder("USDA", "OFF");
        assertThat(result).extracting(FoodDto::name)
                .containsExactlyInAnyOrder("Chicken Breast", "BrandedBar XL");
    }

    @Test
    void deduplicate_noOverlap_returnsBothSources() {
        FoodDto usda = foodDto("Brown Rice", "USDA");
        FoodDto off  = foodDto("Oreo Cookies", "OFF");

        List<FoodDto> result = service.deduplicate(List.of(off), List.of(usda));

        assertThat(result).hasSize(2);
    }

    @Test
    void deduplicate_emptyOff_returnsUsdaOnly() {
        FoodDto usda = foodDto("Salmon", "USDA");

        List<FoodDto> result = service.deduplicate(List.of(), List.of(usda));

        assertThat(result).containsExactly(usda);
    }

    // -------------------------------------------------------------------------
    // Partial results — USDA failure
    // -------------------------------------------------------------------------

    /**
     * When the USDA call throws an exception, the response must have {@code partial = true}
     * and still include the OFF results.
     *
     * <p>We spy on the service and stub the package-private fetch helpers directly to
     * avoid the {@code RestTemplate.getForObject} varargs/Map overload ambiguity.
     */
    @Test
    void searchFoods_usdaFails_returnsPartialWithOffResults() {
        FoodSearchService spy = spy(service);
        FoodDto granola = foodDto("Granola Bar", "OFF");

        doReturn(List.of(granola)).when(spy).fetchFromOpenFoodFacts("granola");
        doThrow(new RuntimeException("USDA unavailable")).when(spy).fetchFromUsda("granola");

        FoodSearchResponseDto response = spy.searchFoods("granola", UUID.randomUUID());

        assertThat(response.partial()).isTrue();
        assertThat(response.foods()).hasSize(1);
        assertThat(response.foods().get(0).name()).isEqualTo("Granola Bar");
        assertThat(response.foods().get(0).source()).isEqualTo("OFF");
    }

    // -------------------------------------------------------------------------
    // Partial results — OFF failure
    // -------------------------------------------------------------------------

    /**
     * When the OFF call throws an exception, the response must have {@code partial = true}
     * and still include the USDA results.
     */
    @Test
    void searchFoods_offFails_returnsPartialWithUsdaResults() {
        FoodSearchService spy = spy(service);
        FoodDto chicken = foodDto("Chicken Breast", "USDA");

        doThrow(new RuntimeException("OFF unavailable")).when(spy).fetchFromOpenFoodFacts("chicken");
        doReturn(List.of(chicken)).when(spy).fetchFromUsda("chicken");

        FoodSearchResponseDto response = spy.searchFoods("chicken", UUID.randomUUID());

        assertThat(response.partial()).isTrue();
        assertThat(response.foods()).hasSize(1);
        assertThat(response.foods().get(0).name()).isEqualTo("Chicken Breast");
        assertThat(response.foods().get(0).source()).isEqualTo("USDA");
    }

    // -------------------------------------------------------------------------
    // Ownership check — update
    // -------------------------------------------------------------------------

    /**
     * {@code updateCustomFood} must throw 404 when the food belongs to a different user,
     * so that user A cannot modify user B's custom foods.
     */
    @Test
    void updateCustomFood_foodBelongsToDifferentUser_throws404() {
        UUID userA  = UUID.randomUUID();
        UUID foodId = UUID.randomUUID();

        // Repository returns empty when queried with userA's ID — food belongs to another user
        when(foodRepository.findByOwnerIdAndId(userA, foodId)).thenReturn(Optional.empty());

        CustomFoodRequest req = new CustomFoodRequest("Oats", 350.0, 12.0, 60.0, 7.0, 100.0);

        assertThatThrownBy(() -> service.updateCustomFood(userA, foodId, req))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
    }

    // -------------------------------------------------------------------------
    // Ownership check — delete
    // -------------------------------------------------------------------------

    /**
     * {@code deleteCustomFood} must throw 404 when the food belongs to a different user.
     */
    @Test
    void deleteCustomFood_foodBelongsToDifferentUser_throws404() {
        UUID userA  = UUID.randomUUID();
        UUID foodId = UUID.randomUUID();

        when(foodRepository.findByOwnerIdAndId(userA, foodId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteCustomFood(userA, foodId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
    }

    // -------------------------------------------------------------------------
    // Disown on delete
    // -------------------------------------------------------------------------

    /**
     * After {@code deleteCustomFood}, the food row must have {@code ownerId = null}
     * and {@code isCustom = false} — it is disowned rather than hard-deleted so that
     * existing meal_entry rows referencing this food remain intact.
     */
    @Test
    void deleteCustomFood_ownedFood_disownsRatherThanDeletes() {
        UUID userId = UUID.randomUUID();
        UUID foodId = UUID.randomUUID();

        Food food = buildFood(userId);
        when(foodRepository.findByOwnerIdAndId(userId, foodId)).thenReturn(Optional.of(food));
        when(foodRepository.save(any(Food.class))).thenAnswer(inv -> inv.getArgument(0));

        service.deleteCustomFood(userId, foodId);

        // Capture what was saved and verify the disown fields
        ArgumentCaptor<Food> captor = ArgumentCaptor.forClass(Food.class);
        verify(foodRepository).save(captor.capture());
        Food saved = captor.getValue();

        assertThat(saved.getOwnerId()).isNull();
        assertThat(saved.getIsCustom()).isFalse();
        // Hard delete must NOT have been called
        verify(foodRepository, never()).delete(any());
        verify(foodRepository, never()).deleteById(any());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private FoodDto foodDto(String name, String source) {
        return new FoodDto(UUID.randomUUID(), name, 200, 10, 30, 5, 100, source, "ext-" + name);
    }

    private Food buildFood(UUID ownerId) {
        Food food = new Food();
        food.setOwnerId(ownerId);
        food.setIsCustom(true);
        food.setSource("CUSTOM");
        food.setName("My Food");
        food.setCalories(BigDecimal.valueOf(200));
        food.setProteinG(BigDecimal.valueOf(10));
        food.setCarbsG(BigDecimal.valueOf(30));
        food.setFatG(BigDecimal.valueOf(5));
        food.setServingG(BigDecimal.valueOf(100));
        return food;
    }
}
