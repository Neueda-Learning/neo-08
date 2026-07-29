package com.neobank.module.dto;

import com.neobank.module.model.BureauStatus;
import com.neobank.module.model.CardOutcome;
import java.time.Instant;

/**
 * The UC-01 search row. It deliberately exposes last-four only and never the PAN hash.
 */
public record CardCaseSummary(
        String applicationId,
        CardOutcome outcome,
        String panLast4,
        BureauStatus bureauStatus,
        Instant issuedAt) {
}
