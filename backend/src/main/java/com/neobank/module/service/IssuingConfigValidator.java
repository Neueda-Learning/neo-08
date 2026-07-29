package com.neobank.module.service;

import com.neobank.module.dto.IssuingConfigSnapshot;
import java.net.URI;
import java.util.Set;

/** Validates a persisted configuration before it is allowed to issue a card. */
final class IssuingConfigValidator {

    private static final Set<String> ADDRESS_FIELDS =
            Set.of("line1", "line2", "city", "postcode", "country");

    private IssuingConfigValidator() {
    }

    static boolean isValid(IssuingConfigSnapshot config) {
        return config != null
                && config.version() > 0
                && config.panPrefix() != null
                && config.panPrefix().matches("9999\\d{2}")
                && config.panLength() == 16
                && validCountries(config)
                && validAddressFields(config)
                && validBureauUrl(config.bureauBaseUrl());
    }

    private static boolean validCountries(IssuingConfigSnapshot config) {
        return config.deliveryCountries() != null
                && !config.deliveryCountries().isEmpty()
                && config.deliveryCountries().size() <= 20
                && config.deliveryCountries().stream()
                        .allMatch(country -> country != null && country.matches("[A-Z]{2}"));
    }

    private static boolean validAddressFields(IssuingConfigSnapshot config) {
        return config.requiredAddressFields() != null
                && !config.requiredAddressFields().isEmpty()
                && config.requiredAddressFields().size() <= 5
                && config.requiredAddressFields().stream().allMatch(ADDRESS_FIELDS::contains);
    }

    private static boolean validBureauUrl(String value) {
        if (value == null || value.isBlank() || value.length() > 256) {
            return false;
        }
        try {
            URI uri = URI.create(value);
            return ("http".equalsIgnoreCase(uri.getScheme())
                            || "https".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null
                    && !uri.getHost().isBlank();
        } catch (IllegalArgumentException invalidUri) {
            return false;
        }
    }
}
