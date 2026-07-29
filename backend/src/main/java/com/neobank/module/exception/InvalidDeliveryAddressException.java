package com.neobank.module.exception;

public class InvalidDeliveryAddressException
        extends RuntimeException {

    public InvalidDeliveryAddressException(String message) {
        super(message);
    }
}