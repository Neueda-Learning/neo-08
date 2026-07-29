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

    /*
     * 直接調用內部 Service 完成發卡。
     * 不再通過 CardIssueBureauClient 發送 HTTP。
     */
    private final CardIssuingService cardIssuingService;

    /*
     * 只用於向 orchestrator 重放已保存的 outcome。
     */
    private final CardIssueProcessor issues;

    public CardIntakeService(
            @Qualifier("applicationTaskExecutor")
            Executor executor,
            CardRecordWriter writer,
            CardIssuingService cardIssuingService,
            CardIssueProcessor issues) {

        this.executor = executor;
        this.writer = writer;
        this.cardIssuingService = cardIssuingService;
        this.issues = issues;
    }

    public void accept(CardExecuteRequest request) {

        String applicationId = request.applicationId();

        /*
         * UC00：先建立唯一的 IN_PROGRESS 記錄。
         */
        if (!writer.insertIfAbsent(applicationId)) {

            /*
             * 相同 applicationId 再次提交時，
             * 不重新發卡，只重放已保存的結果。
             */
            submit(
                    applicationId,
                    () -> issues.replayStoredOutcome(applicationId),
                    "decision replay"
            );

            return;
        }

        /*
         * 新申請直接使用內部 CardIssuingService。
         *
         * process() 完成後，資料庫中的 outcome
         * 已經是 ISSUED 或 FAILED。
         *
         * 然後使用 CardIssueProcessor 重放結果，
         * 向 orchestrator 發送 callback。
         */
        submit(
                applicationId,
                () -> {
                    cardIssuingService.process(request);
                    issues.replayStoredOutcome(applicationId);
                },
                "card issue"
        );
    }

    private void submit(
            String applicationId,
            Runnable work,
            String description) {

        try {
            executor.execute(work);

        } catch (RejectedExecutionException rejected) {

            log.warn(
                    "Worker rejected {} for {}; "
                            + "the durable row remains available",
                    description,
                    applicationId,
                    rejected
            );
        }
    }
}