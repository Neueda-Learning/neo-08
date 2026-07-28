package com.neobank.module.model;

/** Card case outcomes. UC-00 creates only {@link #IN_PROGRESS}. */
public enum CardOutcome {
    IN_PROGRESS,
    ISSUED,
    FAILED
}
