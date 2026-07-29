package com.neobank.module.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Operator command for the UC-07 case-outcome override. */
public record CardOverrideRequest(
        @NotNull NewOutcome newOutcome,
        @NotBlank @Size(max = 512) String reason,
        @NotBlank @Size(max = 64) String operator) {

    /** UC-07 deliberately excludes IN_PROGRESS from operator-selected outcomes. */
    public enum NewOutcome {
        ISSUED,
        FAILED
    }
}
