package com.neobank.module.service;

import com.neobank.module.dto.CardTimelineItem;
import com.neobank.module.repository.CardTimelineRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CardTimelineService {

    private final CardTimelineRepository repository;

    public CardTimelineService(
            CardTimelineRepository repository) {

        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<CardTimelineItem> getTimeline(
            String applicationId) {

        return repository.findTimeline(
                applicationId
        );
    }
}