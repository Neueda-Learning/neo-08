package com.neobank.module.config;

import com.neobank.module.service.PanGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Small infrastructure beans shared by the workflow: the outbound HTTP client
 * and the secure test-PAN generator.
 *
 * <p>The thread pool the decision runs on is Spring Boot's own
 * {@code applicationTaskExecutor} — no bean needed here. Size and naming are properties:
 * {@code spring.task.execution.*} in {@code application.yml}.</p>
 */
@Configuration
public class AppConfig {

    @Bean
    public RestClient restClient(
            RestClient.Builder builder,
            CallbackDeliveryTrackingInterceptor deliveryTracker) {
        return builder
                .requestInterceptor(deliveryTracker)
                .build();
    }

    @Bean
    public PanGenerator panGenerator() {
        return new PanGenerator();
    }
}
