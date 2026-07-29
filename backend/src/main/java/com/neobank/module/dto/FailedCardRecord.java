package com.neobank.module.dto;

import com.neobank.module.model.CardOutcome;
import com.neobank.module.model.FailureReason;

/** Fields required to decide and persist one retry; no applicant payload is present. */
public record FailedCardRecord(
        String applicationId,
        CardOutcome outcome,
        String reference,
        String previousPanLast4,
        String previousPanHash,
        String accountId,
        String productCode,
        FailureReason failureReason) {
}
