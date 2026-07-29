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

/** UC-07 API for the one permitted operator mutation of a card record. */
@RestController
public class OverrideCaseController {

    private final OverrideCaseService overrides;

    public OverrideCaseController(OverrideCaseService overrides) {
        this.overrides = overrides;
    }

    @PostMapping("/cases/{applicationId}/override")
    public ResponseEntity<CardDetailView> override(
            @PathVariable String applicationId,
            @Valid @RequestBody CardOverrideRequest request) {
        return ResponseEntity.ok(overrides.override(applicationId, request));
    }
}
