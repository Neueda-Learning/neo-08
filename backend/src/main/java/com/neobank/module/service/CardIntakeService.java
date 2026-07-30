package com.neobank.module.service;

import com.neobank.module.dto.CardExecuteRequest;
import com.neobank.module.repository.CardRecordWriter;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class CardIntakeService {

    private static final Logger log = LoggerFactory.getLogger(CardIntakeService.class);

    private final Executor executor;
    private final CardRecordWriter writer;
    private final CardIssueProcessor issues;

    public CardIntakeService(
            @Qualifier("applicationTaskExecutor") Executor executor,
            CardRecordWriter writer,
            CardIssueProcessor issues) {
        this.executor = executor;
        this.writer = writer;
        this.issues = issues;
    }

    public void accept(CardExecuteRequest request) {
        String applicationId = request.applicationId();
        if (!writer.insertIfAbsent(applicationId)) {
            submit(
                    applicationId,
                    () -> issues.replayStoredOutcome(applicationId),
                    "decision replay");
            return;
        }

        submit(applicationId, () -> issues.process(request), "card issue");
    }

    private void submit(String applicationId, Runnable work, String description) {
        try {
            executor.execute(work);
        } catch (RejectedExecutionException rejected) {
            log.warn(
                    "Worker rejected {} for {}; the durable row remains available",
                    description,
                    applicationId,
                    rejected);
        }
    }
}
