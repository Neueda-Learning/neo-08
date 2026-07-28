package com.neobank.module.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neobank.module.integrations.cardbureau.CardBureauClient;
import com.neobank.module.integrations.cardbureau.CardBureauClient.IssueCard;
import com.neobank.module.integrations.cardbureau.CardBureauClient.IssuedCard;
import com.neobank.module.integrations.orchestrator.Application;
import com.neobank.module.integrations.orchestrator.ApplicationRequest;
import com.neobank.module.integrations.orchestrator.OrchestratorClient;
import com.neobank.module.model.BureauStatus;
import com.neobank.module.model.Decision;
import com.neobank.module.model.IssuingConfig;
import com.neobank.module.service.CardRecordStore.CallbackDelivery;
import com.neobank.module.service.CardRecordStore.FailedData;
import com.neobank.module.service.CardRecordStore.IntakeDisposition;
import com.neobank.module.service.CardRecordStore.IntakeResult;
import com.neobank.module.service.CardRecordStore.IssuedData;
import com.neobank.module.service.CardRecordStore.StoredDecision;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Plain unit tests for the rule flow. Spring, HTTP and a database are not
 * required to prove ordering, callback mapping or PAN privacy.
 */
@ExtendWith(OutputCaptureExtension.class)
class ApplicationServiceTest {

    private static final String TOKEN = "00000000-0000-0000-0000-000000000001";
    private static final String CALLBACK_TOKEN =
            "00000000-0000-0000-0000-000000000009";

    private static final IssuingConfig CONFIG = new IssuingConfig(
            1,
            "999900",
            16,
            "[\"GB\",\"IE\"]",
            "[\"CREDIT_CARD_STANDARD\",\"CREDIT_CARD_REWARDS\",\"CREDIT_CARD_STUDENT\"]",
            "[\"line1\",\"city\",\"postcode\",\"country\"]",
            Instant.parse("2026-01-01T00:00:00Z"));

    private CardRecordStore records;
    private OrchestratorClient orchestrator;
    private CardBureauClient bureau;
    private ApplicationService service;

    @BeforeEach
    void setUp() {
        records = mock(CardRecordStore.class);
        orchestrator = mock(OrchestratorClient.class);
        bureau = mock(CardBureauClient.class);
        when(records.currentConfig()).thenReturn(CONFIG);
        when(records.tryClaim(
                        anyString(),
                        anyString(),
                        any(Instant.class),
                        any(Instant.class)))
                .thenReturn(true);
        when(records.renewClaim(anyString(), anyString(), any(Instant.class)))
                .thenReturn(true);
        when(records.markIssued(any(IssuedData.class)))
                .thenReturn(Optional.of(new StoredDecision(
                        Decision.ACCEPTED,
                        "stored accepted comment",
                        CALLBACK_TOKEN)));
        when(records.markFailed(any(FailedData.class)))
                .thenAnswer(call -> {
                    FailedData failed = call.getArgument(0);
                    return Optional.of(new StoredDecision(
                            Decision.REFERRED,
                            failed.comment(),
                            CALLBACK_TOKEN));
                });
        when(bureau.issue(any(IssueCard.class)))
                .thenReturn(new IssuedCard("bur-123456789abc", BureauStatus.REQUESTED));

        service = serviceWithExecutor(Runnable::run);
    }

    @Test
    void aNewValidRequestIssuesOneCardStoresOnlySafePanDataAndReportsAccepted() {
        when(records.accept("SIM-01"))
                .thenReturn(new IntakeResult(IntakeDisposition.CREATED, null, null));

        service.processApplicationAsync(request("SIM-01", validApplication()));

        ArgumentCaptor<IssueCard> instruction = ArgumentCaptor.forClass(IssueCard.class);
        verify(bureau).issue(instruction.capture());
        assertThat(instruction.getValue().applicationId()).isEqualTo("SIM-01");
        assertThat(instruction.getValue().cardholderName()).isEqualTo("Maria Nowak");
        assertThat(instruction.getValue().deliveryAddress().country()).isEqualTo("GB");
        assertThat(instruction.getValue().fullPan())
                .hasSize(16)
                .startsWith("999900")
                .matches(PanGenerator::isLuhnValid);

        ArgumentCaptor<IssuedData> stored = ArgumentCaptor.forClass(IssuedData.class);
        verify(records).markIssued(stored.capture());
        assertThat(stored.getValue().applicationId()).isEqualTo("SIM-01");
        assertThat(stored.getValue().processingToken()).isNotBlank();
        assertThat(stored.getValue().reasonCode()).isEqualTo(ApplicationService.ISSUED_CODE);
        assertThat(stored.getValue().panLast4()).hasSize(4).containsOnlyDigits();
        assertThat(stored.getValue().panHash())
                .isEqualTo(PanGenerator.saltedSha256(
                        instruction.getValue().fullPan(),
                        "unit-test-salt"))
                .matches("[0-9a-f]{64}");
        assertThat(stored.getValue().toString()).doesNotContain(instruction.getValue().fullPan());

        verify(orchestrator).applicationStatusUpdate(
                "SIM-01",
                Decision.ACCEPTED,
                "stored accepted comment");
    }

    @Test
    void addressValidationRunsBeforePanGenerationOrBureauWork() {
        Application invalid = applicationWith(
                new Application.Delivery(false, null),
                validApplicant(),
                new Application.Product("CREDIT_CARD_REWARDS", 3000));

        service.processApplication(request("SIM-ADDRESS", invalid), TOKEN);

        verifyNoInteractions(bureau);
        ArgumentCaptor<FailedData> failed = ArgumentCaptor.forClass(FailedData.class);
        verify(records).markFailed(failed.capture());
        assertThat(failed.getValue().reasonCode())
                .isEqualTo(ApplicationService.INVALID_ADDRESS_CODE);
        assertThat(failed.getValue().comment())
                .contains("selected delivery address is missing")
                .contains("No card number was generated");
        verify(orchestrator).applicationStatusUpdate(
                "SIM-ADDRESS",
                Decision.REFERRED,
                failed.getValue().comment());
    }

    @Test
    void missingApplicationIsAStableReferralRatherThanAnExceptionLeak() {
        service.processApplication(request("SIM-BROKEN", null), TOKEN);

        ArgumentCaptor<FailedData> failed = ArgumentCaptor.forClass(FailedData.class);
        verify(records).markFailed(failed.capture());
        assertThat(failed.getValue().reasonCode())
                .isEqualTo(ApplicationService.INVALID_APPLICATION_CODE);
        assertThat(failed.getValue().comment()).doesNotContain("NullPointerException");
        verifyNoInteractions(bureau);
    }

    @Test
    void unknownProductIsReferredBeforePanGenerationOrBureauWork() {
        Application unknown = applicationWith(
                new Application.Delivery(true, null),
                validApplicant(),
                new Application.Product("CREDIT_CARD_PREMIUM", 3000));

        service.processApplication(request("SIM-PRODUCT", unknown), TOKEN);

        verifyNoInteractions(bureau);
        ArgumentCaptor<FailedData> failed = ArgumentCaptor.forClass(FailedData.class);
        verify(records).markFailed(failed.capture());
        assertThat(failed.getValue().reasonCode())
                .isEqualTo(ApplicationService.UNKNOWN_PRODUCT_CODE);
        assertThat(failed.getValue().comment())
                .contains("CREDIT_CARD_PREMIUM")
                .contains("does not exist")
                .contains("No card number was generated");
        verify(orchestrator).applicationStatusUpdate(
                "SIM-PRODUCT",
                Decision.REFERRED,
                failed.getValue().comment());
    }

    @Test
    void bureauFailureIsReferredWithoutPuttingThePanInTheCommentOrLogs(CapturedOutput output) {
        when(bureau.issue(any(IssueCard.class)))
                .thenThrow(new IllegalStateException("provider rejected a private request"));

        service.processApplication(request("SIM-BUREAU", validApplication()), TOKEN);

        ArgumentCaptor<FailedData> failed = ArgumentCaptor.forClass(FailedData.class);
        verify(records).markFailed(failed.capture());
        assertThat(failed.getValue().reasonCode()).isEqualTo(ApplicationService.BUREAU_CODE);
        assertThat(failed.getValue().comment())
                .doesNotContain("provider rejected")
                .doesNotMatch(".*999900[0-9]{10}.*");
        verify(orchestrator).applicationStatusUpdate(
                "SIM-BUREAU",
                Decision.REFERRED,
                failed.getValue().comment());
        assertThat(output.getAll())
                .doesNotContain("provider rejected a private request")
                .doesNotMatch("(?s).*999900[0-9]{10}.*");
    }

    @Test
    void anInProgressDuplicateDoesNotIssueOrCallbackAgain() {
        when(records.accept("SIM-DUP"))
                .thenReturn(new IntakeResult(IntakeDisposition.IN_PROGRESS, null, null));
        when(records.tryClaim(
                        anyString(),
                        anyString(),
                        any(Instant.class),
                        any(Instant.class)))
                .thenReturn(false);

        service.processApplicationAsync(request("SIM-DUP", validApplication()));

        verifyNoInteractions(bureau);
        verifyNoInteractions(orchestrator);
        verify(records, never()).markIssued(any());
        verify(records, never()).markFailed(any());
    }

    @Test
    void aStaleInProgressDuplicateClaimsAndCompletesTheOriginalWork() {
        when(records.accept("SIM-STALE"))
                .thenReturn(new IntakeResult(IntakeDisposition.IN_PROGRESS, null, null));

        service.processApplicationAsync(request("SIM-STALE", validApplication()));

        verify(records).tryClaim(
                eq("SIM-STALE"),
                anyString(),
                any(Instant.class),
                any(Instant.class));
        verify(bureau).issue(any(IssueCard.class));
        verify(orchestrator).applicationStatusUpdate(
                "SIM-STALE",
                Decision.ACCEPTED,
                "stored accepted comment");
    }

    @Test
    void aDecidedDuplicateReplaysExactlyTheStoredCallback() {
        when(records.accept("SIM-REPLAY"))
                .thenReturn(new IntakeResult(
                        IntakeDisposition.DECIDED,
                        Decision.REFERRED,
                        "stored reason"));

        service.processApplicationAsync(request("SIM-REPLAY", validApplication()));

        verify(orchestrator).applicationStatusUpdate(
                "SIM-REPLAY",
                Decision.REFERRED,
                "stored reason");
        verifyNoInteractions(bureau);
    }

    @Test
    void losingAConcurrentInsertInspectsAndReplaysTheWinningRow() {
        when(records.accept("SIM-RACE"))
                .thenThrow(new DataIntegrityViolationException("duplicate"));
        when(records.inspect("SIM-RACE"))
                .thenReturn(new IntakeResult(
                        IntakeDisposition.DECIDED,
                        Decision.ACCEPTED,
                        "winner's decision"));

        service.processApplicationAsync(request("SIM-RACE", validApplication()));

        verify(records).inspect("SIM-RACE");
        verify(orchestrator).applicationStatusUpdate(
                "SIM-RACE",
                Decision.ACCEPTED,
                "winner's decision");
        verifyNoInteractions(bureau);
    }

    @Test
    void rejectedWorkerSchedulingReleasesTheClaimAndRefusesToAcknowledge() {
        Executor rejecting = command -> {
            throw new RejectedExecutionException("pool stopped");
        };
        service = serviceWithExecutor(rejecting);
        when(records.accept("SIM-SCHEDULE"))
                .thenReturn(new IntakeResult(IntakeDisposition.CREATED, null, null));

        assertThatThrownBy(() ->
                service.processApplicationAsync(request("SIM-SCHEDULE", validApplication())))
                .isInstanceOf(ApplicationWorkerUnavailableException.class)
                .hasMessageContaining("temporarily unavailable");

        verify(records).releaseClaim(eq("SIM-SCHEDULE"), anyString());
        verify(records, never()).markFailed(any());
        verifyNoInteractions(orchestrator);
        verifyNoInteractions(bureau);
    }

    @Test
    void abandonedWorkIsReleasedForAResendWithoutInventingAnOutcome() {
        when(records.releaseStale(any(Instant.class)))
                .thenReturn(List.of("SIM-ABANDONED"));

        service.recoverStaleApplications();

        verify(records).releaseStale(any(Instant.class));
        verifyNoInteractions(orchestrator);
    }

    @Test
    void invalidCurrentConfigurationFailsBeforeAnyPanOrBureauWork() {
        when(records.currentConfig()).thenReturn(new IssuingConfig(
                2,
                "123456",
                16,
                "[\"GB\"]",
                "[\"CREDIT_CARD_REWARDS\"]",
                "[\"line1\",\"city\",\"postcode\",\"country\"]",
                Instant.now()));

        service.processApplication(request("SIM-CONFIG", validApplication()), TOKEN);

        verifyNoInteractions(bureau);
        ArgumentCaptor<FailedData> failed = ArgumentCaptor.forClass(FailedData.class);
        verify(records).markFailed(failed.capture());
        assertThat(failed.getValue().reasonCode())
                .isEqualTo(ApplicationService.CONFIGURATION_CODE);
        assertThat(failed.getValue().issuingConfigVersion()).isNull();
    }

    @Test
    void aLostTerminalRaceDoesNotSendASecondCallback() {
        when(records.markIssued(any(IssuedData.class))).thenReturn(Optional.empty());

        service.processApplication(request("SIM-LATE", validApplication()), TOKEN);

        verify(bureau).issue(any(IssueCard.class));
        verifyNoInteractions(orchestrator);
    }

    @Test
    void aSupersededWorkerStopsBeforeTheExternalBureauBoundary() {
        when(records.renewClaim(
                        eq("SIM-OWNERSHIP"),
                        eq(TOKEN),
                        any(Instant.class)))
                .thenReturn(true, false);

        service.processApplication(request("SIM-OWNERSHIP", validApplication()), TOKEN);

        verifyNoInteractions(bureau);
        verify(records, never()).markIssued(any());
        verify(records, never()).markFailed(any());
        verifyNoInteractions(orchestrator);
    }

    @Test
    void issuedBureauWorkRemainsRetryableWhenItsResultCannotBeStored() {
        when(records.markIssued(any(IssuedData.class)))
                .thenThrow(new DataIntegrityViolationException("database unavailable"));

        service.processApplication(request("SIM-PERSIST", validApplication()), TOKEN);

        verify(bureau).issue(any(IssueCard.class));
        verify(records).releaseClaim("SIM-PERSIST", TOKEN);
        verify(records, never()).markFailed(any());
        verifyNoInteractions(orchestrator);
    }

    @Test
    void pendingCallbackIsClaimedAndReplayedWithItsStoredDecision() {
        when(records.claimPendingCallbacks(any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(new CallbackDelivery(
                        "SIM-CALLBACK",
                        Decision.ACCEPTED,
                        "stored callback",
                        CALLBACK_TOKEN)));

        service.retryPendingCallbacks();

        verify(orchestrator).applicationStatusUpdate(
                "SIM-CALLBACK",
                Decision.ACCEPTED,
                "stored callback");
    }

    @Test
    void rejectedCallbackSchedulingReleasesOnlyItsOutboxClaim() {
        service = serviceWithExecutor(command -> {
            throw new RejectedExecutionException("pool stopped");
        });
        when(records.claimPendingCallbacks(any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(new CallbackDelivery(
                        "SIM-CALLBACK-BUSY",
                        Decision.REFERRED,
                        "stored callback",
                        CALLBACK_TOKEN)));

        service.retryPendingCallbacks();

        verify(records).releaseCallbackClaim("SIM-CALLBACK-BUSY", CALLBACK_TOKEN);
        verifyNoInteractions(orchestrator);
    }

    private ApplicationService serviceWithExecutor(Executor executor) {
        return new ApplicationService(
                executor,
                records,
                orchestrator,
                bureau,
                new PanGenerator(new Random(42L)),
                new CallbackDeliveryContext(),
                new ObjectMapper(),
                "unit-test-salt",
                Duration.ofMinutes(2),
                Duration.ofSeconds(30));
    }

    private static ApplicationRequest request(String id, Application application) {
        return new ApplicationRequest(id, "corr-0001", "process-application", application);
    }

    private static Application validApplication() {
        return applicationWith(
                new Application.Delivery(true, null),
                validApplicant(),
                new Application.Product("CREDIT_CARD_REWARDS", 3000));
    }

    private static Application.Applicant validApplicant() {
        return new Application.Applicant(
                "Maria Nowak",
                "1996-04-11",
                "maria@example.com",
                "+447700900001",
                "PL",
                "GB",
                null,
                "RENTING",
                new Application.Address(
                        "42 Hanbury Street",
                        null,
                        "London",
                        "E1 5JP",
                        "GB"),
                24,
                0);
    }

    private static Application applicationWith(
            Application.Delivery delivery,
            Application.Applicant applicant,
            Application.Product product) {
        return new Application(
                "inner-id-is-not-authoritative",
                "MOBILE_APP",
                "2026-07-25T09:14:00Z",
                applicant,
                null,
                null,
                null,
                product,
                delivery,
                null);
    }
}
