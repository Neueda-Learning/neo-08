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
 * 发卡配置 — insert-only 版本化表。
 *
 * <p>{@code MAX(version)} 始终代表当前生效的配置。
 * 每行在插入时即固化，永不更新。</p>
 */
@Entity
@Table(name = "issuing_config")
public class IssuingConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer version;

    @Column(name = "pan_prefix", length = 16, nullable = false)
    private String panPrefix;

    @Column(name = "pan_length", nullable = false)
    private int panLength;

    @Column(name = "delivery_countries", columnDefinition = "TEXT", nullable = false)
    private String deliveryCountries;

    @Column(name = "required_address_fields", columnDefinition = "TEXT", nullable = false)
    private String requiredAddressFields;

    @Column(name = "bureau_base_url", length = 256, nullable = false)
    private String bureauBaseUrl;

    @Column(name = "design_map", columnDefinition = "TEXT")
    private String designMap;

    @Column(name = "effective_from", nullable = false)
    private Instant effectiveFrom;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected IssuingConfig() {
        // JPA
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Integer getVersion() {
        return version;
    }

    public String getPanPrefix() {
        return panPrefix;
    }

    public int getPanLength() {
        return panLength;
    }

    public String getDeliveryCountries() {
        return deliveryCountries;
    }

    public String getRequiredAddressFields() {
        return requiredAddressFields;
    }

    public String getBureauBaseUrl() {
        return bureauBaseUrl;
    }

    public String getDesignMap() {
        return designMap;
    }

    public Instant getEffectiveFrom() {
        return effectiveFrom;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
