package com.macroflow.constants;

/**
 * URL templates for external food data APIs.
 *
 * <p>Centralised here so any endpoint change is made in one place and is immediately
 * visible to all callers. Spring's {@code RestTemplate} expands the {@code {placeholder}}
 * tokens at call time via its URI template support.
 *
 * <p>These are URL templates, not secrets — API keys are injected separately via
 * environment variables and never appear in source code.
 */
public final class ExternalApiConstants {

    private ExternalApiConstants() {
        // constants class — no instantiation
    }

    /**
     * Open Food Facts search endpoint.
     * Template variable: {@code {query}} — the user's search term.
     * Returns up to 20 products per request.
     */
    public static final String OFF_SEARCH_URL =
            "https://world.openfoodfacts.org/cgi/search.pl" +
            "?search_terms={query}&search_simple=1&action=process&json=1&page_size=20";

    /**
     * USDA FoodData Central search endpoint.
     * Template variables: {@code {query}} — search term; {@code {apiKey}} — injected at
     * runtime from the {@code USDA_API_KEY} environment variable via {@code app.usda.api-key}.
     */
    public static final String USDA_SEARCH_URL =
            "https://api.nal.usda.gov/fdc/v1/foods/search" +
            "?query={query}&api_key={apiKey}&pageSize=20";
}
