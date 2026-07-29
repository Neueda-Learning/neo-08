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

    private static final Logger log =
            LoggerFactory.getLogger(CardIntakeService.class);

    private final Executor executor;
    private final CardRecordWriter writer;
    private final CardIssuingService cardIssuingService;

    public CardIntakeService(
            @Qualifier("applicationTaskExecutor")
            Executor executor,
            CardRecordWriter writer,
            CardIssuingService cardIssuingService) {

        this.executor = executor;
        this.writer = writer;
        this.cardIssuingService = cardIssuingService;
    }

    public void accept(CardExecuteRequest request) {

        String applicationId =
                request.applicationId();

        log.info(
                "Received card application {}",
                applicationId
        );

        boolean inserted =
                writer.insertIfAbsent(applicationId);

        log.info(
                "Card application {} inserted={}",
                applicationId,
                inserted
        );

        if (!inserted) {
            log.info(
                    "Card application {} already exists; "
                            + "skipping async processing",
                    applicationId
            );
            return;
        }

        try {
            executor.execute(() -> {
                log.info(
                        "Worker started for {}",
                        applicationId
                );

                processOffThread(request);
            });

            log.info(
                    "Card application {} submitted to worker",
                    applicationId
            );

        } catch (RejectedExecutionException exception) {
            log.warn(
                    "Worker rejected card application {}",
                    applicationId,
                    exception
            );
        }
    }
    private void processOffThread(
            CardExecuteRequest request) {

        try {

            cardIssuingService.process(request);
        } catch (RuntimeException exception) {
            log.error(
                    "Unexpected card issuing error for {}",
                    request.applicationId(),
                    exception
            );
        }
    }
}