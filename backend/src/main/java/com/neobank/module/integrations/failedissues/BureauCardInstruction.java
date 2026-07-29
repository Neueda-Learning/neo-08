package com.neobank.module.integrations.failedissues;

import com.neobank.module.dto.CorrectedAddress;

/** In-memory instruction sent to the card bureau. */
public record BureauCardInstruction(
        String applicationId,
        String reference,
        String pan,
        CorrectedAddress deliveryAddress,
        String accountId,
        String productCode) {
}
