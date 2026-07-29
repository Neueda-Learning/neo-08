package com.neobank.module.service;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** Controlled UC-04 error with optional field-level validation details. */
public class FailedIssueException extends RuntimeException {

    private final HttpStatus status;
    private final Map<String, String> fieldErrors;

    public FailedIssueException(HttpStatus status, String message) {
        this(status, message, Map.of());
    }

    public FailedIssueException(
            HttpStatus status, String message, Map<String, String> fieldErrors) {
        super(message);
        this.status = status;
        this.fieldErrors = Map.copyOf(new LinkedHashMap<>(fieldErrors));
    }

    public HttpStatus status() {
        return status;
    }

    public Map<String, String> fieldErrors() {
        return fieldErrors;
    }
}
