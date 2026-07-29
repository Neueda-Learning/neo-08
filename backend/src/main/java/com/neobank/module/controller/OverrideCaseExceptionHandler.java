package com.neobank.module.controller;

import com.neobank.module.service.OverrideCaseException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** JSON lookup and transition errors scoped to the UC-07 endpoint. */
@RestControllerAdvice(assignableTypes = OverrideCaseController.class)
public class OverrideCaseExceptionHandler {

    @ExceptionHandler(OverrideCaseException.class)
    public ResponseEntity<Map<String, Object>> overrideFailure(
            OverrideCaseException exception) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", exception.status().value());
        body.put("error", exception.status().getReasonPhrase());
        body.put("message", exception.getMessage());
        return ResponseEntity.status(exception.status()).body(body);
    }
}
