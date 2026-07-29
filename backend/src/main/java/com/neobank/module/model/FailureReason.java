package com.neobank.module.model;

/** Recoverable UC-04 issue failures. */
public enum FailureReason {
    CRD_DELIVERY_ADDRESS_INVALID("Fix address & retry"),
    CRD_BUREAU_UNAVAILABLE("Retry");

    private final String action;

    FailureReason(String action) {
        this.action = action;
    }

    public String action() {
        return action;
    }
}