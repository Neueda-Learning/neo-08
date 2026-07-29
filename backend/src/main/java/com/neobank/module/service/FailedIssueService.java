package com.neobank.module.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.neobank.module.dto.CardRetryRequest;
import com.neobank.module.dto.CorrectedAddress;
import com.neobank.module.dto.FailedCardRecord;
import com.neobank.module.dto.FailedIssueView;
import com.neobank.module.dto.IssuingConfigSnapshot;
import com.neobank.module.exception.BureauUnavailableException;
import com.neobank.module.integrations.cardsearch.CardApplicationClient;
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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * UC-04 failed queue and operator-triggered retry flow.
 */
@Service
public class FailedIssueService {

    private static final int QUEUE_LIMIT = 10;

    private final FailedIssueRepository failed;

    private final CardApplicationClient applications;

    private final CardPanGenerator pans;

    private final MockBureauService mockBureauService;

    private final OrchestratorClient orchestrator;

    private final Clock clock = Clock.systemUTC();

    public FailedIssueService(
            FailedIssueRepository failed,
            CardApplicationClient applications,
            CardPanGenerator pans,
            MockBureauService mockBureauService,
            OrchestratorClient orchestrator) {

        this.failed = failed;
        this.applications = applications;
        this.pans = pans;
        this.mockBureauService = mockBureauService;
        this.orchestrator = orchestrator;
    }

    /**
     * Returns the oldest recoverable failed cases,
     * capped at ten rows.
     */
    public List<FailedIssueView> queue() {
        return failed.findOldestFailed(
                QUEUE_LIMIT
        );
    }

    /**
     * Retries one failed card issue.
     */
    public void retry(
            String applicationId,
            CardRetryRequest request) {

        FailedCardRecord card =
                failed.findCard(applicationId)
                        .orElseThrow(() ->
                                new FailedIssueException(
                                        HttpStatus.NOT_FOUND,
                                        "card case not found: "
                                                + applicationId
                                )
                        );

        /*
         * Only FAILED cases can be retried.
         */
        if (card.outcome()
                != CardOutcome.FAILED) {

            throw new FailedIssueException(
                    HttpStatus.BAD_REQUEST,
                    "only FAILED card cases "
                            + "can be retried"
            );
        }

        if (card.failureReason() == null) {
            throw new FailedIssueException(
                    HttpStatus.BAD_REQUEST,
                    "failed card case has "
                            + "no retry reason"
            );
        }

        IssuingConfigSnapshot config =
                failed.findCurrentConfig()
                        .orElseThrow(() ->
                                new FailedIssueException(
                                        HttpStatus
                                                .SERVICE_UNAVAILABLE,
                                        "IssuingConfig "
                                                + "is not available"
                                )
                        );

        /*
         * An operator-provided address is allowed
         * only for an address-validation failure.
         */
        boolean manualAddress =
                card.failureReason()
                        == FailureReason
                        .CRD_DELIVERY_ADDRESS_INVALID;

        CorrectedAddress address =
                addressForRetry(
                        card,
                        request,
                        manualAddress
                );

        /*
         * Validate before generating a fresh PAN.
         */
        validateAddress(
                address,
                config
        );

        /*
         * Every retry generates a fresh PAN.
         *
         * The full PAN remains in memory only.
         * Only last4 and hash are persisted.
         */
        CardPanGenerator.GeneratedPan pan =
                pans.generate(
                        applicationId,
                        config,
                        card.previousPanLast4(),
                        card.previousPanHash()
                );

        Instant attemptedAt =
                clock.instant();

        MockBureauService.BureauResult accepted;

        try {
            /*
             * Direct internal call.
             *
             * This replaces the old HTTP call to
             * http://mock-bureau:8091/bureau/cards.
             */
            accepted =
                    mockBureauService.createCard(
                            applicationId
                    );

        } catch (
                BureauUnavailableException exception) {

            /*
             * Keep the case FAILED and update the
             * time of the most recent attempt.
             */
            failed.recordUnavailableAttempt(
                    applicationId,
                    attemptedAt
            );

            return;
        }

        /*
         * The Bureau accepted the retry.
         *
         * Change FAILED -> ISSUED, save the fresh
         * PAN metadata and create the initial
         * REQUESTED timeline entry.
         */
        boolean changed =
                failed.markIssued(
                        applicationId,
                        pan.last4(),
                        pan.hash(),
                        accepted.bureauCardId(),
                        config.version(),
                        manualAddress,
                        attemptedAt
                );

        if (!changed) {
            throw new FailedIssueException(
                    HttpStatus.BAD_REQUEST,
                    "only FAILED card cases "
                            + "can be retried"
            );
        }

        /*
         * Resume the parked journey.
         *
         * OrchestratorClient handles callback
         * failures internally and does not undo
         * the local ISSUED result.
         */
        orchestrator.applicationStatusUpdate(
                applicationId,
                Decision.ACCEPTED,
                "local-manual: ISSUED; reference="
                        + card.reference()
        );
    }

    /**
     * Determines which delivery address should be
     * used for this retry.
     */
    private CorrectedAddress addressForRetry(
            FailedCardRecord card,
            CardRetryRequest request,
            boolean manualAddress) {

        CorrectedAddress supplied =
                request == null
                        ? null
                        : request.correctedAddress();

        /*
         * Address-validation failures require a
         * corrected address from the operator.
         */
        if (manualAddress) {
            if (supplied == null) {
                throw validation(
                        Map.of(
                                "correctedAddress",
                                "is required for "
                                        + "CRD_DELIVERY_ADDRESS_INVALID"
                        )
                );
            }

            return supplied;
        }

        /*
         * A corrected address must not be supplied
         * for Bureau-unavailable failures.
         */
        if (supplied != null) {
            throw validation(
                    Map.of(
                            "correctedAddress",
                            "is allowed only for "
                                    + "CRD_DELIVERY_ADDRESS_INVALID"
                    )
            );
        }

        /*
         * For a Bureau-unavailable retry, obtain the
         * original address live from the orchestrator.
         */
        return effectiveAddress(
                applications.getApplication(
                        card.applicationId()
                )
        );
    }

    /**
     * Extracts the effective address from the
     * orchestrator application response.
     */
    private static CorrectedAddress effectiveAddress(
            JsonNode response) {

        JsonNode application =
                response != null
                        && response.has("application")
                        ? response.get("application")
                        : response;

        JsonNode delivery =
                application == null
                        ? null
                        : application.get("delivery");

        boolean current =
                delivery != null
                        && delivery.path(
                        "useCurrentAddress"
                ).asBoolean(false);

        JsonNode address =
                current
                        ? application
                        .path("applicant")
                        .path("currentAddress")
                        : delivery == null
                        ? null
                        : delivery.get("address");

        if (address == null
                || address.isMissingNode()
                || address.isNull()) {

            return new CorrectedAddress(
                    null,
                    null,
                    null,
                    null,
                    null
            );
        }

        return new CorrectedAddress(
                text(address, "line1"),
                text(address, "line2"),
                text(address, "city"),
                text(address, "postcode"),
                text(address, "country")
        );
    }

    /**
     * Validates an address using the current
     * IssuingConfig rules.
     */
    private static void validateAddress(
            CorrectedAddress address,
            IssuingConfigSnapshot config) {

        Map<String, String> values =
                new LinkedHashMap<>();

        values.put(
                "line1",
                address == null
                        ? null
                        : address.line1()
        );

        values.put(
                "line2",
                address == null
                        ? null
                        : address.line2()
        );

        values.put(
                "city",
                address == null
                        ? null
                        : address.city()
        );

        values.put(
                "postcode",
                address == null
                        ? null
                        : address.postcode()
        );

        values.put(
                "country",
                address == null
                        ? null
                        : address.country()
        );

        Map<String, String> errors =
                new LinkedHashMap<>();

        for (String required :
                config.requiredAddressFields()) {

            if (isBlank(values.get(required))) {
                errors.put(
                        "correctedAddress."
                                + required,
                        "is required"
                );
            }
        }

        String country =
                values.get("country");

        if (!isBlank(country)) {
            String normalized =
                    country.trim()
                            .toUpperCase(
                                    Locale.ROOT
                            );

            boolean deliverable =
                    config.deliveryCountries()
                            .stream()
                            .filter(value ->
                                    value != null
                            )
                            .map(value ->
                                    value.trim()
                                            .toUpperCase(
                                                    Locale.ROOT
                                            )
                            )
                            .anyMatch(
                                    normalized::equals
                            );

            if (!deliverable) {
                errors.put(
                        "correctedAddress.country",
                        "must be one of "
                                + config
                                .deliveryCountries()
                );
            }
        }

        if (!errors.isEmpty()) {
            throw validation(errors);
        }
    }

    private static FailedIssueException validation(
            Map<String, String> errors) {

        return new FailedIssueException(
                HttpStatus.BAD_REQUEST,
                "corrected address is invalid",
                errors
        );
    }

    private static String text(
            JsonNode node,
            String field) {

        JsonNode value =
                node.get(field);

        return value == null
                || value.isNull()
                ? null
                : value.asText();
    }

    private static boolean isBlank(
            String value) {

        return value == null
                || value.isBlank();
    }
}