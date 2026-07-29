package com.neobank.module.service;

import org.springframework.http.HttpStatus;

/** Controlled JSON error for UC-07 lookup and state-transition failures. */
public class OverrideCaseException extends RuntimeException {

    private final HttpStatus status;

    public OverrideCaseException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
