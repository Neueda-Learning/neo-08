package com.neobank.module.repository;

import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Idempotently inserts the durable UC-00 intake row. */
@Component
public class CardRecordWriter {

    private final CardRecordRepository records;
    private final JdbcTemplate jdbc;

    public CardRecordWriter(CardRecordRepository records, JdbcTemplate jdbc) {
        this.records = records;
        this.jdbc = jdbc;
    }

    public boolean insertIfAbsent(String applicationId) {
        try {
            jdbc.update(
                    """
                    INSERT INTO card_record
                           (application_id, outcome, reference, manual_address)
                    VALUES (?, 'IN_PROGRESS', ?, false)
                    """,
                    applicationId,
                    newReference());
            return true;
        } catch (DataIntegrityViolationException duplicate) {
            if (!records.existsById(applicationId)) {
                throw duplicate;
            }
            return false;
        }
    }

    private static String newReference() {
        return "crd-" + UUID.randomUUID().toString().replace("-", "");
    }
}
