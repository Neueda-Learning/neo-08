package com.neobank.module.repository;

import com.neobank.module.model.CardRecord;
import com.neobank.module.model.CardOutcome;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CardRecordRepository extends JpaRepository<CardRecord, String> {

    List<CardRecord> findAllByOrderByCreatedAtDescApplicationIdDesc();

    /**
     * Serialises terminal transitions. Intake schedules only one worker, but the
     * lock also protects against an operator retry racing a late worker.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select card from CardRecord card where card.applicationId = :applicationId")
    Optional<CardRecord> findForUpdate(@Param("applicationId") String applicationId);

    /** Locks stale work so only one instance can release an abandoned ownership lease. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select card from CardRecord card
            where card.outcome = :outcome
              and card.processingStartedAt <= :cutoff
            """)
    List<CardRecord> findStaleForUpdate(
            @Param("outcome") CardOutcome outcome,
            @Param("cutoff") Instant cutoff);

    /** Locks terminal callbacks that have never succeeded or whose dispatcher disappeared. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select card from CardRecord card
            where card.decisionStatus is not null
              and card.callbackDeliveredAt is null
              and (card.callbackClaimedAt is null or card.callbackClaimedAt <= :staleBefore)
            order by card.decidedAt asc
            """)
    List<CardRecord> findPendingCallbacksForUpdate(@Param("staleBefore") Instant staleBefore);
}
