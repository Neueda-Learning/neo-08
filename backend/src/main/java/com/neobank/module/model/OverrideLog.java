package com.neobank.module.model;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Append-only audit log of manual outcome overrides. Never deleted.
 *
 * <p>Schema: {@code db/changelog/changes/006-create-override-log.yaml}</p>
 */
@Entity
@Table(name = "override_log")
public class OverrideLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "application_id", nullable = false, length = 64)
    private String applicationId;

    @Column(name = "old_outcome", nullable = false, length = 32)
    private String oldOutcome;

    @Column(name = "new_outcome", nullable = false, length = 32)
    private String newOutcome;

    @Column(nullable = false, length = 512)
    private String reason;

    @Column(nullable = false, length = 64)
    private String operator;

    @Column(name = "overridden_at", nullable = false)
    private Instant overriddenAt;

    protected OverrideLog() { /* JPA */ }

    public OverrideLog(String applicationId, String oldOutcome, String newOutcome,
                       String reason, String operator, Instant overriddenAt) {
        this.applicationId = applicationId;
        this.oldOutcome = oldOutcome;
        this.newOutcome = newOutcome;
        this.reason = reason;
        this.operator = operator;
        this.overriddenAt = overriddenAt;
    }

    public Long getId() { return id; }
    public String getApplicationId() { return applicationId; }
    public String getOldOutcome() { return oldOutcome; }
    public String getNewOutcome() { return newOutcome; }
    public String getReason() { return reason; }
    public String getOperator() { return operator; }
    public Instant getOverriddenAt() { return overriddenAt; }
}
