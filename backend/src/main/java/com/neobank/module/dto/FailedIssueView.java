package com.neobank.module.dto;

import com.neobank.module.model.FailureReason;
import java.time.Instant;

/** One row on the UC-04 failed-issues queue; applicant name is hydrated live. */
public record FailedIssueView(
        String applicationId,
        FailureReason reason,
        String action,
        Instant lastAttemptAt) {
}
