package com.neobank.module.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neobank.module.dto.IssuingConfigSnapshot;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Persistence owned by the UC-02 off-thread initial issue flow. */
@Repository
public class CardIssueRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public CardIssueRepository(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public Optional<IntakeCard> findCard(String applicationId) {
        List<IntakeCard> rows = jdbc.query(
                """
                SELECT application_id, outcome, reference, failure_reason
                  FROM card_record
                 WHERE application_id = ?
                """,
                (result, rowNumber) -> new IntakeCard(
                        result.getString("application_id"),
                        result.getString("outcome"),
                        result.getString("reference"),
                        result.getString("failure_reason")),
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

    public boolean markFailed(
            String applicationId,
            String reason,
            String accountId,
            String productCode,
            Instant attemptedAt) {
        return jdbc.update(
                """
                UPDATE card_record
                   SET outcome = 'FAILED',
                       account_id = ?,
                       product_code = ?,
                       pan_last4 = NULL,
                       pan_hash = NULL,
                       bureau_card_id = NULL,
                       bureau_status = NULL,
                       dispatch_ref = NULL,
                       issuing_config_version = NULL,
                       issued_at = NULL,
                       failure_reason = ?,
                       last_attempt_at = ?
                 WHERE application_id = ?
                   AND outcome = 'IN_PROGRESS'
                """,
                accountId,
                productCode,
                reason,
                Timestamp.from(attemptedAt),
                applicationId) == 1;
    }

    @Transactional
    public boolean markIssued(
            String applicationId,
            String panLast4,
            String panHash,
            String bureauCardId,
            String accountId,
            String productCode,
            int configVersion,
            Instant issuedAt) {
        int changed = jdbc.update(
                """
                UPDATE card_record
                   SET outcome = 'ISSUED',
                       pan_last4 = ?,
                       pan_hash = ?,
                       bureau_card_id = ?,
                       bureau_status = 'REQUESTED',
                       dispatch_ref = NULL,
                       account_id = ?,
                       product_code = ?,
                       manual_address = false,
                       issuing_config_version = ?,
                       issued_at = ?,
                       failure_reason = NULL,
                       last_attempt_at = ?
                 WHERE application_id = ?
                   AND outcome = 'IN_PROGRESS'
                """,
                panLast4,
                panHash,
                bureauCardId,
                accountId,
                productCode,
                configVersion,
                Timestamp.from(issuedAt),
                Timestamp.from(issuedAt),
                applicationId);
        if (changed == 0) {
            return false;
        }

        jdbc.update(
                """
                INSERT INTO card_status_history
                       (application_id, status, source, observed_at, manual_address)
                VALUES (?, 'REQUESTED', 'ISSUE', ?, false)
                """,
                applicationId,
                Timestamp.from(issuedAt));
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

    public record IntakeCard(
            String applicationId,
            String outcome,
            String reference,
            String failureReason) {
    }
}
