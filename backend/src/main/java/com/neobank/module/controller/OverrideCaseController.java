package com.neobank.module.controller;

import com.neobank.module.dto.CardDetailView;
import com.neobank.module.dto.CardOverrideRequest;
import com.neobank.module.service.OverrideCaseService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * UC-07 manual card-case override API.
 */
@RestController
public class OverrideCaseController {

    private final OverrideCaseService overrideCaseService;

    public OverrideCaseController(
            OverrideCaseService overrideCaseService) {

        this.overrideCaseService = overrideCaseService;
    }

    @PostMapping("/cases/{applicationId}/override")
    public ResponseEntity<CardDetailView> override(
            @PathVariable String applicationId,
            @Valid @RequestBody CardOverrideRequest request) {

        CardDetailView result =
                overrideCaseService.override(
                        applicationId,
                        request
                );

        return ResponseEntity.ok(result);
    }
}