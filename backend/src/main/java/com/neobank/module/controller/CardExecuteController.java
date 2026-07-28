package com.neobank.module.controller;

import com.neobank.module.dto.CardExecuteAck;
import com.neobank.module.dto.CardExecuteRequest;
import com.neobank.module.service.CardIntakeService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The v5 card-issuing entry point. */
@RestController
@RequestMapping("/api/v1/card")
public class CardExecuteController {

    private final CardIntakeService intake;

    public CardExecuteController(CardIntakeService intake) {
        this.intake = intake;
    }

    @PostMapping("/execute")
    public ResponseEntity<CardExecuteAck> execute(
            @Valid @RequestBody CardExecuteRequest request) {
        intake.accept(request);
        return ResponseEntity.accepted().body(CardExecuteAck.inProgress(request));
    }
}
