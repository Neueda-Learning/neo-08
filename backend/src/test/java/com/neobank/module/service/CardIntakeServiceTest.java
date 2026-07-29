package com.neobank.module.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.neobank.module.dto.CardExecuteRequest;
import com.neobank.module.repository.CardRecordWriter;
import org.junit.jupiter.api.Test;

class CardIntakeServiceTest {

    private final CardRecordWriter writer = mock(CardRecordWriter.class);
    private final CardIssueProcessor issues = mock(CardIssueProcessor.class);
    private final CardIntakeService intake =
            new CardIntakeService(Runnable::run, writer, issues);

    @Test
    void newlyInsertedApplicationIsIssuedOffThread() {
        CardExecuteRequest request = request("APP-NEW");
        when(writer.insertIfAbsent("APP-NEW")).thenReturn(true);

        intake.accept(request);

        verify(issues).process(request);
        verify(issues, never()).replayStoredOutcome("APP-NEW");
    }

    @Test
    void duplicateApplicationReplaysItsStoredOutcomeWithoutReissuing() {
        CardExecuteRequest request = request("APP-DUPLICATE");
        when(writer.insertIfAbsent("APP-DUPLICATE")).thenReturn(false);

        intake.accept(request);

        verify(issues).replayStoredOutcome("APP-DUPLICATE");
        verify(issues, never()).process(request);
    }

    private static CardExecuteRequest request(String applicationId) {
        return new CardExecuteRequest(
                applicationId,
                "correlation-1",
                "issue-card",
                null,
                null);
    }
}
