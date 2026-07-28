package com.neobank.module.controller;

import com.neobank.module.dto.BureauDialsResponse;
import com.neobank.module.dto.UpdateBureauDialsRequest;
import com.neobank.module.model.BureauDials;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/bureau/admin/dials")
public class BureauAdminController {

    private final BureauDials bureauDials;

    public BureauAdminController(BureauDials bureauDials) {
        this.bureauDials = bureauDials;
    }

    /**
     * Returns the current mock bureau control settings.
     */
    @GetMapping
    public ResponseEntity<BureauDialsResponse> getDials() {
        return ResponseEntity.ok(toResponse());
    }

    /**
     * Partially updates the mock bureau control settings.
     *
     * Any null field keeps its current value.
     */
    @PutMapping
    public ResponseEntity<BureauDialsResponse> updateDials(
            @Valid @RequestBody UpdateBureauDialsRequest request) {

        bureauDials.update(
                request.secondsPerStage(),
                request.latencyMs(),
                request.killSwitch()
        );

        return ResponseEntity.ok(toResponse());
    }

    /**
     * Converts the current in-memory dial state into an API response.
     */
    private BureauDialsResponse toResponse() {
        return new BureauDialsResponse(
                bureauDials.getSecondsPerStage(),
                bureauDials.getLatencyMs(),
                bureauDials.isKillSwitch()
        );
    }


}