package com.neobank.module.service;

import com.neobank.module.repository.CardTimelineRepository;
import com.neobank.module.repository.CardTimelineRepository.PollCandidate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class BureauStatusPoller {

    private static final Logger log =
            LoggerFactory.getLogger(
                    BureauStatusPoller.class
            );

    private final CardTimelineRepository repository;
    private final MockBureauService mockBureauService;

    public BureauStatusPoller(
            CardTimelineRepository repository,
            MockBureauService mockBureauService) {

        this.repository = repository;
        this.mockBureauService =
                mockBureauService;
    }

    /**
     * Polls every two seconds by default.
     *
     * The value can be changed with:
     * CARD_TIMELINE_POLL_MS=3000
     */
    @Scheduled(
            fixedDelayString =
                    "${card.timeline.poll-ms:2000}"
    )
    public void pollBureauStatuses() {

        List<PollCandidate> candidates =
                repository.findCardsToPoll();

        for (PollCandidate candidate :
                candidates) {

            pollOne(candidate);
        }
    }

    private void pollOne(
            PollCandidate candidate) {

        try {
            MockBureauService.BureauResult result =
                    mockBureauService.getCard(
                            candidate.bureauCardId()
                    );

            repository.recordObservedStatus(
                    candidate.applicationId(),
                    result.status(),
                    result.dispatchRef()
            );

        } catch (RuntimeException exception) {

            /*
             * One unavailable or missing Bureau card
             * must not stop the scheduled task from
             * processing the remaining cards.
             *
             * debug avoids writing the same warning
             * every two seconds after a backend restart,
             * because the simplified Bureau uses memory.
             */
            log.debug(
                    "Could not poll Bureau card {} "
                            + "for application {}: {}",
                    candidate.bureauCardId(),
                    candidate.applicationId(),
                    exception.getMessage()
            );
        }
    }
}