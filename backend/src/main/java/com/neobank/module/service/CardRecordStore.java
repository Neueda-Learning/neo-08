package com.neobank.module.service;

import com.neobank.module.dto.CardRecordView;
import com.neobank.module.model.BureauStatus;
import com.neobank.module.model.CardOutcome;
import com.neobank.module.model.CardRecord;
import com.neobank.module.model.CardStatusHistory;
import com.neobank.module.model.Decision;
import com.neobank.module.model.IssuingConfig;
import com.neobank.module.model.StatusSource;
import com.neobank.module.repository.CardRecordRepository;
import com.neobank.module.repository.CardStatusHistoryRepository;
import com.neobank.module.repository.IssuingConfigRepository;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transaction boundary for the card workflow.
 *
 * <p>Keeping database-only work here prevents an HTTP callback or bureau call
 * from holding a connection, and makes {@code @Transactional} effective (calls
 * from {@link ApplicationService} cross a Spring bean boundary).</p>
 */
@Service
public class CardRecordStore {

    private final CardRecordRepository cardRecords;
    private final IssuingConfigRepository issuingConfigs;
    private final CardStatusHistoryRepository statusHistory;

    public CardRecordStore(
            CardRecordRepository cardRecords,
            IssuingConfigRepository issuingConfigs,
            CardStatusHistoryRepository statusHistory) {
        this.cardRecords = cardRecords;
        this.issuingConfigs = issuingConfigs;
        this.statusHistory = statusHistory;
    }

    /**
     * Creates the durable intake checkpoint. A concurrent insert can still win
     * between the read and write; the caller handles that unique-key exception
     * after this transaction has rolled back, then calls {@link #inspect}.
     */
    @Transactional
    public IntakeResult accept(String applicationId) {
        Optional<CardRecord> existing = cardRecords.findById(applicationId);
        if (existing.isPresent()) {
            return intakeResult(existing.get(), false);
        }

        CardRecord created = cardRecords.saveAndFlush(CardRecord.newInProgress(applicationId));
        return intakeResult(created, true);
    }

    @Transactional(readOnly = true)
    public IntakeResult inspect(String applicationId) {
        CardRecord row = cardRecords.findById(applicationId)
                .orElseThrow(() -> new IllegalStateException(
                        "concurrent intake failed without creating " + applicationId));
        return intakeResult(row, false);
    }

    @Transactional(readOnly = true)
    public IssuingConfig currentConfig() {
        return issuingConfigs.findTopByOrderByVersionDesc()
                .orElseThrow(() -> new IllegalStateException("no issuing configuration exists"));
    }

    /**
     * Claims work before it is submitted to the executor. Concurrent duplicates
     * observe the live lease and do not enqueue a second bureau instruction.
     */
    @Transactional
    public boolean tryClaim(
            String applicationId,
            String processingToken,
            Instant startedAt,
            Instant staleBefore) {
        CardRecord row = cardRecords.findForUpdate(applicationId)
                .orElseThrow(() -> new IllegalStateException(
                        "no card record exists for " + applicationId));
        if (!row.isInProgress()) {
            return false;
        }
        return row.claimProcessing(processingToken, startedAt, staleBefore);
    }

    /** Revalidates ownership and extends the lease immediately before work or an external call. */
    @Transactional
    public boolean renewClaim(
            String applicationId,
            String processingToken,
            Instant startedAt) {
        return cardRecords.findForUpdate(applicationId)
                .map(row -> row.renewProcessingClaim(processingToken, startedAt))
                .orElse(false);
    }

    /** Undo only the named claim; a replacement worker's token is never cleared. */
    @Transactional
    public void releaseClaim(String applicationId, String processingToken) {
        cardRecords.findForUpdate(applicationId)
                .ifPresent(row -> row.releaseProcessingClaim(processingToken));
    }

    /**
     * Commits the local outcome and first bureau observation atomically.
     * The callback is deliberately sent only after this method returns.
     */
    @Transactional
    public Optional<StoredDecision> markIssued(IssuedData issued) {
        CardRecord row = cardRecords.findForUpdate(issued.applicationId())
                .orElseThrow(() -> new IllegalStateException(
                        "no card record exists for " + issued.applicationId()));
        if (!row.isInProgress() || !row.isClaimedBy(issued.processingToken())) {
            return Optional.empty();
        }

        row.markIssued(
                issued.reasonCode(),
                issued.comment(),
                issued.reference(),
                issued.panLast4(),
                issued.panHash(),
                issued.bureauCardId(),
                issued.bureauStatus(),
                issued.productCode(),
                issued.issuingConfigVersion(),
                issued.decidedAt());
        statusHistory.save(new CardStatusHistory(
                issued.applicationId(),
                issued.bureauStatus(),
                StatusSource.ISSUE,
                issued.decidedAt(),
                null));
        String callbackToken = claimInitialCallback(row, issued.decidedAt());
        return Optional.of(new StoredDecision(
                Decision.ACCEPTED,
                issued.comment(),
                callbackToken));
    }

    @Transactional
    public Optional<StoredDecision> markFailed(FailedData failed) {
        CardRecord row = cardRecords.findForUpdate(failed.applicationId())
                .orElseThrow(() -> new IllegalStateException(
                        "no card record exists for " + failed.applicationId()));
        if (!row.isInProgress() || !row.isClaimedBy(failed.processingToken())) {
            return Optional.empty();
        }

        row.markFailed(
                failed.reasonCode(),
                failed.comment(),
                failed.productCode(),
                failed.issuingConfigVersion(),
                failed.decidedAt());
        String callbackToken = claimInitialCallback(row, failed.decidedAt());
        return Optional.of(new StoredDecision(
                Decision.REFERRED,
                failed.comment(),
                callbackToken));
    }

    @Transactional(readOnly = true)
    public List<CardRecordView> findAll() {
        return cardRecords.findAllByOrderByCreatedAtDescApplicationIdDesc().stream()
                .map(CardRecordView::of)
                .toList();
    }

    /**
     * Releases abandoned leases without inventing a business outcome. The
     * orchestrator can safely resend the original payload and claim the row.
     */
    @Transactional
    public List<String> releaseStale(Instant cutoff) {
        return cardRecords.findStaleForUpdate(CardOutcome.IN_PROGRESS, cutoff).stream()
                .filter(row -> row.releaseProcessingClaim(null))
                .map(CardRecord::getApplicationId)
                .toList();
    }

    /**
     * Claims due callback outbox rows. The database lock and per-attempt token
     * prevent two service instances from owning the same delivery at once.
     */
    @Transactional
    public List<CallbackDelivery> claimPendingCallbacks(
            Instant claimedAt,
            Instant staleBefore) {
        return cardRecords.findPendingCallbacksForUpdate(staleBefore).stream()
                .map(row -> {
                    String token = UUID.randomUUID().toString();
                    boolean claimed = row.claimCallback(token, claimedAt, staleBefore);
                    return claimed
                            ? new CallbackDelivery(
                                    row.getApplicationId(),
                                    row.getDecisionStatus(),
                                    row.getComment(),
                                    token)
                            : null;
                })
                .filter(Objects::nonNull)
                .toList();
    }

    /** Marks a callback delivered only if the HTTP attempt still owns its claim. */
    @Transactional
    public boolean markCallbackDelivered(
            String applicationId,
            String callbackToken,
            Instant deliveredAt) {
        return cardRecords.findForUpdate(applicationId)
                .map(row -> row.markCallbackDelivered(callbackToken, deliveredAt))
                .orElse(false);
    }

    /** Releases only the named callback claim after an executor hand-off failure. */
    @Transactional
    public void releaseCallbackClaim(String applicationId, String callbackToken) {
        cardRecords.findForUpdate(applicationId)
                .ifPresent(row -> row.releaseCallbackClaim(callbackToken));
    }

    private static String claimInitialCallback(CardRecord row, Instant decidedAt) {
        String token = UUID.randomUUID().toString();
        if (!row.claimCallback(token, decidedAt, decidedAt)) {
            throw new IllegalStateException(
                    "terminal card record could not create its callback outbox claim");
        }
        return token;
    }

    private static IntakeResult intakeResult(CardRecord row, boolean created) {
        if (!row.isDecided()) {
            return new IntakeResult(
                    created ? IntakeDisposition.CREATED : IntakeDisposition.IN_PROGRESS,
                    null,
                    null);
        }
        return new IntakeResult(
                IntakeDisposition.DECIDED,
                row.getDecisionStatus(),
                row.getComment());
    }

    public enum IntakeDisposition {
        CREATED,
        IN_PROGRESS,
        DECIDED
    }

    public record IntakeResult(
            IntakeDisposition disposition,
            Decision decision,
            String comment) {
    }

    public record StoredDecision(
            Decision decision,
            String comment,
            String callbackToken) {
    }

    public record CallbackDelivery(
            String applicationId,
            Decision decision,
            String comment,
            String callbackToken) {
    }

    public record IssuedData(
            String applicationId,
            String processingToken,
            String reasonCode,
            String comment,
            String reference,
            String panLast4,
            String panHash,
            String bureauCardId,
            BureauStatus bureauStatus,
            String productCode,
            int issuingConfigVersion,
            Instant decidedAt) {
    }

    public record FailedData(
            String applicationId,
            String processingToken,
            String reasonCode,
            String comment,
            String productCode,
            Integer issuingConfigVersion,
            Instant decidedAt) {
    }

}
