/*
package com.neobank.module.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neobank.module.dto.FailedCardRecord;
import com.neobank.module.dto.FailedIssueView;
import com.neobank.module.dto.IssuingConfigSnapshot;
import com.neobank.module.model.CardOutcome;
import com.neobank.module.model.FailureReason;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

*/
/** UC-04 queue and retry persistence. *//*

@Repository
public class FailedIssueRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public FailedIssueRepository(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public List<FailedIssueView> findOldestFailed(int limit) {
        return jdbc.query(
                """
                SELECT application_id, failure_reason, last_attempt_at
                  FROM card_record
                 WHERE outcome = 'FAILED'
                   AND failure_reason IS NOT NULL
                 ORDER BY last_attempt_at ASC, application_id ASC
                 LIMIT ?
                """,
                (result, rowNumber) -> {
                    FailureReason reason =
                            FailureReason.valueOf(result.getString("failure_reason"));
                    Timestamp attemptedAt = result.getTimestamp("last_attempt_at");
                    return new FailedIssueView(
                            result.getString("application_id"),
                            reason,
                            reason.action(),
                            attemptedAt == null ? null : attemptedAt.toInstant());
                },
                limit);
    }

    public Optional<FailedCardRecord> findCard(String applicationId) {
        List<FailedCardRecord> rows = jdbc.query(
                """
                SELECT application_id, outcome, reference, pan_last4, pan_hash, account_id,
                       product_code, failure_reason
                  FROM card_record
                 WHERE application_id = ?
                """,
                (result, rowNumber) -> {
                    String reason = result.getString("failure_reason");
                    return new FailedCardRecord(
                            result.getString("application_id"),
                            CardOutcome.valueOf(result.getString("outcome")),
                            result.getString("reference"),
                            result.getString("pan_last4"),
                            result.getString("pan_hash"),
                            result.getString("account_id"),
                            result.getString("product_code"),
                            reason == null ? null : FailureReason.valueOf(reason));
                },
                applicationId);
        return rows.stream().findFirst();
    }

    public Optional<IssuingConfigSnapshot> findCurrentConfig() {
        List<IssuingConfigSnapshot> rows = jdbc.query(
                """
                SELECT version, pan_prefix, pan_length, delivery_countries,
                       required_address_fields, bureau_base_url
                  FROM issuing_config
                 ORDER BY version DESC
                 LIMIT 1
                """,
                (result, rowNumber) -> new IssuingConfigSnapshot(
                        result.getInt("version"),
                        result.getString("pan_prefix"),
                        result.getInt("pan_length"),
                        stringList(result.getString("delivery_countries")),
                        stringList(result.getString("required_address_fields")),
                        result.getString("bureau_base_url")));
        return rows.stream().findFirst();
    }

    public void recordUnavailableAttempt(String applicationId, Instant attemptedAt) {
        jdbc.update(
                """
                UPDATE card_record
                   SET failure_reason = 'CRD_BUREAU_UNAVAILABLE',
                       last_attempt_at = ?
                 WHERE application_id = ?
                   AND outcome = 'FAILED'
                """,
                Timestamp.from(attemptedAt),
                applicationId);
    }

    @Transactional
    public boolean markIssued(
            String applicationId,
            String panLast4,
            String panHash,
            String bureauCardId,
            int configVersion,
            boolean manualAddress,
            Instant attemptedAt) {
        int changed = jdbc.update(
                """
                UPDATE card_record
                   SET outcome = 'ISSUED',
                       pan_last4 = ?,
                       pan_hash = ?,
                       bureau_card_id = ?,
                       bureau_status = 'REQUESTED',
                       dispatch_ref = NULL,
                       manual_address = ?,
                       issuing_config_version = ?,
                       issued_at = ?,
                       failure_reason = NULL,
                       last_attempt_at = ?
                 WHERE application_id = ?
                   AND outcome = 'FAILED'
                """,
                panLast4,
                panHash,
                bureauCardId,
                manualAddress,
                configVersion,
                Timestamp.from(attemptedAt),
                Timestamp.from(attemptedAt),
                applicationId);
        if (changed == 0) {
            return false;
        }

        jdbc.update(
                """
                INSERT INTO card_status_history
                       (application_id, status, source, observed_at, manual_address)
                VALUES (?, 'REQUESTED', 'ISSUE', ?, ?)
                """,
                applicationId,
                Timestamp.from(attemptedAt),
                manualAddress);
        return true;
    }

    private List<String> stringList(String value) {
        try {
            JsonNode node = json.readTree(value);
            if (node != null && node.isTextual()) {
                node = json.readTree(node.asText());
            }
            if (node == null || !node.isArray()) {
                throw new IllegalStateException("IssuingConfig JSON value is not an array");
            }
            List<String> values = new ArrayList<>();
            node.forEach(item -> values.add(item.asText()));
            return values;
        } catch (Exception invalidJson) {
            throw new IllegalStateException("IssuingConfig contains invalid JSON", invalidJson);
        }
    }
}
*/
package com.neobank.module.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neobank.module.dto.FailedCardRecord;
import com.neobank.module.dto.FailedIssueView;
import com.neobank.module.dto.IssuingConfigSnapshot;
import com.neobank.module.model.CardOutcome;
import com.neobank.module.model.FailureReason;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** UC-04 queue and retry persistence. */
@Repository
public class FailedIssueRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public FailedIssueRepository(
            JdbcTemplate jdbc,
            ObjectMapper json) {

        this.jdbc = jdbc;
        this.json = json;
    }

    public List<FailedIssueView> findOldestFailed(
            int limit) {

        return jdbc.query(
                """
                SELECT application_id,
                       failure_reason,
                       last_attempt_at
                  FROM card_record
                 WHERE outcome = 'FAILED'
                   AND failure_reason IS NOT NULL
                 ORDER BY last_attempt_at ASC,
                          application_id ASC
                 LIMIT ?
                """,
                (result, rowNumber) -> {
                    FailureReason reason =
                            FailureReason.valueOf(
                                    result.getString(
                                            "failure_reason"));

                    Timestamp attemptedAt =
                            result.getTimestamp(
                                    "last_attempt_at");

                    return new FailedIssueView(
                            result.getString(
                                    "application_id"),
                            reason,
                            reason.action(),
                            attemptedAt == null
                                    ? null
                                    : attemptedAt.toInstant());
                },
                limit);
    }

    public Optional<FailedCardRecord> findCard(
            String applicationId) {

        List<FailedCardRecord> rows =
                jdbc.query(
                        """
                        SELECT application_id,
                               outcome,
                               reference,
                               pan_last4,
                               pan_hash,
                               account_id,
                               product_code,
                               failure_reason
                          FROM card_record
                         WHERE application_id = ?
                        """,
                        (result, rowNumber) -> {
                            String reason =
                                    result.getString(
                                            "failure_reason");

                            return new FailedCardRecord(
                                    result.getString(
                                            "application_id"),
                                    CardOutcome.valueOf(
                                            result.getString(
                                                    "outcome")),
                                    result.getString(
                                            "reference"),
                                    result.getString(
                                            "pan_last4"),
                                    result.getString(
                                            "pan_hash"),
                                    result.getString(
                                            "account_id"),
                                    result.getString(
                                            "product_code"),
                                    reason == null
                                            ? null
                                            : FailureReason.valueOf(
                                            reason));
                        },
                        applicationId);

        return rows.stream().findFirst();
    }

    public Optional<IssuingConfigSnapshot>
    findCurrentConfig() {

        List<IssuingConfigSnapshot> rows =
                jdbc.query(
                        """
                        SELECT version,
                               pan_prefix,
                               pan_length,
                               delivery_countries,
                               required_address_fields,
                               bureau_base_url
                          FROM issuing_config
                         ORDER BY version DESC
                         LIMIT 1
                        """,
                        (result, rowNumber) ->
                                new IssuingConfigSnapshot(
                                        result.getInt(
                                                "version"),
                                        result.getString(
                                                "pan_prefix"),
                                        result.getInt(
                                                "pan_length"),
                                        stringList(
                                                result.getString(
                                                        "delivery_countries")),
                                        stringList(
                                                result.getString(
                                                        "required_address_fields")),
                                        result.getString(
                                                "bureau_base_url")));

        return rows.stream().findFirst();
    }

    public void recordUnavailableAttempt(
            String applicationId,
            Instant attemptedAt) {

        jdbc.update(
                """
                UPDATE card_record
                   SET failure_reason =
                           'CRD_BUREAU_UNAVAILABLE',
                       last_attempt_at = ?
                 WHERE application_id = ?
                   AND outcome = 'FAILED'
                """,
                Timestamp.from(attemptedAt),
                applicationId);
    }

    /**
     * Updates the original FAILED CardRecord and inserts
     * the initial Bureau lifecycle observation atomically.
     */
    @Transactional
    public boolean markIssued(
            String applicationId,
            String panLast4,
            String panHash,
            String bureauCardId,
            int configVersion,
            boolean manualAddress,
            Instant issuedAt) {

        int changed =
                jdbc.update(
                        """
                        UPDATE card_record
                           SET outcome = 'ISSUED',
                               pan_last4 = ?,
                               pan_hash = ?,
                               bureau_card_id = ?,
                               bureau_status = 'REQUESTED',
                               dispatch_ref = NULL,
                               manual_address = ?,
                               issuing_config_version = ?,
                               issued_at = ?,
                               failure_reason = NULL,
                               last_attempt_at = ?
                         WHERE application_id = ?
                           AND outcome = 'FAILED'
                        """,
                        panLast4,
                        panHash,
                        bureauCardId,
                        manualAddress,
                        configVersion,
                        Timestamp.from(issuedAt),
                        Timestamp.from(issuedAt),
                        applicationId);

        if (changed == 0) {
            return false;
        }

        jdbc.update(
                """
                INSERT INTO card_status_history (
                    application_id,
                    status,
                    source,
                    observed_at,
                    manual_address
                ) VALUES (
                    ?,
                    'REQUESTED',
                    'ISSUE',
                    ?,
                    ?
                )
                """,
                applicationId,
                Timestamp.from(issuedAt),
                manualAddress);

        return true;
    }

    private List<String> stringList(
            String value) {

        try {
            JsonNode node =
                    json.readTree(value);

            /*
             * Supports both a real JSON array and the
             * double-encoded JSON value used by H2 tests.
             */
            if (node != null
                    && node.isTextual()) {

                node = json.readTree(
                        node.asText());
            }

            if (node == null
                    || !node.isArray()) {

                throw new IllegalStateException(
                        "IssuingConfig JSON value "
                                + "is not an array");
            }

            List<String> values =
                    new ArrayList<>();

            node.forEach(item ->
                    values.add(item.asText()));

            return values;

        } catch (Exception invalidJson) {
            throw new IllegalStateException(
                    "IssuingConfig contains invalid JSON",
                    invalidJson);
        }
    }
}