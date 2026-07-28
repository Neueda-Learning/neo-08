package com.neobank.module.dto;

public record UpdateBureauDialsRequest(
        Integer secondsPerStage,
        Integer latencyMs,
        Boolean killSwitch
) {
}