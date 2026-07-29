package com.neobank.module.service;

/** Raised when UC-02 is asked to review a card case this module does not own. */
public class CardDetailNotFoundException extends RuntimeException {

    public CardDetailNotFoundException(String applicationId) {
        super("card case not found: " + applicationId);
    }
}
