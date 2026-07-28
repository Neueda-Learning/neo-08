package com.neobank.module.dto;

import com.neobank.module.model.CardRecord;
import java.time.Instant;

/**
 * Safe operator view of a card case. The full PAN cannot leak through this DTO
 * because the entity itself stores only its last four digits and digest.
 */
public record CardRecordView(
        String applicationId,
        String status,
        String outcome,
        String reasonCode,
        String reference,
        String panMasked,
        String bureauCardId,
        String bureauStatus,
        String dispatchRef,
        String productCode,
        Integer issuingConfigVersion,
        String comment,
        Instant createdAt,
        Instant decidedAt) {

    public static CardRecordView of(CardRecord row) {
        return new CardRecordView(
                row.getApplicationId(),
                row.status(),
                row.getOutcome().name(),
                row.getReasonCode(),
                row.getReference(),
                mask(row.getPanLast4()),
                row.getBureauCardId(),
                row.getBureauStatus() == null ? null : row.getBureauStatus().name(),
                row.getDispatchRef(),
                row.getProductCode(),
                row.getIssuingConfigVersion(),
                row.getComment(),
                row.getCreatedAt(),
                row.getDecidedAt());
    }

    private static String mask(String lastFour) {
        return lastFour == null ? null : "**** **** **** " + lastFour;
    }
}
