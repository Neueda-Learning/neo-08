package com.neobank.module.exception;

public class BureauUnavailableException
        extends RuntimeException {

    public BureauUnavailableException(String message) {
        super(message);
    }
}
