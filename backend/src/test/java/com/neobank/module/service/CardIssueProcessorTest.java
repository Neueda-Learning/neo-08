package com.neobank.module.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neobank.module.dto.CardExecuteRequest;
import com.neobank.module.dto.IssuingConfigSnapshot;
import com.neobank.module.integrations.cardissue.CardIssueBureauClient;
import com.neobank.module.integrations.orchestrator.OrchestratorClient;
import com.neobank.module.model.Decision;
import com.neobank.module.repository.CardIssueRepository;
import com.neobank.module.repository.CardIssueRepository.IntakeCard;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CardIssueProcessorTest {

    private static final String APPLICATION_ID = "CARD-CONFIG-1";
    private static final String CONFIG_FAILURE = "CRD_ISSUING_CONFIG_INVALID";

    private final CardIssueRepository cards = mock(CardIssueRepository.class);
    private final CardIssueBureauClient bureau = mock(CardIssueBureauClient.class);
    private final OrchestratorClient orchestrator = mock(OrchestratorClient.class);
    private final CardIssueProcessor processor =
            new CardIssueProcessor(cards, new CardPanGenerator(), bureau, orchestrator);
    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void cardIsWaitingToBeIssued() {
        when(cards.findCard(APPLICATION_ID))
                .thenReturn(Optional.of(
                        new IntakeCard(APPLICATION_ID, "IN_PROGRESS", "crd-config-1", null)));
        when(cards.markFailed(
                        eq(APPLICATION_ID),
                        eq(CONFIG_FAILURE),
                        eq("ACC-100"),
                        eq("CREDIT_CARD_REWARDS"),
                        any(Instant.class)))
                .thenReturn(true);
    }

    @Test
    void missingIssuingConfigBecomesFailedInsteadOfRemainingInProgress() throws Exception {
        when(cards.findCurrentConfig()).thenReturn(Optional.empty());

        processor.process(request());

        verifyConfigFailureWasPersistedAndReported();
    }

    @Test
    void invalidIssuingConfigBecomesFailedInsteadOfEscapingTheProcessor() throws Exception {
        when(cards.findCurrentConfig()).thenReturn(Optional.of(new IssuingConfigSnapshot(
                2,
                "123456",
                16,
                List.of("GB"),
                List.of("line1", "city", "postcode", "country"),
                "http://mock-bureau:8091")));

        assertThatCode(() -> processor.process(request())).doesNotThrowAnyException();

        verifyConfigFailureWasPersistedAndReported();
        verify(bureau, never()).issue(any(), any());
    }

    @Test
    void unreadableIssuingConfigBecomesFailedInsteadOfEscapingTheProcessor() throws Exception {
        when(cards.findCurrentConfig())
                .thenThrow(new IllegalStateException("IssuingConfig contains invalid JSON"));

        assertThatCode(() -> processor.process(request())).doesNotThrowAnyException();

        verifyConfigFailureWasPersistedAndReported();
        verify(bureau, never()).issue(any(), any());
    }

    private void verifyConfigFailureWasPersistedAndReported() {
        verify(cards).markFailed(
                eq(APPLICATION_ID),
                eq(CONFIG_FAILURE),
                eq("ACC-100"),
                eq("CREDIT_CARD_REWARDS"),
                any(Instant.class));
        verify(orchestrator).applicationStatusUpdate(
                eq(APPLICATION_ID),
                eq(Decision.REFERRED),
                org.mockito.ArgumentMatchers.contains(CONFIG_FAILURE));
    }

    private CardExecuteRequest request() throws Exception {
        JsonNode application = json.readTree("""
                {
                  "applicant": {
                    "fullName": "Maria Nowak",
                    "currentAddress": {
                      "line1": "42 Hanbury Street",
                      "city": "London",
                      "postcode": "E1 5JP",
                      "country": "GB"
                    }
                  },
                  "product": {
                    "productCode": "CREDIT_CARD_REWARDS"
                  },
                  "delivery": {
                    "useCurrentAddress": true
                  }
                }
                """);
        JsonNode outputs = json.readTree("""
                {
                  "accountId": "ACC-100"
                }
                """);
        return new CardExecuteRequest(
                APPLICATION_ID, "correlation-1", "issue-card", application, outputs);
    }
}
