package com.neobank.module.dto;

import com.neobank.module.model.BureauStatus;
import java.time.Instant;

public record CardTimelineItem(
        BureauStatus status,
        Instant observedAt,
        String source,
        String dispatchRef
) {
}