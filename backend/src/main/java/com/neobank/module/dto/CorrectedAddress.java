package com.neobank.module.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Operator-supplied delivery address. It is used in memory and never persisted. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CorrectedAddress(
        String line1,
        String line2,
        String city,
        String postcode,
        String country) {
}
