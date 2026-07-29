package com.neobank.module.dto;

/** The retry endpoint acknowledges the operator action after the attempt has been recorded. */
public record CardRetryAck(String status) {

    public static CardRetryAck retrying() {
        return new CardRetryAck("retrying");
    }
}
