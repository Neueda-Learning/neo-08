package com.neobank.module.controller;

import com.neobank.module.exception.BureauUnavailableException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Error mapping owned only by the newly added bureau and issuing-config APIs. */
@RestControllerAdvice(assignableTypes = {
        BureauAdminController.class,
        IssuingConfigController.class,
        MockBureauCardController.class
})
public class CardOperationsExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> invalidRequest(IllegalArgumentException exception) {
        return error(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(BureauUnavailableException.class)
    public ResponseEntity<Map<String, Object>> bureauUnavailable(
            BureauUnavailableException exception) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage());
    }

    private static ResponseEntity<Map<String, Object>> error(
            HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }
}
