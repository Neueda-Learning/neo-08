package com.neobank.module.dto;

import java.util.List;

/** The read-only UC-02 representation of one locally owned card record. */
public record CardDetailView(
        String outcome,
        String reference,
        String panMasked,
        String panHash,
        String bureauCardId,
        String bureauStatus,
        String dispatchRef,
        String accountId,
        String productCode,
        Integer issuingConfigVersion,
        List<CardReasonView> reasons) {

    public CardDetailView {
        reasons = List.copyOf(reasons);
    }
}
