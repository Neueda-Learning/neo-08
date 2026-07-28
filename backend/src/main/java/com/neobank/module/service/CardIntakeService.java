package com.neobank.module.service;

import com.neobank.module.dto.CardExecuteRequest;
import com.neobank.module.repository.CardRecordWriter;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/** UC-00 intake service for the v5 card execute endpoint. */
@Service
public class CardIntakeService {

    private static final Logger log = LoggerFactory.getLogger(CardIntakeService.class);

    private final Executor executor;
    private final CardRecordWriter writer;

    public CardIntakeService(@Qualifier("applicationTaskExecutor") Executor executor,
                             CardRecordWriter writer) {
        this.executor = executor;
        this.writer = writer;
    }

    public void accept(CardExecuteRequest request) {
        String applicationId = request.applicationId();
        if (!writer.insertIfAbsent(applicationId)) {
            return;
        }

        try {
            executor.execute(() -> processOffThread(request));
        } catch (RejectedExecutionException rejected) {
            log.warn("Worker rejected card application {}; the IN_PROGRESS row remains durable",
                    applicationId, rejected);
        }
    }

    private void processOffThread(CardExecuteRequest request) {
        // UC-00 stops at the memory-only hand-off. Later use cases replace this body.
        log.info("Card application {} handed to the worker", request.applicationId());
    }
}
