package com.neobank.module.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.neobank.module.dto.CardCaseSummary;
import com.neobank.module.dto.CardSearchResult;
import com.neobank.module.service.CardSearchService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** UC-01 Card Board API. */
@RestController
@RequestMapping("/cases")
public class CardCaseController {

    public static final String HAS_MORE_HEADER = "X-Has-More";

    private final CardSearchService searches;

    public CardCaseController(CardSearchService searches) {
        this.searches = searches;
    }

    @GetMapping
    public ResponseEntity<List<CardCaseSummary>> search(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "10") int limit) {
        CardSearchResult result = searches.search(q, limit);
        return ResponseEntity.ok()
                .header(HAS_MORE_HEADER, Boolean.toString(result.hasMore()))
                .body(result.cases());
    }

    @GetMapping("/{applicationId}/applicant")
    public ResponseEntity<JsonNode> applicant(@PathVariable String applicationId) {
        return ResponseEntity.ok(searches.getApplicant(applicationId));
    }
}
