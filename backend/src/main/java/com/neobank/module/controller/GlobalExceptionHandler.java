package com.neobank.module.controller;

import com.neobank.module.exception.BureauUnavailableException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(
            MethodArgumentNotValidException.class
    )
    public ResponseEntity<Map<String, Object>>
    handleValidation(
            MethodArgumentNotValidException exception) {

        String detail =
                exception.getBindingResult()
                        .getFieldErrors()
                        .stream()
                        .map(error ->
                                error.getField()
                                        + " "
                                        + error.getDefaultMessage())
                        .reduce((first, second) ->
                                first + "; " + second)
                        .orElse("validation failed");

        return error(
                HttpStatus.BAD_REQUEST,
                detail
        );
    }

    @ExceptionHandler(
            HttpMessageNotReadableException.class
    )
    public ResponseEntity<Map<String, Object>>
    handleUnreadable(
            HttpMessageNotReadableException exception) {

        String message =
                exception.getMostSpecificCause()
                        .getMessage();

        int newline =
                message == null
                        ? -1
                        : message.indexOf('\n');

        String detail =
                newline > 0
                        ? message.substring(0, newline)
                        : message;

        return error(
                HttpStatus.BAD_REQUEST,
                "malformed request body: "
                        + detail
        );
    }

    @ExceptionHandler(
            IllegalArgumentException.class
    )
    public ResponseEntity<Map<String, Object>>
    handleIllegalArgument(
            IllegalArgumentException exception) {

        return error(
                HttpStatus.BAD_REQUEST,
                exception.getMessage()
        );
    }

    @ExceptionHandler(
            BureauUnavailableException.class
    )
    public ResponseEntity<Map<String, Object>>
    handleBureauUnavailable(
            BureauUnavailableException exception) {

        return error(
                HttpStatus.SERVICE_UNAVAILABLE,
                exception.getMessage()
        );
    }

    private ResponseEntity<Map<String, Object>>
    error(
            HttpStatus status,
            String message) {

        Map<String, Object> body =
                new LinkedHashMap<>();

        body.put(
                "timestamp",
                Instant.now().toString()
        );

        body.put(
                "status",
                status.value()
        );

        body.put(
                "error",
                status.getReasonPhrase()
        );

        body.put(
                "message",
                message
        );

        return ResponseEntity
                .status(status)
                .body(body);
    }
}