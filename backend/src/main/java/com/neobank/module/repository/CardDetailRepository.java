package com.neobank.module.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Read-only persistence for the UC-02 card-detail endpoint. */
@Repository
public class CardDetailRepository {

    private final JdbcTemplate jdbc;

    public CardDetailRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<StoredCardDetail> findByApplicationId(String applicationId) {
        List<StoredCardDetail> rows = jdbc.query(
                """
                SELECT outcome, reference, pan_last4, pan_hash, bureau_card_id,
                       bureau_status, dispatch_ref, account_id, product_code,
                       issuing_config_version, failure_reason
                  FROM card_record
                 WHERE application_id = ?
                """,
                (result, rowNumber) -> new StoredCardDetail(
                        result.getString("outcome"),
                        result.getString("reference"),
                        result.getString("pan_last4"),
                        result.getString("pan_hash"),
                        result.getString("bureau_card_id"),
                        result.getString("bureau_status"),
                        result.getString("dispatch_ref"),
                        result.getString("account_id"),
                        result.getString("product_code"),
                        result.getObject("issuing_config_version", Integer.class),
                        result.getString("failure_reason")),
                applicationId);
        return rows.stream().findFirst();
    }

    public record StoredCardDetail(
            String outcome,
            String reference,
            String panLast4,
            String panHash,
            String bureauCardId,
            String bureauStatus,
            String dispatchRef,
            String accountId,
            String productCode,
            Integer issuingConfigVersion,
            String failureReason) {
    }
}
