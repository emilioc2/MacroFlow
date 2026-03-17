package com.macroflow.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * Configures the {@link RestTemplate} bean used by {@link com.macroflow.service.FoodSearchService}
 * to call external food APIs (Open Food Facts and USDA FoodData Central).
 *
 * <p>A 5-second connect/read timeout is set here as a socket-level safety net.
 * The service layer also applies a per-call {@code CompletableFuture.orTimeout(5, SECONDS)}
 * so that slow responses don't block the fan-out indefinitely even if the socket-level
 * timeout fires later.
 */
@Configuration
public class RestClientConfig {

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(5))
                .build();
    }
}
