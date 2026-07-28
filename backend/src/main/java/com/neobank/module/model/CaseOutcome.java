package com.neobank.module.model;

/**
 * The two terminal outcomes a card-issuing case can reach, plus the initial state.
 * Replaces the credit-focused {@link Decision} enum.
 */
public enum CaseOutcome {
    IN_PROGRESS,
    ISSUED,
    FAILED
}
