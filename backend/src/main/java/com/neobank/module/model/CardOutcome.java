package com.neobank.module.model;

/**
 * The card module's local state machine. It is deliberately separate from
 * {@link Decision}, whose values belong to the orchestrator callback contract.
 */
public enum CardOutcome {
    IN_PROGRESS,
    ISSUED,
    FAILED
}
