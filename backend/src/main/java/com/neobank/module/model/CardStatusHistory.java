package com.neobank.module.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Append-only audit of bureau status transitions.
 * One row is written with the initial issue; later polling can append changes.
 */
@Entity
@Table(name = "card_status_history")
public class CardStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "application_id", nullable = false, length = 64)
    private String applicationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private BureauStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private StatusSource source;

    @Column(name = "observed_at", nullable = false)
    private Instant observedAt;

    @Column(name = "dispatch_ref", length = 64)
    private String dispatchRef;

    protected CardStatusHistory() {
        // JPA
    }

    public CardStatusHistory(
            String applicationId,
            BureauStatus status,
            StatusSource source,
            Instant observedAt,
            String dispatchRef) {
        this.applicationId = applicationId;
        this.status = status;
        this.source = source;
        this.observedAt = observedAt;
        this.dispatchRef = dispatchRef;
    }

    public Long getId() {
        return id;
    }

    public String getApplicationId() {
        return applicationId;
    }

    public BureauStatus getStatus() {
        return status;
    }

    public StatusSource getSource() {
        return source;
    }

    public Instant getObservedAt() {
        return observedAt;
    }

    public String getDispatchRef() {
        return dispatchRef;
    }
}
