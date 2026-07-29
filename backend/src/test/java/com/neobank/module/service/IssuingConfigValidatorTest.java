package com.neobank.module.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.neobank.module.dto.IssuingConfigSnapshot;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class IssuingConfigValidatorTest {

    @Test
    void acceptsACompleteIssuingConfiguration() {
        assertThat(IssuingConfigValidator.isValid(validConfig())).isTrue();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidConfigs")
    void rejectsAnyConfigurationThatDoesNotMeetTheIssuingRules(
            String description, IssuingConfigSnapshot config) {
        assertThat(IssuingConfigValidator.isValid(config))
                .as(description)
                .isFalse();
    }

    private static Stream<Arguments> invalidConfigs() {
        return Stream.of(
                Arguments.of("missing config", null),
                Arguments.of("non-positive version", config(
                        0, "999901", 16, countries(), addressFields(), bureauUrl())),
                Arguments.of("PAN outside reserved range", config(
                        2, "123456", 16, countries(), addressFields(), bureauUrl())),
                Arguments.of("PAN length other than 16", config(
                        2, "999901", 15, countries(), addressFields(), bureauUrl())),
                Arguments.of("no delivery countries", config(
                        2, "999901", 16, List.of(), addressFields(), bureauUrl())),
                Arguments.of("lowercase country", config(
                        2, "999901", 16, List.of("gb"), addressFields(), bureauUrl())),
                Arguments.of("unknown address field", config(
                        2, "999901", 16, countries(), List.of("line1", "county"), bureauUrl())),
                Arguments.of("no required address fields", config(
                        2, "999901", 16, countries(), List.of(), bureauUrl())),
                Arguments.of("non-HTTP bureau URL", config(
                        2, "999901", 16, countries(), addressFields(), "mock-bureau:8091")));
    }

    private static IssuingConfigSnapshot validConfig() {
        return config(2, "999901", 16, countries(), addressFields(), bureauUrl());
    }

    private static IssuingConfigSnapshot config(
            int version,
            String panPrefix,
            int panLength,
            List<String> deliveryCountries,
            List<String> requiredAddressFields,
            String bureauBaseUrl) {
        return new IssuingConfigSnapshot(
                version,
                panPrefix,
                panLength,
                deliveryCountries,
                requiredAddressFields,
                bureauBaseUrl);
    }

    private static List<String> countries() {
        return List.of("GB", "IE", "FR");
    }

    private static List<String> addressFields() {
        return List.of("line1", "city", "postcode", "country");
    }

    private static String bureauUrl() {
        return "http://mock-bureau:8091";
    }
}
