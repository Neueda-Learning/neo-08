package com.neobank.module.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The v5 card-issuing envelope. Application and outputs stay in memory and are never persisted.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CardExecuteRequest(
        @NotBlank @Size(max = 64) String applicationId,
        String correlationId,
        @NotBlank String command,
        JsonNode application,
        JsonNode outputs) {
}
