package com.neobank.module.model;

/**
 * Bureau-side lifecycle states observed by polling, never commanded directly.
 */
public enum BureauStatus {
    REQUESTED,
    PERSONALISED,
    DISPATCHED
}
