package com.neobank.module.dto;

import java.util.List;

/** Immutable current IssuingConfig used by one retry attempt. */
public record IssuingConfigSnapshot(
        int version,
        String panPrefix,
        int panLength,
        List<String> deliveryCountries,
        List<String> requiredAddressFields,
        String bureauBaseUrl) {

    public IssuingConfigSnapshot {
        deliveryCountries = List.copyOf(deliveryCountries);
        requiredAddressFields = List.copyOf(requiredAddressFields);
    }
}
