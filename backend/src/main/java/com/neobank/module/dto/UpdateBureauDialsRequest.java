package com.neobank.module.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record UpdateBureauDialsRequest(

        @Min(
                value = 1,
                message = "secondsPerStage must be at least 1"
        )
        @Max(
                value = 3600,
                message = "secondsPerStage must not exceed 3600"
        )
        Integer secondsPerStage,

        @Min(
                value = 0,
                message = "latencyMs must not be negative"
        )
        @Max(
                value = 30000,
                message = "latencyMs must not exceed 30000"
        )
        Integer latencyMs,

        Boolean killSwitch

) {
}