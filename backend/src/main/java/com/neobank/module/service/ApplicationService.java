package com.neobank.module.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neobank.module.dto.CardRecordView;
import com.neobank.module.integrations.cardbureau.CardBureauClient;
import com.neobank.module.integrations.cardbureau.CardBureauClient.DeliveryAddress;
import com.neobank.module.integrations.cardbureau.CardBureauClient.IssueCard;
import com.neobank.module.integrations.cardbureau.CardBureauClient.IssuedCard;
import com.neobank.module.integrations.orchestrator.Application;
import com.neobank.module.integrations.orchestrator.ApplicationRequest;
import com.neobank.module.integrations.orchestrator.OrchestratorClient;
import com.neobank.module.model.IssuingConfig;
import com.neobank.module.service.CardRecordStore.CallbackDelivery;
import com.neobank.module.service.CardRecordStore.FailedData;
import com.neobank.module.service.CardRecordStore.IntakeDisposition;
import com.neobank.module.service.CardRecordStore.IntakeResult;
import com.neobank.module.service.CardRecordStore.IssuedData;
import com.neobank.module.service.CardRecordStore.StoredDecision;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Card-issuing workflow behind the fixed orchestrator contract.
 *
 * <p>The request thread creates one durable {@code IN_PROGRESS} row and schedules
 * the rest. The worker validates delivery policy before creating a PAN, sends the
 * full test PAN and address only to the bureau boundary, stores only last-four and
 * a salted digest, then commits before reporting the callback.</p>
 */
@Service
public class ApplicationService {

    static final String ISSUED_CODE = "CRD_ISSUED";
    static final String INVALID_APPLICATION_CODE = "CRD_APPLICATION_INCOMPLETE";
    static final String UNKNOWN_PRODUCT_CODE = "CRD_PRODUCT_UNKNOWN";
    static final String INVALID_ADDRESS_CODE = "CRD_DELIVERY_ADDRESS_INVALID";
    static final String CONFIGURATION_CODE = "CRD_CONFIGURATION_UNAVAILABLE";
    static final String BUREAU_CODE = "CRD_BUREAU_UNAVAILABLE";

    private static final Logger log = LoggerFactory.getLogger(ApplicationService.class);
    private static final Set<String> ADDRESS_FIELDS =
            Set.of("line1", "line2", "city", "postcode", "country");

    private final Executor executor;
    private final CardRecordStore records;
    private final OrchestratorClient orchestrator;
    private final CardBureauClient bureau;
    private final PanGenerator pans;
    private final CallbackDeliveryContext callbackContext;
    private final ObjectMapper json;
    private final String panHashSalt;
    private final Duration processingLease;
    private final Duration callbackLease;

    public ApplicationService(
            @Qualifier("applicationTaskExecutor") Executor executor,
            CardRecordStore records,
            OrchestratorClient orchestrator,
            CardBureauClient bureau,
            PanGenerator pans,
            CallbackDeliveryContext callbackContext,
            ObjectMapper json,
            @Value("${card.pan-hash-salt}") String panHashSalt,
            @Value("${card.processing-lease:2m}") Duration processingLease,
            @Value("${card.callback-lease:30s}") Duration callbackLease) {
        this.executor = executor;
        this.records = records;
        this.orchestrator = orchestrator;
        this.bureau = bureau;
        this.pans = pans;
        this.callbackContext = callbackContext;
        this.json = json;
        if (panHashSalt == null || panHashSalt.isBlank()) {
            throw new IllegalArgumentException("card.pan-hash-salt must not be blank");
        }
        if (processingLease == null || processingLease.isZero() || processingLease.isNegative()) {
            throw new IllegalArgumentException("card.processing-lease must be positive");
        }
        if (callbackLease == null || callbackLease.isZero() || callbackLease.isNegative()) {
            throw new IllegalArgumentException("card.callback-lease must be positive");
        }
        this.panHashSalt = panHashSalt;
        this.processingLease = processingLease;
        this.callbackLease = callbackLease;
    }

    /**
     * Creates the intake checkpoint and returns without doing rules or provider
     * work on the request thread.
     */
    public void processApplicationAsync(ApplicationRequest request) {
        String applicationId = request.applicationId();
        log.info(
                "RECEIVED card issue {} corr={} command={}",
                applicationId,
                shortCorrelation(request.correlationId()),
                request.command());

        IntakeResult intake;
        try {
            intake = records.accept(applicationId);
        } catch (DataIntegrityViolationException concurrentDuplicate) {
            // accept() has already left its rolled-back transaction. Inspecting
            // now is safe and sees the row committed by the winning request.
            intake = records.inspect(applicationId);
        }

        if (intake.disposition() == IntakeDisposition.DECIDED) {
            submitCallbackReplay(applicationId, intake);
            return;
        }

        Instant startedAt = Instant.now();
        String processingToken = UUID.randomUUID().toString();
        if (!records.tryClaim(
                applicationId,
                processingToken,
                startedAt,
                startedAt.minus(processingLease))) {
            log.info(
                    "DUPLICATE {} is already being processed; no second card will be issued",
                    applicationId);
            return;
        }
        submitNewRequest(request, processingToken);
    }

    private void submitNewRequest(ApplicationRequest request, String processingToken) {
        try {
            executor.execute(() -> processApplication(request, processingToken));
        } catch (RuntimeException schedulingFailure) {
            // The caller must not receive 202 when no worker accepted the hand-off.
            // Release the lease so the orchestrator can retry immediately.
            releaseClaimQuietly(request.applicationId(), processingToken);
            log.error(
                    "Card worker could not be scheduled for {} (type={})",
                    request.applicationId(),
                    schedulingFailure.getClass().getSimpleName());
            throw new ApplicationWorkerUnavailableException(schedulingFailure);
        }
    }

    private void submitCallbackReplay(String applicationId, IntakeResult intake) {
        try {
            executor.execute(() -> orchestrator.applicationStatusUpdate(
                    applicationId,
                    intake.decision(),
                    intake.comment()));
        } catch (RuntimeException schedulingFailure) {
            log.error(
                    "Stored callback replay could not be scheduled for {} (type={})",
                    applicationId,
                    schedulingFailure.getClass().getSimpleName());
            throw new ApplicationWorkerUnavailableException(schedulingFailure);
        }
    }

    /**
     * Worker entry point, package-private so the rules stay fast to unit test.
     * Database transactions and outbound calls remain deliberately separate.
     */
    void processApplication(ApplicationRequest request, String processingToken) {
        String applicationId = request.applicationId();
        if (!renewClaim(applicationId, processingToken, "worker start")) {
            return;
        }

        IssuingPolicy policy;
        try {
            policy = issuingPolicy(records.currentConfig());
        } catch (RuntimeException invalidConfiguration) {
            log.error(
                    "Issuing configuration unavailable for {} (type={})",
                    applicationId,
                    invalidConfiguration.getClass().getSimpleName());
            storeFailure(
                    applicationId,
                    CONFIGURATION_CODE,
                    "Card issuing policy is unavailable; the case was referred for manual review.",
                    productCode(request.application()),
                    null,
                    processingToken);
            return;
        }

        Validation validation = validate(request.application(), policy);
        if (validation.failure() != null) {
            RuleFailure failure = validation.failure();
            storeFailure(
                    applicationId,
                    failure.code(),
                    failure.comment(),
                    failure.productCode(),
                    policy.version(),
                    processingToken);
            return;
        }

        // A queued worker may have lost its lease before reaching the external
        // boundary. Revalidate ownership so a superseded task cannot issue a card.
        if (!renewClaim(applicationId, processingToken, "bureau instruction")) {
            return;
        }

        SafeIssuedCard issued;
        try {
            issued = issueCard(applicationId, validation.input(), policy);
        } catch (RuntimeException providerFailure) {
            // Provider exceptions are intentionally not logged with message/stack:
            // real HTTP clients sometimes echo a request, which would expose the PAN.
            log.error(
                    "Card bureau failed for {} (type={})",
                    applicationId,
                    providerFailure.getClass().getSimpleName());
            storeFailure(
                    applicationId,
                    BUREAU_CODE,
                    "The card personalisation bureau is unavailable; the case was referred for retry.",
                    validation.input().productCode(),
                    policy.version(),
                    processingToken);
            return;
        }

        try {
            records.markIssued(new IssuedData(
                            applicationId,
                            processingToken,
                            ISSUED_CODE,
                            issued.comment(),
                            issued.reference(),
                            issued.panLast4(),
                            issued.panHash(),
                            issued.bureauCardId(),
                            issued.bureauStatus(),
                            validation.input().productCode(),
                            policy.version(),
                            Instant.now()))
                    .ifPresent(report -> report(applicationId, ISSUED_CODE, report));
        } catch (RuntimeException persistenceFailure) {
            // The bureau operation is idempotent and already succeeded. Never
            // turn a database hiccup into a false FAILED/REFERRED decision;
            // release this ownership so the same instruction can be reconciled.
            log.error(
                    "Issued card result could not be stored for {}; it remains retryable (type={})",
                    applicationId,
                    persistenceFailure.getClass().getSimpleName());
            releaseClaimQuietly(applicationId, processingToken);
        }
    }

    /**
     * The full PAN is scoped to this method. It is validated, sent to the
     * bureau, reduced to safe derivatives, and never returned.
     */
    private SafeIssuedCard issueCard(
            String applicationId,
            IssueInput input,
            IssuingPolicy policy) {
        String fullPan = pans.generateStable(
                policy.panPrefix(),
                policy.panLength(),
                applicationId,
                panHashSalt);
        IssuedCard bureauCard = bureau.issue(new IssueCard(
                applicationId,
                input.cardholderName(),
                fullPan,
                input.productCode(),
                input.deliveryAddress()));

        String lastFour = PanGenerator.lastFour(fullPan);
        // Use one environment-specific secret salt so the unique index can also
        // detect the vanishingly unlikely event that two applications receive
        // the same generated test PAN.
        String panHash = PanGenerator.saltedSha256(fullPan, panHashSalt);
        String reference = stableReference(applicationId);
        String comment = "Card issued as %s, ending %s; personalisation status is %s."
                .formatted(reference, lastFour, bureauCard.status());
        return new SafeIssuedCard(
                reference,
                lastFour,
                panHash,
                bureauCard.bureauCardId(),
                bureauCard.status(),
                comment);
    }

    private void storeFailure(
            String applicationId,
            String code,
            String comment,
            String productCode,
            Integer configVersion,
            String processingToken) {
        try {
            records.markFailed(new FailedData(
                            applicationId,
                            processingToken,
                            code,
                            comment,
                            productCode,
                            configVersion,
                            Instant.now()))
                    .ifPresent(report -> report(applicationId, code, report));
        } catch (RuntimeException persistenceFailure) {
            log.error(
                    "Failure outcome could not be stored for {} (type={})",
                    applicationId,
                    persistenceFailure.getClass().getSimpleName());
        }
    }

    private boolean renewClaim(
            String applicationId,
            String processingToken,
            String boundary) {
        try {
            boolean renewed = records.renewClaim(
                    applicationId,
                    processingToken,
                    Instant.now());
            if (!renewed) {
                log.info(
                        "Worker for {} no longer owns the lease at {}; stopping",
                        applicationId,
                        boundary);
            }
            return renewed;
        } catch (RuntimeException persistenceFailure) {
            log.error(
                    "Worker lease for {} could not be renewed at {} (type={})",
                    applicationId,
                    boundary,
                    persistenceFailure.getClass().getSimpleName());
            return false;
        }
    }

    private void releaseClaimQuietly(String applicationId, String processingToken) {
        try {
            records.releaseClaim(applicationId, processingToken);
        } catch (RuntimeException persistenceFailure) {
            log.error(
                    "Worker lease for {} could not be released (type={})",
                    applicationId,
                    persistenceFailure.getClass().getSimpleName());
        }
    }

    private void report(String applicationId, String reasonCode, StoredDecision report) {
        callbackContext.runWithToken(
                report.callbackToken(),
                () -> orchestrator.applicationStatusUpdate(
                        applicationId,
                        report.decision(),
                        report.comment()));
        log.info("DECIDED {} -> {} ({})", applicationId, report.decision(), reasonCode);
    }

    /** Everything received, newest first, for this module's read-only board. */
    public List<CardRecordView> findAll() {
        return records.findAll();
    }

    /**
     * Releases leases abandoned by a stopped JVM. It deliberately does not
     * invent an outcome without the original applicant payload; an orchestrator
     * resend can immediately reclaim and safely repeat the idempotent operation.
     */
    @Scheduled(
            fixedDelayString = "${card.recovery-interval-ms:30000}",
            initialDelayString = "${card.recovery-interval-ms:30000}")
    void recoverStaleApplications() {
        Instant now = Instant.now();
        records.releaseStale(now.minus(processingLease))
                .forEach(applicationId ->
                        log.warn("RELEASED stale worker lease for {}; awaiting retry", applicationId));
    }

    /**
     * Retries terminal callbacks from a durable outbox. The callback itself is
     * an idempotent PUT; a token fences multiple service instances and the HTTP
     * interceptor records the first successful response.
     */
    @Scheduled(
            fixedDelayString = "${card.callback-retry-interval-ms:10000}",
            initialDelayString = "${card.callback-retry-interval-ms:10000}")
    void retryPendingCallbacks() {
        Instant now = Instant.now();
        records.claimPendingCallbacks(now, now.minus(callbackLease))
                .forEach(this::submitCallbackRetry);
    }

    private void submitCallbackRetry(CallbackDelivery delivery) {
        try {
            executor.execute(() -> report(
                    delivery.applicationId(),
                    "CALLBACK_RETRY",
                    new StoredDecision(
                            delivery.decision(),
                            delivery.comment(),
                            delivery.callbackToken())));
        } catch (RuntimeException schedulingFailure) {
            try {
                records.releaseCallbackClaim(
                        delivery.applicationId(),
                        delivery.callbackToken());
            } catch (RuntimeException persistenceFailure) {
                log.error(
                        "Callback claim for {} could not be released (type={})",
                        delivery.applicationId(),
                        persistenceFailure.getClass().getSimpleName());
            }
            log.error(
                    "Callback retry could not be scheduled for {} (type={})",
                    delivery.applicationId(),
                    schedulingFailure.getClass().getSimpleName());
        }
    }

    private Validation validate(Application application, IssuingPolicy policy) {
        if (application == null) {
            return Validation.failed(new RuleFailure(
                    INVALID_APPLICATION_CODE,
                    "The application payload is missing; card issuing requires applicant, product and delivery details.",
                    null));
        }

        Application.Applicant applicant = application.applicant();
        String productCode = productCode(application);
        if (applicant == null || !hasText(applicant.fullName())) {
            return Validation.failed(new RuleFailure(
                    INVALID_APPLICATION_CODE,
                    "The applicant's full name is required for card personalisation.",
                    productCode));
        }
        if (!hasText(productCode)) {
            return Validation.failed(new RuleFailure(
                    INVALID_APPLICATION_CODE,
                    "A product code is required before a card can be issued.",
                    null));
        }
        if (!policy.allowedProductCodes().contains(productCode)) {
            return Validation.failed(new RuleFailure(
                    UNKNOWN_PRODUCT_CODE,
                    "Product code "
                            + productCode
                            + " does not exist in the card issuing catalogue. No card number was generated.",
                    productCode));
        }

        Application.Delivery delivery = application.delivery();
        if (delivery == null || delivery.useCurrentAddress() == null) {
            return invalidAddress(
                    "Choose whether the card should use the applicant's current address.",
                    productCode);
        }

        Application.Address address = Boolean.TRUE.equals(delivery.useCurrentAddress())
                ? applicant.currentAddress()
                : delivery.address();
        if (address == null) {
            return invalidAddress(
                    "The selected delivery address is missing.",
                    productCode);
        }

        List<String> missing = policy.requiredAddressFields().stream()
                .filter(field -> !hasText(addressField(address, field)))
                .sorted()
                .toList();
        if (!missing.isEmpty()) {
            return invalidAddress(
                    "The delivery address is incomplete; provide " + String.join(", ", missing) + ".",
                    productCode);
        }

        String country = address.country();
        if (country == null
                || !country.matches("[A-Z]{2}")
                || !policy.deliveryCountries().contains(country)) {
            return invalidAddress(
                    "Cards can be delivered only to "
                            + String.join(", ", policy.deliveryCountries())
                            + "; received "
                            + display(country)
                            + ".",
                    productCode);
        }

        DeliveryAddress safeAddress = new DeliveryAddress(
                address.line1(),
                address.line2(),
                address.city(),
                address.postcode(),
                address.country());
        return Validation.valid(new IssueInput(
                applicant.fullName().trim(),
                productCode.trim(),
                safeAddress));
    }

    private static Validation invalidAddress(String detail, String productCode) {
        return Validation.failed(new RuleFailure(
                INVALID_ADDRESS_CODE,
                detail + " No card number was generated.",
                productCode));
    }

    private IssuingPolicy issuingPolicy(IssuingConfig config) {
        if (config.getVersion() == null
                || config.getPanPrefix() == null
                || !config.getPanPrefix().matches("9999[0-9]{2}")
                || config.getPanLength() != 16) {
            throw new IllegalStateException("issuing PAN range is invalid");
        }

        Set<String> countries = parseStringSet(config.getDeliveryCountries(), "deliveryCountries");
        if (countries.isEmpty()
                || countries.stream().anyMatch(code -> !code.matches("[A-Z]{2}"))) {
            throw new IllegalStateException("delivery countries are invalid");
        }

        Set<String> productCodes =
                parseStringSet(config.getAllowedProductCodes(), "allowedProductCodes");
        if (productCodes.isEmpty()
                || productCodes.stream().anyMatch(code -> !code.matches("[A-Z][A-Z0-9_]{2,63}"))) {
            throw new IllegalStateException("allowed product codes are invalid");
        }

        Set<String> requiredFields =
                parseStringSet(config.getRequiredAddressFields(), "requiredAddressFields");
        if (requiredFields.isEmpty() || !ADDRESS_FIELDS.containsAll(requiredFields)) {
            throw new IllegalStateException("required address fields are invalid");
        }

        return new IssuingPolicy(
                config.getVersion(),
                config.getPanPrefix(),
                config.getPanLength(),
                Collections.unmodifiableSet(new LinkedHashSet<>(countries)),
                Collections.unmodifiableSet(new LinkedHashSet<>(productCodes)),
                Collections.unmodifiableSet(new LinkedHashSet<>(requiredFields)));
    }

    private Set<String> parseStringSet(String value, String field) {
        try {
            List<String> parsed = json.readValue(value, new TypeReference<>() {
            });
            if (parsed == null || parsed.stream().anyMatch(item -> item == null || item.isBlank())) {
                throw new IllegalStateException(field + " contains a blank value");
            }
            return new LinkedHashSet<>(parsed);
        } catch (JsonProcessingException invalidJson) {
            throw new IllegalStateException(field + " must be a JSON string array", invalidJson);
        }
    }

    private static String addressField(Application.Address address, String field) {
        return switch (field) {
            case "line1" -> address.line1();
            case "line2" -> address.line2();
            case "city" -> address.city();
            case "postcode" -> address.postcode();
            case "country" -> address.country();
            default -> null;
        };
    }

    private static String productCode(Application application) {
        return application == null || application.product() == null
                ? null
                : application.product().productCode();
    }

    private static String stableReference(String applicationId) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(applicationId.getBytes(StandardCharsets.UTF_8));
            return "crd-" + HexFormat.of().formatHex(digest, 0, 6);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static String shortCorrelation(String correlationId) {
        if (!hasText(correlationId)) {
            return "?";
        }
        return correlationId.length() <= 8 ? correlationId : correlationId.substring(0, 8);
    }

    private static String display(String value) {
        return value == null || value.isBlank()
                ? "no country"
                : value.toUpperCase(Locale.ROOT);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record IssuingPolicy(
            int version,
            String panPrefix,
            int panLength,
            Set<String> deliveryCountries,
            Set<String> allowedProductCodes,
            Set<String> requiredAddressFields) {
    }

    private record IssueInput(
            String cardholderName,
            String productCode,
            DeliveryAddress deliveryAddress) {
    }

    private record RuleFailure(String code, String comment, String productCode) {
    }

    private record Validation(IssueInput input, RuleFailure failure) {

        private static Validation valid(IssueInput input) {
            return new Validation(input, null);
        }

        private static Validation failed(RuleFailure failure) {
            return new Validation(null, failure);
        }
    }

    private record SafeIssuedCard(
            String reference,
            String panLast4,
            String panHash,
            String bureauCardId,
            com.neobank.module.model.BureauStatus bureauStatus,
            String comment) {
    }
}
