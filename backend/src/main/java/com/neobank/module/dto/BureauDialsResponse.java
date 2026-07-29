package com.neobank.module.dto;

public record BureauDialsResponse(
        int secondsPerStage,
        int latencyMs,
        boolean killSwitch
) {
}
