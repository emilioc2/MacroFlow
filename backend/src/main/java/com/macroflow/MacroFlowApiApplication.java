package com.macroflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the MacroFlow API.
 *
 * <p>Spring Boot auto-configuration handles component scanning, JPA, Security,
 * Flyway, and Actuator setup. All configuration is externalised via
 * {@code application.yml} and environment variables — nothing is hardcoded here.
 */
@SpringBootApplication
public class MacroFlowApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(MacroFlowApiApplication.class, args);
    }
}
