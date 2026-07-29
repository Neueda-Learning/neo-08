package com.neobank.module.service;

import com.neobank.module.exception.BureauUnavailableException;
import com.neobank.module.model.BureauDials;
import com.neobank.module.model.BureauStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MockBureauService {

    private final BureauDials bureauDials;

    private final ConcurrentHashMap<String, MockCard>
            cardsByApplicationId =
            new ConcurrentHashMap<>();

    public MockBureauService(BureauDials bureauDials) {
        this.bureauDials = bureauDials;
    }

    /**
     * Creates one card per applicationId.
     * Repeated calls return the existing card.
     */
    public BureauResult createCard(
            String applicationId) {

        applyLatency();

        if (bureauDials.isKillSwitch()) {
            throw new BureauUnavailableException(
                    "Mock bureau is unavailable "
                            + "because killSwitch is enabled"
            );
        }

        MockCard card =
                cardsByApplicationId.computeIfAbsent(
                        applicationId,
                        id -> new MockCard(
                                generateBureauCardId(),
                                id,
                                Instant.now()
                        )
                );

        return toResult(card);
    }

    /**
     * Finds a bureau card by its bureauCardId.
     */
    public BureauResult getCard(
            String bureauCardId) {

        applyLatency();

        MockCard card =
                cardsByApplicationId.values()
                        .stream()
                        .filter(existing ->
                                existing.bureauCardId
                                        .equals(bureauCardId))
                        .findFirst()
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "Bureau card not found: "
                                                + bureauCardId
                                )
                        );

        return toResult(card);
    }

    /**
     * Calculates the current lifecycle status.
     */
    private BureauResult toResult(
            MockCard card) {

        BureauStatus status =
                calculateStatus(card);

        if (status == BureauStatus.DISPATCHED
                && card.dispatchRef == null) {

            synchronized (card) {
                if (card.dispatchRef == null) {
                    card.dispatchRef =
                            generateDispatchReference();
                }
            }
        }

        return new BureauResult(
                card.bureauCardId,
                card.applicationId,
                status,
                card.dispatchRef
        );
    }

    /**
     * REQUESTED -> PERSONALISED -> DISPATCHED.
     */
    private BureauStatus calculateStatus(
            MockCard card) {

        long elapsedSeconds =
                Duration.between(
                        card.createdAt,
                        Instant.now()
                ).getSeconds();

        long secondsPerStage =
                bureauDials.getSecondsPerStage();

        if (elapsedSeconds
                >= secondsPerStage * 2) {

            return BureauStatus.DISPATCHED;
        }

        if (elapsedSeconds
                >= secondsPerStage) {

            return BureauStatus.PERSONALISED;
        }

        return BureauStatus.REQUESTED;
    }

    /**
     * Applies the configured UC05 latency.
     */
    private void applyLatency() {

        int latencyMs =
                bureauDials.getLatencyMs();

        if (latencyMs <= 0) {
            return;
        }

        try {
            Thread.sleep(latencyMs);

        } catch (InterruptedException exception) {

            Thread.currentThread().interrupt();

            throw new BureauUnavailableException(
                    "Mock bureau request was interrupted"
            );
        }
    }

    private String generateBureauCardId() {

        return "bur-"
                + UUID.randomUUID()
                .toString()
                .substring(0, 8);
    }

    private String generateDispatchReference() {

        return "RM-"
                + ThreadLocalRandom.current()
                .nextInt(1000, 10000)
                + "-"
                + ThreadLocalRandom.current()
                .nextInt(1000, 10000);
    }

    public record BureauResult(
            String bureauCardId,
            String applicationId,
            BureauStatus status,
            String dispatchRef
    ) {
    }

    private static class MockCard {

        private final String bureauCardId;
        private final String applicationId;
        private final Instant createdAt;

        private volatile String dispatchRef;

        private MockCard(
                String bureauCardId,
                String applicationId,
                Instant createdAt) {

            this.bureauCardId =
                    bureauCardId;

            this.applicationId =
                    applicationId;

            this.createdAt =
                    createdAt;
        }
    }
}