package com.macroflow.api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Smoke test that verifies the Spring application context loads successfully.
 *
 * <p>Uses the {@code test} profile so H2 is used instead of PostgreSQL,
 * and Flyway is disabled — no external database required to run CI.
 */
@SpringBootTest
@ActiveProfiles("test")
class MacroFlowApiApplicationTests {

    @Test
    void contextLoads() {
        // If the context fails to start, this test fails — that's the intent.
    }
}
