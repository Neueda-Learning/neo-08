package com.neobank.module.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;

/**
 * One durable card-issuing case, keyed by the orchestrator's application id.
 *
 * <p>The intake transaction initially writes only {@code applicationId},
 * {@code outcome} and timestamps. Applicant names and delivery addresses never
 * become columns. A successful worker stores only the last four digits and a
 * salted digest of the test PAN; the full PAN exists only in the issue method's
 * local scope.</p>
 */
@Entity
@Table(name = "card_record")
public class CardRecord {

    public static final String IN_PROGRESS = "in-progress";

    @Id
    @Column(name = "application_id", nullable = false, length = 64)
    private String applicationId;

    @Enumerated(EnumType.STRING)
    @Column(length = 32, nullable = false)
    private CardOutcome outcome;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision_status", length = 32)
    private Decision decisionStatus;

    @Column(name = "reason_code", length = 64)
    private String reasonCode;

    @Column(name = "decision_comment", length = 512)
    private String comment;

    @Column(length = 64)
    private String reference;

    @Column(name = "pan_last4", columnDefinition = "CHAR(4)")
    private String panLast4;

    @Column(name = "pan_hash", columnDefinition = "CHAR(64)")
    private String panHash;

    @Column(name = "bureau_card_id", length = 64)
    private String bureauCardId;

    @Enumerated(EnumType.STRING)
    @Column(name = "bureau_status", length = 32)
    private BureauStatus bureauStatus;

    @Column(name = "dispatch_ref", length = 64)
    private String dispatchRef;

    @Column(name = "product_code", length = 64)
    private String productCode;

    @Column(name = "issuing_config_version")
    private Integer issuingConfigVersion;

    @Column(name = "processing_started_at")
    private Instant processingStartedAt;

    @Column(name = "processing_token", length = 36)
    private String processingToken;

    @Column(name = "callback_delivered_at")
    private Instant callbackDeliveredAt;

    @Column(name = "callback_claimed_at")
    private Instant callbackClaimedAt;

    @Column(name = "callback_claim_token", length = 36)
    private String callbackClaimToken;

    @Column(name = "callback_attempts", nullable = false)
    private int callbackAttempts;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CardRecord() {
        // JPA
    }

    private CardRecord(String applicationId) {
        this.applicationId = Objects.requireNonNull(applicationId, "applicationId");
        this.outcome = CardOutcome.IN_PROGRESS;
    }

    public static CardRecord newInProgress(String applicationId) {
        return new CardRecord(applicationId);
    }

    public void markIssued(
            String reasonCode,
            String comment,
            String reference,
            String panLast4,
            String panHash,
            String bureauCardId,
            BureauStatus bureauStatus,
            String productCode,
            int issuingConfigVersion,
            Instant decidedAt) {
        requireInProgress();
        this.outcome = CardOutcome.ISSUED;
        this.decisionStatus = Decision.ACCEPTED;
        this.reasonCode = requireText(reasonCode, "reasonCode");
        this.comment = requireText(comment, "comment");
        this.reference = requireText(reference, "reference");
        this.panLast4 = requireLength(panLast4, 4, "panLast4");
        this.panHash = requireLength(panHash, 64, "panHash");
        this.bureauCardId = requireText(bureauCardId, "bureauCardId");
        this.bureauStatus = Objects.requireNonNull(bureauStatus, "bureauStatus");
        this.productCode = requireText(productCode, "productCode");
        this.issuingConfigVersion = issuingConfigVersion;
        completeWork(decidedAt);
    }

    public void markFailed(
            String reasonCode,
            String comment,
            String productCode,
            Integer issuingConfigVersion,
            Instant decidedAt) {
        requireInProgress();
        this.outcome = CardOutcome.FAILED;
        // Card issuance happens after approval. Address/provider failures are
        // repairable operational cases, so they park rather than reject the journey.
        this.decisionStatus = Decision.REFERRED;
        this.reasonCode = requireText(reasonCode, "reasonCode");
        this.comment = requireText(comment, "comment");
        this.productCode = productCode;
        this.issuingConfigVersion = issuingConfigVersion;
        completeWork(decidedAt);
    }

    /**
     * Claims an in-progress case unless another worker still owns a live lease.
     * A stale lease may be taken over after a process interruption.
     */
    public boolean claimProcessing(String token, Instant startedAt, Instant staleBefore) {
        requireInProgress();
        requireText(token, "token");
        Objects.requireNonNull(startedAt, "startedAt");
        Objects.requireNonNull(staleBefore, "staleBefore");
        if (processingStartedAt != null && processingStartedAt.isAfter(staleBefore)) {
            return false;
        }
        processingToken = token;
        processingStartedAt = startedAt;
        return true;
    }

    /** Extends a lease only for the worker that still owns it. */
    public boolean renewProcessingClaim(String token, Instant startedAt) {
        if (!isInProgress() || !isClaimedBy(token)) {
            return false;
        }
        processingStartedAt = Objects.requireNonNull(startedAt, "startedAt");
        return true;
    }

    public boolean isClaimedBy(String token) {
        return token != null && token.equals(processingToken);
    }

    /** Releases a claim when submission failed or its owner exceeded the lease. */
    public boolean releaseProcessingClaim(String token) {
        if (isInProgress() && (token == null || isClaimedBy(token))) {
            processingToken = null;
            processingStartedAt = null;
            return true;
        }
        return false;
    }

    /** Claims one pending callback; stale claims can be fenced and retried. */
    public boolean claimCallback(String token, Instant claimedAt, Instant staleBefore) {
        requireText(token, "token");
        Objects.requireNonNull(claimedAt, "claimedAt");
        Objects.requireNonNull(staleBefore, "staleBefore");
        if (!isDecided()
                || callbackDeliveredAt != null
                || (callbackClaimedAt != null && callbackClaimedAt.isAfter(staleBefore))) {
            return false;
        }
        callbackClaimToken = token;
        callbackClaimedAt = claimedAt;
        callbackAttempts++;
        return true;
    }

    /** Records success only for the dispatcher that still owns this callback. */
    public boolean markCallbackDelivered(String token, Instant deliveredAt) {
        if (!isDecided()
                || callbackDeliveredAt != null
                || token == null
                || !token.equals(callbackClaimToken)) {
            return false;
        }
        callbackDeliveredAt = Objects.requireNonNull(deliveredAt, "deliveredAt");
        callbackClaimToken = null;
        callbackClaimedAt = null;
        return true;
    }

    public boolean releaseCallbackClaim(String token) {
        if (callbackDeliveredAt == null
                && token != null
                && token.equals(callbackClaimToken)) {
            callbackClaimToken = null;
            callbackClaimedAt = null;
            return true;
        }
        return false;
    }

    private void completeWork(Instant completedAt) {
        this.decidedAt = Objects.requireNonNull(completedAt, "decidedAt");
        this.processingToken = null;
        this.processingStartedAt = null;
        this.callbackDeliveredAt = null;
        this.callbackClaimedAt = null;
        this.callbackClaimToken = null;
        this.callbackAttempts = 0;
    }

    private void requireInProgress() {
        if (!isInProgress()) {
            throw new IllegalStateException("card case is already decided");
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    private static String requireLength(String value, int length, String field) {
        if (value == null || value.length() != length) {
            throw new IllegalArgumentException(field + " must be exactly " + length + " characters");
        }
        return value;
    }

    public boolean isInProgress() {
        return outcome == CardOutcome.IN_PROGRESS;
    }

    public boolean isDecided() {
        return decisionStatus != null;
    }

    /** The exact status persisted for callback replay and exposed to the board. */
    public String status() {
        return isInProgress() ? IN_PROGRESS : decisionStatus.name();
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public String getApplicationId() {
        return applicationId;
    }

    public CardOutcome getOutcome() {
        return outcome;
    }

    public Decision getDecisionStatus() {
        return decisionStatus;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public String getComment() {
        return comment;
    }

    public String getReference() {
        return reference;
    }

    public String getPanLast4() {
        return panLast4;
    }

    public String getPanHash() {
        return panHash;
    }

    public String getBureauCardId() {
        return bureauCardId;
    }

    public BureauStatus getBureauStatus() {
        return bureauStatus;
    }

    public String getDispatchRef() {
        return dispatchRef;
    }

    public String getProductCode() {
        return productCode;
    }

    public Integer getIssuingConfigVersion() {
        return issuingConfigVersion;
    }

    public Instant getProcessingStartedAt() {
        return processingStartedAt;
    }

    public String getProcessingToken() {
        return processingToken;
    }

    public Instant getCallbackDeliveredAt() {
        return callbackDeliveredAt;
    }

    public Instant getCallbackClaimedAt() {
        return callbackClaimedAt;
    }

    public String getCallbackClaimToken() {
        return callbackClaimToken;
    }

    public int getCallbackAttempts() {
        return callbackAttempts;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
