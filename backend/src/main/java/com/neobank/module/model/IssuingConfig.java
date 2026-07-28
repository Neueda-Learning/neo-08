package com.neobank.module.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Insert-only issuing policy. The current policy is the highest version, and
 * every completed card pins the version it used.
 *
 * <p>The list fields are stored as JSON text so the database remains easy
 * to inspect while the Java service can validate them strictly.</p>
 */
@Entity
@Table(name = "issuing_config")
public class IssuingConfig {

    @Id
    private Integer version;

    @Column(name = "pan_prefix", length = 15, nullable = false)
    private String panPrefix;

    @Column(name = "pan_length", nullable = false)
    private int panLength;

    @Column(name = "delivery_countries", length = 512, nullable = false)
    private String deliveryCountries;

    @Column(name = "allowed_product_codes", length = 512, nullable = false)
    private String allowedProductCodes;

    @Column(name = "required_address_fields", length = 512, nullable = false)
    private String requiredAddressFields;

    @Column(name = "effective_from", nullable = false)
    private Instant effectiveFrom;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected IssuingConfig() {
        // JPA
    }

    public IssuingConfig(
            Integer version,
            String panPrefix,
            int panLength,
            String deliveryCountries,
            String allowedProductCodes,
            String requiredAddressFields,
            Instant effectiveFrom) {
        this.version = version;
        this.panPrefix = panPrefix;
        this.panLength = panLength;
        this.deliveryCountries = deliveryCountries;
        this.allowedProductCodes = allowedProductCodes;
        this.requiredAddressFields = requiredAddressFields;
        this.effectiveFrom = effectiveFrom;
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

    public String getAllowedProductCodes() {
        return allowedProductCodes;
    }

    public String getRequiredAddressFields() {
        return requiredAddressFields;
    }

    public Instant getEffectiveFrom() {
        return effectiveFrom;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
