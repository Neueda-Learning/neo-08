package com.neobank.module.controller;

import com.neobank.module.dto.CardDetailView;
import com.neobank.module.service.CardDetailService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** UC-02 read-only card-record review endpoint. */
@RestController
@RequestMapping("/cases")
public class CardDetailController {

    private final CardDetailService details;

    public CardDetailController(CardDetailService details) {
        this.details = details;
    }

    @GetMapping("/{applicationId}")
    public ResponseEntity<CardDetailView> get(@PathVariable String applicationId) {
        return ResponseEntity.ok(details.get(applicationId));
    }
}
