package com.neobank.module.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * UC-07 database operations.
 */
@Repository
public class OverrideCaseRepository {

    private final JdbcTemplate jdbc;

    public OverrideCaseRepository(
            JdbcTemplate jdbc) {

        this.jdbc = jdbc;
    }

    /**
     * Locks the card row for the duration of the
     * override transaction.
     */
    public Optional<OverrideCard> findForUpdate(
            String applicationId) {

        List<OverrideCard> rows =
                jdbc.query(
                        """
                        SELECT application_id,
                               outcome,
                               reference,
                               pan_last4,
                               pan_hash
                          FROM card_record
                         WHERE application_id = ?
                           FOR UPDATE
                        """,
                        (result, rowNumber) ->
                                new OverrideCard(
                                        result.getString(
                                                "application_id"
                                        ),
                                        result.getString(
                                                "outcome"
                                        ),
                                        result.getString(
                                                "reference"
                                        ),
                                        result.getString(
                                                "pan_last4"
                                        ),
                                        result.getString(
                                                "pan_hash"
                                        )
                                ),
                        applicationId
                );

        return rows.stream()
                .findFirst();
    }

    /**
     * Used to make identical requests idempotent.
     */
    public boolean hasMatchingOverride(
            String applicationId,
            String newOutcome,
            String reason,
            String operator) {

        Integer count =
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*)
                          FROM override_log
                         WHERE application_id = ?
                           AND new_outcome = ?
                           AND reason = ?
                           AND operator = ?
                        """,
                        Integer.class,
                        applicationId,
                        newOutcome,
                        reason,
                        operator
                );

        return count != null
                && count > 0;
    }

    /**
     * Optimistic condition prevents overwriting a
     * concurrently changed outcome.
     */
    public boolean updateOutcome(
            String applicationId,
            String expectedOutcome,
            String newOutcome) {

        int updated =
                jdbc.update(
                        """
                        UPDATE card_record
                           SET outcome = ?
                         WHERE application_id = ?
                           AND outcome = ?
                        """,
                        newOutcome,
                        applicationId,
                        expectedOutcome
                );

        return updated == 1;
    }

    /**
     * Writes an append-only UC07 audit record.
     */
    public void insertOverride(
            String applicationId,
            String oldOutcome,
            String newOutcome,
            String reason,
            String operator,
            Instant overriddenAt) {

        jdbc.update(
                """
                INSERT INTO override_log
                       (
                           application_id,
                           old_outcome,
                           new_outcome,
                           reason,
                           operator,
                           overridden_at
                       )
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                applicationId,
                oldOutcome,
                newOutcome,
                reason,
                operator,
                Timestamp.from(overriddenAt)
        );
    }

    public record OverrideCard(
            String applicationId,
            String outcome,
            String reference,
            String panLast4,
            String panHash) {
    }
}