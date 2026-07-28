package com.neobank.module.controller;

import com.neobank.module.model.BureauDials;
import com.neobank.module.model.BureauStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/bureau/cards")
public class MockBureauCardController {

    private final BureauDials bureauDials;

    /*
     * UC05 mock data only.
     * All cards disappear when the application restarts.
     */
    private final ConcurrentHashMap<String, MockCard>
            cardsByApplicationId = new ConcurrentHashMap<>();

    public MockBureauCardController(BureauDials bureauDials) {
        this.bureauDials = bureauDials;
    }

    /**
     * Creates a new mock bureau card.
     *
     * Repeating the same applicationId returns the existing card,
     * so the operation is idempotent.
     */
    @PostMapping
    public ResponseEntity<BureauCardResponse> createCard(
            @Valid @RequestBody CreateBureauCardRequest request) {

        applyLatency();

        if (bureauDials.isKillSwitch()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Mock bureau is unavailable because killSwitch is enabled"
            );
        }

        MockCard card = cardsByApplicationId.computeIfAbsent(
                request.applicationId(),
                applicationId -> new MockCard(
                        generateBureauCardId(),
                        applicationId,
                        Instant.now()
                )
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(toResponse(card));
    }

    /**
     * Returns the current card lifecycle status.
     *
     * Status changes automatically according to secondsPerStage:
     * REQUESTED -> PERSONALISED -> DISPATCHED.
     */
    @GetMapping("/{bureauCardId}")
    public ResponseEntity<BureauCardResponse> getCard(
            @PathVariable String bureauCardId) {

        applyLatency();

        MockCard card = cardsByApplicationId.values()
                .stream()
                .filter(existing ->
                        existing.bureauCardId.equals(bureauCardId))
                .findFirst()
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Bureau card not found: "
                                        + bureauCardId
                        )
                );

        return ResponseEntity.ok(toResponse(card));
    }

    /**
     * Builds the response and creates the dispatch reference
     * when the card reaches DISPATCHED.
     */
    private BureauCardResponse toResponse(MockCard card) {

        BureauStatus status = calculateStatus(card);

        if (status == BureauStatus.DISPATCHED
                && card.dispatchRef == null) {

            synchronized (card) {
                if (card.dispatchRef == null) {
                    card.dispatchRef =
                            generateDispatchReference();
                }
            }
        }

        return new BureauCardResponse(
                card.bureauCardId,
                card.applicationId,
                status,
                card.dispatchRef
        );
    }

    /**
     * Calculates status from elapsed time.
     *
     * For secondsPerStage = 5:
     * 0-4 seconds   -> REQUESTED
     * 5-9 seconds   -> PERSONALISED
     * 10+ seconds   -> DISPATCHED
     */
    private BureauStatus calculateStatus(MockCard card) {

        long elapsedSeconds = Duration.between(
                card.createdAt,
                Instant.now()
        ).getSeconds();

        long secondsPerStage =
                bureauDials.getSecondsPerStage();

        if (elapsedSeconds >= secondsPerStage * 2) {
            return BureauStatus.DISPATCHED;
        }

        if (elapsedSeconds >= secondsPerStage) {
            return BureauStatus.PERSONALISED;
        }

        return BureauStatus.REQUESTED;
    }

    /**
     * Applies the configured mock response latency.
     */
    private void applyLatency() {

        int latencyMs = bureauDials.getLatencyMs();

        if (latencyMs <= 0) {
            return;
        }

        try {
            Thread.sleep(latencyMs);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();

            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Mock bureau request was interrupted",
                    exception
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

    /**
     * Request body for POST /bureau/cards.
     */
    public record CreateBureauCardRequest(

            @NotBlank(
                    message = "applicationId is required"
            )
            @Size(
                    max = 64,
                    message = "applicationId must not exceed 64 characters"
            )
            String applicationId

    ) {
    }

    /**
     * Response returned from the mock bureau.
     */
    public record BureauCardResponse(
            String bureauCardId,
            String applicationId,
            BureauStatus status,
            String dispatchRef
    ) {
    }

    /**
     * Internal in-memory card representation.
     */
    private static class MockCard {

        private final String bureauCardId;
        private final String applicationId;
        private final Instant createdAt;

        private volatile String dispatchRef;

        private MockCard(
                String bureauCardId,
                String applicationId,
                Instant createdAt) {

            this.bureauCardId = bureauCardId;
            this.applicationId = applicationId;
            this.createdAt = createdAt;
        }
    }
}