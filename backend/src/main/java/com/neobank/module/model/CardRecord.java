package com.neobank.module.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * The durable UC-00 hand-off row.
 * No application payload field exists on this entity.
 */
@Entity
@Table(name = "card_record")
public class CardRecord {

    @Id
    @Column(
            name = "application_id",
            nullable = false,
            length = 64
    )
    private String applicationId;

    @Enumerated(EnumType.STRING)
    @Column(
            nullable = false,
            length = 32
    )
    private CardOutcome outcome;

    @Column(
            nullable = false,
            unique = true,
            length = 64
    )
    private String reference;

    @Column(
            name = "pan_last4",
            columnDefinition = "CHAR(4)"
    )
    private String panLast4;

    @Column(
            name = "pan_hash",
            columnDefinition = "CHAR(64)"
    )
    private String panHash;

    @Column(
            name = "bureau_card_id",
            length = 64
    )
    private String bureauCardId;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "bureau_status",
            length = 32
    )
    private BureauStatus bureauStatus;

    @Column(
            name = "dispatch_ref",
            length = 64
    )
    private String dispatchRef;

    @Column(
            name = "account_id",
            length = 64
    )
    private String accountId;

    @Column(
            name = "product_code",
            length = 64
    )
    private String productCode;

    @Column(
            name = "manual_address",
            nullable = false
    )
    private boolean manualAddress;

    @Column(
            name = "issuing_config_version"
    )
    private Integer issuingConfigVersion;

    @Column(
            name = "issued_at"
    )
    private Instant issuedAt;

    @Column(
            name = "reason_code",
            length = 64
    )
    private String reasonCode;

    protected CardRecord() {
        // JPA
    }

    public CardRecord(
            String applicationId,
            String reference) {

        this.applicationId = applicationId;
        this.reference = reference;
        this.outcome = CardOutcome.IN_PROGRESS;
        this.manualAddress = false;
    }

    public String getApplicationId() {
        return applicationId;
    }

    public CardOutcome getOutcome() {
        return outcome;
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

    public String getAccountId() {
        return accountId;
    }

    public String getProductCode() {
        return productCode;
    }

    public boolean isManualAddress() {
        return manualAddress;
    }

    public Integer getIssuingConfigVersion() {
        return issuingConfigVersion;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public void markIssued(
            String panLast4,
            String panHash,
            String bureauCardId,
            BureauStatus bureauStatus,
            Integer configVersion) {

        if (outcome != CardOutcome.IN_PROGRESS) {
            return;
        }

        this.outcome = CardOutcome.ISSUED;
        this.panLast4 = panLast4;
        this.panHash = panHash;
        this.bureauCardId = bureauCardId;
        this.bureauStatus = bureauStatus;
        this.issuingConfigVersion = configVersion;
        this.reasonCode = "CRD_ISSUED";
        this.issuedAt = Instant.now();
    }

    public void markFailed(
            String reasonCode) {

        if (outcome != CardOutcome.IN_PROGRESS) {
            return;
        }

        this.outcome = CardOutcome.FAILED;
        this.reasonCode = reasonCode;
    }
}