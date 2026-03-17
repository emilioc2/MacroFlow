package com.macroflow.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies Flyway migration integrity by running all migrations against a real
 * PostgreSQL instance (via Testcontainers) and asserting that every expected
 * table exists in the public schema.
 *
 * <p>This test intentionally does NOT use the {@code test} profile — that profile
 * uses H2 with Flyway disabled. Instead, {@link #overrideDataSource} wires the
 * Spring datasource directly to the Testcontainer, so Flyway runs exactly as it
 * would in production.
 *
 * <p>The test is skipped automatically when Docker is not available (e.g. in CI
 * environments without a Docker daemon), so it never blocks the build.
 */
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")  // provides JWT/USDA stubs so context starts without real env vars
@SpringBootTest
class FlywayMigrationTest {

    /** All tables that V1__init.sql must create. */
    private static final List<String> EXPECTED_TABLES = List.of(
            "app_user",
            "user_profile",
            "daily_targets",
            "food",
            "meal_entry",
            "saved_meal",
            "saved_meal_item",
            "recently_logged",
            "reminder_config",
            "user_preferences"
    );

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    /**
     * Override the datasource at test startup so Spring connects to the Testcontainer
     * rather than the environment-variable-driven production URL. Flyway is enabled by
     * default in {@code application.yml}, so migrations run automatically when the
     * context starts — no extra configuration needed here.
     */
    @DynamicPropertySource
    static void overrideDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        // Flyway owns the schema here; Hibernate must not attempt its own DDL
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void allTablesExistAfterMigration() {
        // Query information_schema so the assertion is independent of Flyway internals
        List<String> actualTables = jdbcTemplate.queryForList(
                """
                SELECT table_name
                FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_type = 'BASE TABLE'
                """,
                String.class
        );

        assertThat(actualTables)
                .as("All tables defined in V1__init.sql must exist after migration")
                .containsAll(EXPECTED_TABLES);
    }
}
