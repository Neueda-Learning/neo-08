package com.neobank.module.service;

import com.neobank.module.dto.CardDetailView;
import com.neobank.module.dto.CardOverrideRequest;
import com.neobank.module.dto.CardOverrideRequest.NewOutcome;
import com.neobank.module.integrations.orchestrator.OrchestratorClient;
import com.neobank.module.model.Decision;
import com.neobank.module.repository.OverrideCaseRepository;
import com.neobank.module.repository.OverrideCaseRepository.OverrideCard;
import java.time.Clock;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** UC-07 manual outcome correction; it never generates a PAN or calls the bureau. */
@Service
public class OverrideCaseService {

    private final OverrideCaseRepository overrides;
    private final CardDetailService details;
    private final OrchestratorClient orchestrator;
    private final TransactionTemplate transactions;
    private final Clock clock = Clock.systemUTC();

    public OverrideCaseService(
            OverrideCaseRepository overrides,
            CardDetailService details,
            OrchestratorClient orchestrator,
            TransactionTemplate transactions) {
        this.overrides = overrides;
        this.details = details;
        this.orchestrator = orchestrator;
        this.transactions = transactions;
    }

    public CardDetailView override(String applicationId, CardOverrideRequest request) {
        OverrideResult result = transactions.execute(
                status -> applyOverride(applicationId, request));
        if (result == null) {
            throw new IllegalStateException("override transaction returned no result");
        }

        if (result.changed()) {
            orchestrator.applicationStatusUpdate(
                    applicationId,
                    result.outcome() == NewOutcome.ISSUED
                            ? Decision.ACCEPTED
                            : Decision.REFERRED,
                    "local-manual: " + result.outcome().name()
                            + "; reason=" + request.reason()
                            + "; reference=" + result.reference());
        }
        return details.get(applicationId);
    }

    private OverrideResult applyOverride(
            String applicationId, CardOverrideRequest request) {
        OverrideCard card = overrides.findForUpdate(applicationId)
                .orElseThrow(() -> new OverrideCaseException(
                        HttpStatus.NOT_FOUND, "card case not found: " + applicationId));
        NewOutcome requested = request.newOutcome();

        if ("IN_PROGRESS".equals(card.outcome())) {
            throw conflict("an IN_PROGRESS card case cannot be overridden");
        }

        if (card.outcome().equals(requested.name())) {
            if (isIdenticalReplay(applicationId, requested, request)) {
                return new OverrideResult(false, requested, card.reference());
            }
            throw conflict("card case already has outcome " + requested.name());
        }

        if (requested == NewOutcome.ISSUED
                && (isBlank(card.panLast4()) || isBlank(card.panHash()))) {
            throw conflict(
                    "card case has no issued PAN data; retry it through the failed-issues queue");
        }

        if (!overrides.updateOutcome(
                applicationId, card.outcome(), requested.name())) {
            throw conflict("card case outcome changed concurrently");
        }
        overrides.insertOverride(
                applicationId,
                card.outcome(),
                requested.name(),
                request.reason(),
                request.operator(),
                clock.instant());
        return new OverrideResult(true, requested, card.reference());
    }

    private boolean isIdenticalReplay(
            String applicationId,
            NewOutcome requested,
            CardOverrideRequest request) {
        return overrides.hasMatchingOverride(
                applicationId,
                requested.name(),
                request.reason(),
                request.operator());
    }

    private static OverrideCaseException conflict(String message) {
        return new OverrideCaseException(HttpStatus.CONFLICT, message);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record OverrideResult(
            boolean changed, NewOutcome outcome, String reference) {
    }
}
