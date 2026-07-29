package com.neobank.module.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.neobank.module.dto.CardRetryRequest;
import com.neobank.module.dto.CorrectedAddress;
import com.neobank.module.dto.FailedCardRecord;
import com.neobank.module.dto.FailedIssueView;
import com.neobank.module.dto.IssuingConfigSnapshot;
import com.neobank.module.integrations.cardsearch.CardApplicationClient;
import com.neobank.module.integrations.failedissues.BureauAcceptedCard;
import com.neobank.module.integrations.failedissues.BureauCardInstruction;
import com.neobank.module.integrations.failedissues.CardBureauClient;
import com.neobank.module.integrations.orchestrator.OrchestratorClient;
import com.neobank.module.model.CardOutcome;
import com.neobank.module.model.Decision;
import com.neobank.module.model.FailureReason;
import com.neobank.module.repository.FailedIssueRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** UC-04 failed queue and operator-triggered retry flow. */
@Service
public class FailedIssueService {

    private static final int QUEUE_LIMIT = 10;

    private final FailedIssueRepository failed;
    private final CardApplicationClient applications;
    private final CardPanGenerator pans;
    private final CardBureauClient bureau;
    private final OrchestratorClient orchestrator;
    private final Clock clock = Clock.systemUTC();

    public FailedIssueService(
            FailedIssueRepository failed,
            CardApplicationClient applications,
            CardPanGenerator pans,
            CardBureauClient bureau,
            OrchestratorClient orchestrator) {
        this.failed = failed;
        this.applications = applications;
        this.pans = pans;
        this.bureau = bureau;
        this.orchestrator = orchestrator;
    }

    public List<FailedIssueView> queue() {
        return failed.findOldestFailed(QUEUE_LIMIT);
    }

    public void retry(String applicationId, CardRetryRequest request) {
        FailedCardRecord card = failed.findCard(applicationId)
                .orElseThrow(() -> new FailedIssueException(
                        HttpStatus.NOT_FOUND, "card case not found: " + applicationId));
        if (card.outcome() != CardOutcome.FAILED) {
            throw new FailedIssueException(
                    HttpStatus.BAD_REQUEST, "only FAILED card cases can be retried");
        }
        if (card.failureReason() == null) {
            throw new FailedIssueException(
                    HttpStatus.BAD_REQUEST, "failed card case has no retry reason");
        }

        IssuingConfigSnapshot config = failed.findCurrentConfig()
                .orElseThrow(() -> new FailedIssueException(
                        HttpStatus.SERVICE_UNAVAILABLE, "IssuingConfig is not available"));

        boolean manualAddress =
                card.failureReason() == FailureReason.CRD_DELIVERY_ADDRESS_INVALID;
        CorrectedAddress address = addressForRetry(card, request, manualAddress);
        validateAddress(address, config);

        CardPanGenerator.GeneratedPan pan =
                pans.generate(
                        applicationId,
                        config,
                        card.previousPanLast4(),
                        card.previousPanHash());
        Instant attemptedAt = clock.instant();
        Optional<BureauAcceptedCard> accepted = bureau.issue(
                config.bureauBaseUrl(),
                new BureauCardInstruction(
                        applicationId,
                        card.reference(),
                        pan.fullPan(),
                        address,
                        card.accountId(),
                        card.productCode()));

        if (accepted.isEmpty()) {
            failed.recordUnavailableAttempt(applicationId, attemptedAt);
            return;
        }

        boolean changed = failed.markIssued(
                applicationId,
                pan.last4(),
                pan.hash(),
                accepted.get().bureauCardId(),
                config.version(),
                manualAddress,
                attemptedAt);
        if (!changed) {
            throw new FailedIssueException(
                    HttpStatus.BAD_REQUEST, "only FAILED card cases can be retried");
        }

        // The repository-wide callback contract permits ACCEPTED/REJECTED/REFERRED only.
        // "local-manual" remains visible in the comment without breaking that fixed wire shape.
        orchestrator.applicationStatusUpdate(
                applicationId,
                Decision.ACCEPTED,
                "local-manual: ISSUED; reference=" + card.reference());
    }

    private CorrectedAddress addressForRetry(
            FailedCardRecord card, CardRetryRequest request, boolean manualAddress) {
        CorrectedAddress supplied = request == null ? null : request.correctedAddress();
        if (manualAddress) {
            if (supplied == null) {
                throw validation(Map.of(
                        "correctedAddress", "is required for CRD_DELIVERY_ADDRESS_INVALID"));
            }
            return supplied;
        }
        if (supplied != null) {
            throw validation(Map.of(
                    "correctedAddress", "is allowed only for CRD_DELIVERY_ADDRESS_INVALID"));
        }
        return effectiveAddress(applications.getApplication(card.applicationId()));
    }

    private static CorrectedAddress effectiveAddress(JsonNode response) {
        JsonNode application = response != null && response.has("application")
                ? response.get("application")
                : response;
        JsonNode delivery = application == null ? null : application.get("delivery");
        boolean current = delivery != null
                && delivery.path("useCurrentAddress").asBoolean(false);
        JsonNode address = current
                ? application.path("applicant").path("currentAddress")
                : delivery == null ? null : delivery.get("address");
        if (address == null || address.isMissingNode() || address.isNull()) {
            return new CorrectedAddress(null, null, null, null, null);
        }
        return new CorrectedAddress(
                text(address, "line1"),
                text(address, "line2"),
                text(address, "city"),
                text(address, "postcode"),
                text(address, "country"));
    }

    private static void validateAddress(
            CorrectedAddress address, IssuingConfigSnapshot config) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("line1", address == null ? null : address.line1());
        values.put("line2", address == null ? null : address.line2());
        values.put("city", address == null ? null : address.city());
        values.put("postcode", address == null ? null : address.postcode());
        values.put("country", address == null ? null : address.country());

        Map<String, String> errors = new LinkedHashMap<>();
        for (String required : config.requiredAddressFields()) {
            if (isBlank(values.get(required))) {
                errors.put("correctedAddress." + required, "is required");
            }
        }

        String country = values.get("country");
        if (!isBlank(country)) {
            String normalized = country.toUpperCase(Locale.ROOT);
            boolean deliverable = config.deliveryCountries().stream()
                    .map(value -> value.toUpperCase(Locale.ROOT))
                    .anyMatch(normalized::equals);
            if (!deliverable) {
                errors.put(
                        "correctedAddress.country",
                        "must be one of " + config.deliveryCountries());
            }
        }
        if (!errors.isEmpty()) {
            throw validation(errors);
        }
    }

    private static FailedIssueException validation(Map<String, String> errors) {
        return new FailedIssueException(
                HttpStatus.BAD_REQUEST, "corrected address is invalid", errors);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
