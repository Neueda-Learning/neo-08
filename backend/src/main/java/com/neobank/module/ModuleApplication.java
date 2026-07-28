package com.neobank.module;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * One module of the neo-bank onboarding journey.
 *
 * <p>It durably accepts an application from the orchestrator with {@code 202}, performs the
 * card-issuing workflow off the request thread, and calls the orchestrator back with
 * {@code ACCEPTED} / {@code REFERRED}.</p>
 *
 * <p>Which module this is — its id, display name and BIAN domain — is configuration, not
 * code: see {@code application.yml} and {@code .env.example}.</p>
 */
@SpringBootApplication
@EnableScheduling
public class ModuleApplication {

    public static void main(String[] args) {
        SpringApplication.run(ModuleApplication.class, args);
    }
}
