package com.neobank.module.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.neobank.module.dto.CardExecuteRequest;
import com.neobank.module.dto.IssuingConfigSnapshot;
import com.neobank.module.integrations.cardissue.CardIssueBureauClient;
import com.neobank.module.integrations.cardissue.CardIssueBureauClient.AcceptedCard;
import com.neobank.module.integrations.cardissue.CardIssueBureauClient.CardInstruction;
import com.neobank.module.integrations.cardissue.CardIssueBureauClient.DeliveryAddress;
import com.neobank.module.integrations.orchestrator.OrchestratorClient;
import com.neobank.module.model.Decision;
import com.neobank.module.model.FailureReason;
import com.neobank.module.repository.CardIssueRepository;
import com.neobank.module.repository.CardIssueRepository.IntakeCard;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Executes the UC-02 issue decision after UC-00 has durably inserted the intake row. */
@Service
public class CardIssueProcessor {

    private static final Logger log = LoggerFactory.getLogger(CardIssueProcessor.class);

    private final CardIssueRepository cards;
    private final CardPanGenerator pans;
    private final CardIssueBureauClient bureau;
    private final OrchestratorClient orchestrator;
    private final Clock clock = Clock.systemUTC();

    public CardIssueProcessor(
            CardIssueRepository cards,
            CardPanGenerator pans,
            CardIssueBureauClient bureau,
            OrchestratorClient orchestrator) {
        this.cards = cards;
        this.pans = pans;
        this.bureau = bureau;
        this.orchestrator = orchestrator;
    }

    public void process(CardExecuteRequest request) {
        IntakeCard card = cards.findCard(request.applicationId())
                .orElseThrow(() -> new IllegalStateException(
                        "durable card intake row is missing: " + request.applicationId()));
        if (!"IN_PROGRESS".equals(card.outcome())) {
            reportStoredOutcome(card);
            return;
        }

        IssuingConfigSnapshot config = cards.findCurrentConfig().orElse(null);
        if (config == null) {
            log.error("Cannot issue card {} because IssuingConfig is unavailable",
                    request.applicationId());
            return;
        }

        String accountId = text(request.outputs(), "accountId");
        String productCode = nestedText(request.application(), "product", "productCode");
        DeliveryAddress address = effectiveAddress(request.application());
        Instant attemptedAt = clock.instant();

        if (!isDeliverable(address, config)) {
            if (cards.markFailed(
                    card.applicationId(),
                    FailureReason.CRD_DELIVERY_ADDRESS_INVALID.name(),
                    accountId,
                    productCode,
                    attemptedAt)) {
                reportFailed(card, FailureReason.CRD_DELIVERY_ADDRESS_INVALID.name());
            }
            return;
        }

        CardPanGenerator.GeneratedPan pan =
                pans.generate(card.applicationId(), config, null, null);
        Optional<AcceptedCard> accepted = bureau.issue(
                config.bureauBaseUrl(),
                new CardInstruction(
                        card.applicationId(),
                        card.reference(),
                        pan.fullPan(),
                        nestedText(request.application(), "applicant", "fullName"),
                        address,
                        accountId,
                        productCode));

        if (accepted.isEmpty()) {
            if (cards.markFailed(
                    card.applicationId(),
                    FailureReason.CRD_BUREAU_UNAVAILABLE.name(),
                    accountId,
                    productCode,
                    attemptedAt)) {
                reportFailed(card, FailureReason.CRD_BUREAU_UNAVAILABLE.name());
            }
            return;
        }

        if (cards.markIssued(
                card.applicationId(),
                pan.last4(),
                pan.hash(),
                accepted.get().bureauCardId(),
                accountId,
                productCode,
                config.version(),
                clock.instant())) {
            orchestrator.applicationStatusUpdate(
                    card.applicationId(),
                    Decision.ACCEPTED,
                    "ISSUED; reference=" + card.reference());
        }
    }

    /** Replays a previously committed decision without regenerating or re-instructing a card. */
    public void replayStoredOutcome(String applicationId) {
        cards.findCard(applicationId).ifPresent(this::reportStoredOutcome);
    }

    private void reportStoredOutcome(IntakeCard card) {
        if ("ISSUED".equals(card.outcome())) {
            orchestrator.applicationStatusUpdate(
                    card.applicationId(),
                    Decision.ACCEPTED,
                    "ISSUED; reference=" + card.reference());
        } else if ("FAILED".equals(card.outcome())) {
            reportFailed(card, card.failureReason());
        }
    }

    private void reportFailed(IntakeCard card, String reason) {
        orchestrator.applicationStatusUpdate(
                card.applicationId(),
                Decision.REFERRED,
                "application-manual: FAILED; reason=" + reason
                        + "; reference=" + card.reference());
    }

    private static DeliveryAddress effectiveAddress(JsonNode application) {
        if (application == null || application.isNull()) {
            return emptyAddress();
        }
        JsonNode delivery = application.path("delivery");
        JsonNode address = delivery.path("useCurrentAddress").asBoolean(false)
                ? application.path("applicant").path("currentAddress")
                : delivery.path("address");
        if (address.isMissingNode() || address.isNull()) {
            return emptyAddress();
        }
        return new DeliveryAddress(
                text(address, "line1"),
                text(address, "line2"),
                text(address, "city"),
                text(address, "postcode"),
                text(address, "country"));
    }

    private static DeliveryAddress emptyAddress() {
        return new DeliveryAddress(null, null, null, null, null);
    }

    private static boolean isDeliverable(
            DeliveryAddress address, IssuingConfigSnapshot config) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("line1", address.line1());
        fields.put("line2", address.line2());
        fields.put("city", address.city());
        fields.put("postcode", address.postcode());
        fields.put("country", address.country());

        for (String required : config.requiredAddressFields()) {
            if (isBlank(fields.get(required))) {
                return false;
            }
        }
        if (isBlank(address.country())) {
            return false;
        }
        String country = address.country().toUpperCase(Locale.ROOT);
        List<String> allowed = config.deliveryCountries();
        return allowed.stream()
                .map(value -> value.toUpperCase(Locale.ROOT))
                .anyMatch(country::equals);
    }

    private static String nestedText(JsonNode node, String parent, String child) {
        return node == null ? null : text(node.path(parent), child);
    }

    private static String text(JsonNode node, String field) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        JsonNode value = node.get(field);
        return value == null || value.isNull() || !value.isValueNode()
                ? null
                : value.asText();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
