package com.neobank.module.model;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Append-only audit of bureau status transitions.
 * One row per observed change — written by initial issue flow and the scheduled poller.
 *
 * <p>Schema: {@code db/changelog/changes/005-create-card-status-history.yaml}</p>
 */
@Entity
@Table(name = "card_status_history")
public class CardStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "application_id", nullable = false, length = 64)
    private String applicationId;

    /** REQUESTED | PERSONALISED | DISPATCHED | PIN_MAILER_QUEUED | PIN_MAILER_DISPATCHED */
    @Column(nullable = false, length = 32)
    private String status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private StatusSource source;

    @Column(name = "observed_at", nullable = false)
    private Instant observedAt;

    protected CardStatusHistory() { /* JPA */ }

    public CardStatusHistory(String applicationId, String status, StatusSource source, Instant observedAt) {
        this.applicationId = applicationId;
        this.status = status;
        this.source = source;
        this.observedAt = observedAt;
    }

    public Long getId() { return id; }
    public String getApplicationId() { return applicationId; }
    public String getStatus() { return status; }
    public StatusSource getSource() { return source; }
    public Instant getObservedAt() { return observedAt; }
}
