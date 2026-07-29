package com.neobank.module.repository;

import com.neobank.module.dto.CardTimelineItem;
import com.neobank.module.model.BureauStatus;
import java.sql.Timestamp;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class CardTimelineRepository {

    private final JdbcTemplate jdbc;

    public CardTimelineRepository(
            JdbcTemplate jdbc) {

        this.jdbc = jdbc;
    }

    /**
     * Returns ISSUED cards whose Bureau lifecycle
     * has not yet reached DISPATCHED.
     */
    public List<PollCandidate> findCardsToPoll() {

        return jdbc.query(
                """
                SELECT application_id,
                       bureau_card_id,
                       bureau_status
                  FROM card_record
                 WHERE outcome = 'ISSUED'
                   AND bureau_card_id IS NOT NULL
                   AND (
                        bureau_status IS NULL
                        OR bureau_status <> 'DISPATCHED'
                   )
                 ORDER BY issued_at ASC,
                          application_id ASC
                """,
                (result, rowNumber) -> {

                    String status =
                            result.getString(
                                    "bureau_status"
                            );

                    return new PollCandidate(
                            result.getString(
                                    "application_id"
                            ),
                            result.getString(
                                    "bureau_card_id"
                            ),
                            status == null
                                    ? null
                                    : BureauStatus.valueOf(
                                    status
                            )
                    );
                }
        );
    }

    /**
     * Records the first lifecycle entry created
     * when the Bureau accepts the issue instruction.
     *
     * The NOT EXISTS guard makes the operation
     * idempotent.
     */
    @Transactional
    public void recordInitialRequested(
            String applicationId) {

        jdbc.update(
                """
                INSERT INTO card_status_history
                       (
                           application_id,
                           status,
                           source,
                           observed_at,
                           manual_address
                       )
                SELECT ?,
                       'REQUESTED',
                       'ISSUE',
                       CURRENT_TIMESTAMP,
                       false
                 WHERE NOT EXISTS (
                       SELECT 1
                         FROM card_status_history
                        WHERE application_id = ?
                 )
                """,
                applicationId,
                applicationId
        );
    }

    /**
     * Records a lifecycle status observed by
     * the scheduled poller.
     *
     * Duplicate and backwards transitions are ignored.
     */
    @Transactional
    public void recordObservedStatus(
            String applicationId,
            BureauStatus observedStatus,
            String dispatchRef) {

        List<BureauStatus> statuses =
                jdbc.query(
                        """
                        SELECT bureau_status
                          FROM card_record
                         WHERE application_id = ?
                           AND outcome = 'ISSUED'
                         LIMIT 1
                        """,
                        (result, rowNumber) -> {

                            String value =
                                    result.getString(
                                            "bureau_status"
                                    );

                            return value == null
                                    ? null
                                    : BureauStatus.valueOf(
                                    value
                            );
                        },
                        applicationId
                );

        if (statuses.isEmpty()) {
            return;
        }

        BureauStatus currentStatus =
                statuses.getFirst();

        /*
         * REQUESTED, PERSONALISED and DISPATCHED are
         * declared in lifecycle order in BureauStatus.
         *
         * This ignores:
         * - duplicate observations;
         * - backwards transitions caused by changing
         *   secondsPerStage during a demo.
         *
         * A forward jump from REQUESTED directly to
         * DISPATCHED is allowed.
         */
        if (currentStatus != null
                && observedStatus.ordinal()
                <= currentStatus.ordinal()) {

            return;
        }

        int changed =
                jdbc.update(
                        """
                        UPDATE card_record
                           SET bureau_status = ?,
                               dispatch_ref = ?
                         WHERE application_id = ?
                           AND outcome = 'ISSUED'
                        """,
                        observedStatus.name(),
                        dispatchRef,
                        applicationId
                );

        if (changed == 0) {
            return;
        }

        jdbc.update(
                """
                INSERT INTO card_status_history
                       (
                           application_id,
                           status,
                           source,
                           observed_at,
                           manual_address
                       )
                VALUES (?, ?, 'POLL',
                        CURRENT_TIMESTAMP, false)
                """,
                applicationId,
                observedStatus.name()
        );
    }

    /**
     * Returns the observed lifecycle in chronological
     * order.
     *
     * dispatchRef is stored on card_record, so it is
     * joined onto the final DISPATCHED history entry.
     */
    public List<CardTimelineItem> findTimeline(
            String applicationId) {

        return jdbc.query(
                """
                SELECT history.status,
                       history.observed_at,
                       history.source,
                       CASE
                           WHEN history.status =
                                'DISPATCHED'
                           THEN record.dispatch_ref
                           ELSE NULL
                       END AS dispatch_ref
                  FROM card_status_history history
                  JOIN card_record record
                    ON record.application_id =
                       history.application_id
                 WHERE history.application_id = ?
                 ORDER BY history.observed_at ASC
                """,
                (result, rowNumber) -> {

                    Timestamp observedAt =
                            result.getTimestamp(
                                    "observed_at"
                            );

                    return new CardTimelineItem(
                            BureauStatus.valueOf(
                                    result.getString(
                                            "status"
                                    )
                            ),
                            observedAt.toInstant(),
                            result.getString(
                                    "source"
                            ),
                            result.getString(
                                    "dispatch_ref"
                            )
                    );
                },
                applicationId
        );
    }

    public record PollCandidate(
            String applicationId,
            String bureauCardId,
            BureauStatus currentStatus
    ) {
    }
}