package com.neobank.module.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 卡片记录 — 发卡模块的核心表。
 *
 * <p>{@code application_id} 是主键：它由编排器提供，<b>不</b>自动生成。</p>
 */
@Entity
@Table(name = "card_record")
public class CardRecord {

    @Id
    @Column(name = "application_id", length = 64, nullable = false)
    private String applicationId;

    @Enumerated(EnumType.STRING)
    @Column(length = 32, nullable = false)
    private Outcome outcome = Outcome.IN_PROGRESS;

    @Column(length = 64, nullable = false)
    private String reference;

    @Column(name = "pan_last4", columnDefinition = "CHAR(4)")
    private String panLast4;

    @Column(name = "pan_hash", columnDefinition = "CHAR(64)")
    private String panHash;

    @Column(name = "bureau_card_id", length = 64)
    private String bureauCardId;

    @Column(name = "bureau_status", length = 32)
    private String bureauStatus;

    @Column(name = "dispatch_ref", length = 64)
    private String dispatchRef;

    @Column(name = "account_id", length = 64)
    private String accountId;

    @Column(name = "product_code", length = 64, nullable = false)
    private String productCode;

    @Column(name = "manual_address", nullable = false)
    private boolean manualAddress;

    @Column(name = "issuing_config_version", nullable = false)
    private Integer issuingConfigVersion;

    @Column(name = "design_code", length = 32)
    private String designCode;

    @Column(length = 32)
    private String tier;

    @Column(name = "issued_at")
    private Instant issuedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CardRecord() {
        // JPA
    }

    /**
     * 便捷构造函数 — 设置 outcome 为 IN_PROGRESS，manualAddress 为 false。
     */
    public CardRecord(String applicationId, String productCode, String reference,
                      Integer issuingConfigVersion) {
        this.applicationId = applicationId;
        this.productCode = productCode;
        this.reference = reference;
        this.issuingConfigVersion = issuingConfigVersion;
        this.outcome = Outcome.IN_PROGRESS;
        this.manualAddress = false;
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

    // ---- getters ----

    public String getApplicationId() {
        return applicationId;
    }

    public String getOutcome() {
        return outcome.name();
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

    public String getBureauStatus() {
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

    public String getDesignCode() {
        return designCode;
    }

    public String getTier() {
        return tier;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
