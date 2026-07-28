package com.neobank.module.controller;

import com.neobank.module.dto.BureauDialsResponse;
import com.neobank.module.dto.UpdateBureauDialsRequest;
import com.neobank.module.model.BureauDials;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/bureau/admin/dials")
public class BureauAdminController {

    private final BureauDials bureauDials;

    public BureauAdminController(BureauDials bureauDials) {
        this.bureauDials = bureauDials;
    }

    @GetMapping
    public ResponseEntity<BureauDialsResponse> getDials() {

        return ResponseEntity.ok(toResponse());
    }

    @PutMapping
    public ResponseEntity<BureauDialsResponse> updateDials(
            @RequestBody UpdateBureauDialsRequest request) {

        bureauDials.update(
                request.secondsPerStage(),
                request.latencyMs(),
                request.killSwitch()
        );

        return ResponseEntity.ok(toResponse());
    }

    private BureauDialsResponse toResponse() {
        return new BureauDialsResponse(
                bureauDials.getSecondsPerStage(),
                bureauDials.getLatencyMs(),
                bureauDials.isKillSwitch()
        );
    }
}
