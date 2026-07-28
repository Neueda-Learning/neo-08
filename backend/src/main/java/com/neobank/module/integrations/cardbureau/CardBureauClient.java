package com.neobank.module.integrations.cardbureau;

import com.neobank.module.model.BureauStatus;

/**
 * Boundary to the card-personalisation bureau.
 *
 * <p>The full test PAN and delivery address cross this boundary only in memory.
 * Neither value is returned to, or persisted by, this module.</p>
 *
 * <p>{@code applicationId} is the bureau idempotency key. Implementations must
 * return the same card for a repeated, identical instruction; process recovery
 * deliberately relies on that guarantee.</p>
 */
public interface CardBureauClient {

    IssuedCard issue(IssueCard command);

    record IssueCard(
            String applicationId,
            String cardholderName,
            String fullPan,
            String productCode,
            DeliveryAddress deliveryAddress) {
    }

    record DeliveryAddress(
            String line1,
            String line2,
            String city,
            String postcode,
            String country) {
    }

    record IssuedCard(String bureauCardId, BureauStatus status) {
    }
}
