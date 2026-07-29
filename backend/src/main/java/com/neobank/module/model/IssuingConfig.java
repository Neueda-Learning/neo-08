package com.neobank.module.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;

@Entity
@Immutable
@Table(name = "issuing_config")
public class IssuingConfig {

    @Id
    @Column(
            name = "version",
            nullable = false,
            updatable = false
    )
    private Integer version;

    @Column(
            name = "pan_prefix",
            length = 6,
            nullable = false,
            updatable = false
    )
    private String panPrefix;

    @Column(
            name = "pan_length",
            nullable = false,
            updatable = false
    )
    private int panLength;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(
            name = "delivery_countries",
            columnDefinition = "json",
            nullable = false,
            updatable = false
    )
    private List<String> deliveryCountries;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(
            name = "required_address_fields",
            columnDefinition = "json",
            nullable = false,
            updatable = false
    )
    private List<String> requiredAddressFields;

    @Column(
            name = "bureau_base_url",
            length = 256,
            nullable = false,
            updatable = false
    )
    private String bureauBaseUrl;

    @Column(
            name = "effective_from",
            nullable = false,
            updatable = false
    )
    private Instant effectiveFrom;

    protected IssuingConfig() {
        // JPA
    }

    public IssuingConfig(
            Integer version,
            String panPrefix,
            int panLength,
            List<String> deliveryCountries,
            List<String> requiredAddressFields,
            String bureauBaseUrl,
            Instant effectiveFrom) {

        this.version = version;
        this.panPrefix = panPrefix;
        this.panLength = panLength;
        this.deliveryCountries = List.copyOf(deliveryCountries);
        this.requiredAddressFields =
                List.copyOf(requiredAddressFields);
        this.bureauBaseUrl = bureauBaseUrl;
        this.effectiveFrom = effectiveFrom;
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

    public List<String> getDeliveryCountries() {
        return List.copyOf(deliveryCountries);
    }

    public List<String> getRequiredAddressFields() {
        return List.copyOf(requiredAddressFields);
    }

    public String getBureauBaseUrl() {
        return bureauBaseUrl;
    }

    public Instant getEffectiveFrom() {
        return effectiveFrom;
    }
}