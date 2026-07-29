package com.neobank.module.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * UC-07 manual override request.
 */
public record CardOverrideRequest(

        @NotNull(message = "newOutcome is required")
        NewOutcome newOutcome,

        @NotBlank(message = "reason is required")
        @Size(
                max = 512,
                message = "reason must not exceed 512 characters"
        )
        String reason,

        @NotBlank(message = "operator is required")
        @Size(
                max = 64,
                message = "operator must not exceed 64 characters"
        )
        String operator

) {

    /**
     * Operators may only choose a final outcome.
     */
    public enum NewOutcome {
        ISSUED,
        FAILED
    }
}