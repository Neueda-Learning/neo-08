package com.neobank.module.controller;

import com.neobank.module.dto.CardTimelineItem;
import com.neobank.module.service.CardTimelineService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/cases")
public class CardTimelineController {

    private final CardTimelineService service;

    public CardTimelineController(
            CardTimelineService service) {

        this.service = service;
    }

    @GetMapping("/{applicationId}/timeline")
    public ResponseEntity<List<CardTimelineItem>>
    getTimeline(
            @PathVariable String applicationId) {

        List<CardTimelineItem> timeline =
                service.getTimeline(
                        applicationId
                );

        return ResponseEntity.ok(timeline);
    }
}