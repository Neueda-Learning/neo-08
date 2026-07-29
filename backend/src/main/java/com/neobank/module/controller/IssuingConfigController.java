package com.neobank.module.controller;

import com.neobank.module.dto.CreateIssuingConfigRequest;
import com.neobank.module.dto.CreateIssuingConfigResponse;
import com.neobank.module.model.IssuingConfig;
import com.neobank.module.service.IssuingConfigService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/config")
public class IssuingConfigController {

    private final IssuingConfigService issuingConfigService;

    public IssuingConfigController(
            IssuingConfigService issuingConfigService) {
        this.issuingConfigService = issuingConfigService;
    }

    @PostMapping
    public ResponseEntity<CreateIssuingConfigResponse> create(
            @Valid @RequestBody CreateIssuingConfigRequest request) {

        CreateIssuingConfigResponse response =
                issuingConfigService.createVersion(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @GetMapping("/current")
    public ResponseEntity<IssuingConfig> getCurrent() {

        IssuingConfig config =
                issuingConfigService.getCurrentConfig();

        return ResponseEntity.ok(config);
    }


}
