-- The schema list, and NOTHING else.
--
-- MySQL's entrypoint runs this on the FIRST start of an empty volume — and only then. If you
-- already had a `mysql-data` volume before the sidecar existed, this never ran for you and
-- `sidecar_db` does not exist; the sidecar will say so and tell you to `docker compose down -v`.
--
-- Schema isolation is the rule: every service migrates its OWN schema with Liquibase and never
-- reads another's. They integrate over REST, not through shared tables. The sidecar is held to
-- the same rule even though it is only a development tool.
CREATE DATABASE IF NOT EXISTS neo_08;
CREATE DATABASE IF NOT EXISTS sidecar_db;
-- ============================================================================
-- Module 08 · Card Issuing — Complete Table Schema DDL
--
-- Based on UC-00 through UC-10 from w3-docs/module-08-card-issuing-docs/
-- Target database: neo_08 (MySQL 8.4)
-- Production schema managed by Liquibase; this file is for architecture review
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1. issuing_config — Card issuing configuration (insert-only, versioned)
--    current = MAX(version)
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS issuing_config;
CREATE TABLE issuing_config (
                                version                INT           NOT NULL AUTO_INCREMENT COMMENT 'Version number; MAX(version) is the current active version',
                                pan_prefix             VARCHAR(16)   NOT NULL              COMMENT 'Reserved TEST PAN prefix, e.g. 999900',
                                pan_length             INT           NOT NULL              COMMENT 'Total PAN length including Luhn check digit, default 16',
                                delivery_countries     JSON          NOT NULL              COMMENT 'Countries the bureau can ship to, e.g. ["GB","IE"]',
                                required_address_fields JSON         NOT NULL              COMMENT 'Required fields for alternate address, e.g. ["line1","city","postcode","country"]',
                                bureau_base_url        VARCHAR(256)  NOT NULL              COMMENT 'Mock card personalisation bureau endpoint',
                                design_map             JSON              NULL              COMMENT '(UC-09) productCode -> {designCode, tier} mapping',
                                effective_from         TIMESTAMP     NOT NULL              COMMENT 'When this version takes effect',
                                created_at             TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Row creation timestamp',

                                PRIMARY KEY (version),

                                INDEX idx_issuing_config_effective (effective_from)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- 2. card_record — Card record (core table, N:1 -> issuing_config)
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS card_record;
CREATE TABLE card_record (
                             application_id         VARCHAR(64)   NOT NULL              COMMENT 'Journey unique key; the only applicant-related field stored',
                             outcome                VARCHAR(32)   NOT NULL DEFAULT 'IN_PROGRESS' COMMENT 'ISSUED | FAILED | IN_PROGRESS',
                             reference              VARCHAR(64)   NOT NULL              COMMENT 'Human-readable case number, e.g. crd-000064',
                             pan_last4              CHAR(4)           NULL              COMMENT 'Last 4 PAN digits (physically cannot store more)',
                             pan_hash               CHAR(64)          NULL              COMMENT 'Salted SHA-256 of full PAN; plaintext never stored',
                             bureau_card_id         VARCHAR(64)       NULL              COMMENT 'Bureau card ID, e.g. bur-77103',
                             bureau_status          VARCHAR(32)       NULL              COMMENT 'REQUESTED | PERSONALISED | DISPATCHED; NULL when bureau not yet called',
                             dispatch_ref           VARCHAR(64)       NULL              COMMENT 'Postal tracking number, e.g. RM-2214-9915',
                             account_id             VARCHAR(64)       NULL              COMMENT 'Module 7 linked account ID, e.g. acc-000123',
                             product_code           VARCHAR(64)   NOT NULL              COMMENT 'Product code, e.g. CREDIT_CARD_REWARDS',
                             manual_address         TINYINT(1)    NOT NULL DEFAULT 0    COMMENT 'Whether an operator corrected the address',
                             issuing_config_version INT           NOT NULL              COMMENT 'FK -> issuing_config.version; permanently pinned at issue time',
                             design_code            VARCHAR(32)       NULL              COMMENT '(UC-09) Card design code, e.g. GOLD',
                             tier                   VARCHAR(32)       NULL              COMMENT '(UC-09) Card tier',
                             issued_at              TIMESTAMP         NULL              COMMENT 'Issue completion time (PAN generated + bureau instruction sent)',
                             created_at             TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Row creation timestamp',
                             updated_at             TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Row last update timestamp',

                             PRIMARY KEY (application_id),

                             INDEX idx_card_record_outcome       (outcome),
                             INDEX idx_card_record_bureau_status (bureau_status),
                             INDEX idx_card_record_product_code  (product_code),
                             INDEX idx_card_record_issued_at     (issued_at),

                             CONSTRAINT fk_card_record_config
                                 FOREIGN KEY (issuing_config_version) REFERENCES issuing_config(version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- 3. card_status_history — Card status history (append-only, N:1 -> card_record)
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS card_status_history;
CREATE TABLE card_status_history (
                                     id               BIGINT          NOT NULL AUTO_INCREMENT COMMENT 'Surrogate primary key',
                                     application_id   VARCHAR(64)     NOT NULL              COMMENT 'FK -> card_record.application_id',
                                     status           VARCHAR(32)     NOT NULL              COMMENT 'REQUESTED | PERSONALISED | DISPATCHED',
                                     source           VARCHAR(16)     NOT NULL              COMMENT 'ISSUE (initial instruction) | POLL (scheduled polling)',
                                     observed_at      TIMESTAMP       NOT NULL              COMMENT 'When this module observed the status transition',

                                     PRIMARY KEY (id),

                                     INDEX idx_csh_application (application_id),
                                     INDEX idx_csh_observed    (observed_at),

                                     CONSTRAINT fk_csh_card_record
                                         FOREIGN KEY (application_id) REFERENCES card_record(application_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- 4. override_log — Manual override audit log (append-only, N:1 -> card_record)
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS override_log;
CREATE TABLE override_log (
                              id               BIGINT          NOT NULL AUTO_INCREMENT COMMENT 'Surrogate primary key',
                              application_id   VARCHAR(64)     NOT NULL              COMMENT 'FK -> card_record.application_id',
                              old_outcome      VARCHAR(32)     NOT NULL              COMMENT 'Outcome before override',
                              new_outcome      VARCHAR(32)     NOT NULL              COMMENT 'Outcome after override',
                              reason           VARCHAR(512)    NOT NULL              COMMENT 'Mandatory justification entered by operator',
                              operator         VARCHAR(64)     NOT NULL              COMMENT 'Operator identifier who performed the override',
                              overridden_at    TIMESTAMP       NOT NULL              COMMENT 'Override timestamp',

                              PRIMARY KEY (id),

                              INDEX idx_ol_application (application_id),
                              INDEX idx_ol_operator    (operator),
                              INDEX idx_ol_overridden  (overridden_at),

                              CONSTRAINT fk_ol_card_record
                                  FOREIGN KEY (application_id) REFERENCES card_record(application_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
