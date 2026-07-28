package com.neobank.module.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * This module's real table: one row per card-issuing decision.
 *
 * <p>Replaces {@link DemoShowcase} — see {@code db/changelog/changes/002-create-card-decision.yaml}
 * for the schema and {@link com.neobank.module.service.CardIssuingRules} for how a row's
 * {@code decision}, {@code reason} and {@code approvedLimit} are produced.</p>
 */
@Entity
@Table(name = "card_decision")
public class CardDecision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The id the orchestrator gave us — from the envelope, not from inside the application. */
    @Column(name = "application_id", nullable = false, length = 64)
    private String applicationId;

    /** ACCEPTED | REJECTED | REFERRED. Stored as text, not an ordinal. */
    @Column(nullable = false, length = 32)
    private String decision;

    /** The reason a bank employee could read back to the customer. */
    @Column(nullable = false, length = 512)
    private String reason;

    /** What the customer asked for. Null when the product block was missing or unrecognised. */
    @Column(name = "requested_limit")
    private Integer requestedLimit;

    /** What this module approved. Null unless {@code decision} is {@code ACCEPTED}. */
    @Column(name = "approved_limit")
    private Integer approvedLimit;

    /** When this module answered. */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected CardDecision() {
        // JPA
    }

    public CardDecision(String applicationId, Decision decision, String reason,
                         Integer requestedLimit, Integer approvedLimit) {
        this.applicationId = applicationId;
        this.decision = decision.name();
        this.reason = reason;
        this.requestedLimit = requestedLimit;
        this.approvedLimit = approvedLimit;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public String getApplicationId() {
        return applicationId;
    }

    public String getDecision() {
        return decision;
    }

    public String getReason() {
        return reason;
    }

    public Integer getRequestedLimit() {
        return requestedLimit;
    }

    public Integer getApprovedLimit() {
        return approvedLimit;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

