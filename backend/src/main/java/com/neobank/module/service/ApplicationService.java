package com.neobank.module.service;

import com.neobank.module.dto.DemoShowcaseView;
import com.neobank.module.integrations.orchestrator.ApplicationRequest;
import com.neobank.module.integrations.orchestrator.OrchestratorClient;
import com.neobank.module.model.Decision;
import com.neobank.module.model.DemoShowcase;
import com.neobank.module.repository.DemoShowcaseRepository;
import java.util.List;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApplicationService {

    private static final Logger log = LoggerFactory.getLogger(ApplicationService.class);

    private final Executor executor;
    private final DemoShowcaseRepository demoShowcase;
    private final OrchestratorClient orchestrator;

    public ApplicationService(@Qualifier("applicationTaskExecutor") Executor executor,
                              DemoShowcaseRepository demoShowcase,
                              OrchestratorClient orchestrator) {
        this.executor = executor;
        this.demoShowcase = demoShowcase;
        this.orchestrator = orchestrator;
    }

    public void processApplicationAsync(ApplicationRequest request) {
        executor.execute(() -> processApplication(request));
    }

    void processApplication(ApplicationRequest request) {
        String applicationId = request.applicationId();
        try {
            log.info("Hello world from processApplication — {}", request.summary());
            demoShowcase.save(new DemoShowcase(applicationId, Decision.ACCEPTED));
            orchestrator.applicationStatusUpdate(applicationId, Decision.ACCEPTED,
                    "hello world from processApplication");
        } catch (RuntimeException e) {
            log.error("processApplication failed for {} — referring", applicationId, e);
            orchestrator.applicationStatusUpdate(applicationId, Decision.REFERRED,
                    "module error: " + e);
        }
    }

    @Transactional(readOnly = true)
    public List<DemoShowcaseView> findAll() {
        return demoShowcase.findAllByOrderByCreatedAtDescIdDesc().stream()
                .map(DemoShowcaseView::of)
                .toList();
    }
}
