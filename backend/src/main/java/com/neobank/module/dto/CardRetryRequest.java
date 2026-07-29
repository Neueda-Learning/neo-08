package com.neobank.module.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** UC-04 retry command; correctedAddress is allowed only for the address failure reason. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CardRetryRequest(CorrectedAddress correctedAddress) {
}
