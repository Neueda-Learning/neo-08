package com.neobank.module.controller;

import com.neobank.module.service.CardDetailNotFoundException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** JSON error mapping scoped to the new UC-02 endpoint. */
@RestControllerAdvice(assignableTypes = CardDetailController.class)
public class CardDetailExceptionHandler {

    @ExceptionHandler(CardDetailNotFoundException.class)
    public ResponseEntity<Map<String, Object>> notFound(CardDetailNotFoundException exception) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", HttpStatus.NOT_FOUND.value());
        body.put("error", HttpStatus.NOT_FOUND.getReasonPhrase());
        body.put("message", exception.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }
}
