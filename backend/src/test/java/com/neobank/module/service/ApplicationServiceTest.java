package com.neobank.module.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.neobank.module.integrations.orchestrator.ApplicationRequest;
import com.neobank.module.integrations.orchestrator.OrchestratorClient;
import com.neobank.module.model.Decision;
import com.neobank.module.repository.DemoShowcaseRepository;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ApplicationServiceTest {

    @Test
    void handsTheExistingApplicationFlowToTheExecutor() {
        AtomicReference<Runnable> submitted = new AtomicReference<>();
        DemoShowcaseRepository repository = mock(DemoShowcaseRepository.class);
        OrchestratorClient orchestrator = mock(OrchestratorClient.class);
        ApplicationService service =
                new ApplicationService(submitted::set, repository, orchestrator);
        ApplicationRequest request =
                new ApplicationRequest("SIM-01", "corr-1", "process-application", null);

        service.processApplicationAsync(request);
        submitted.get().run();

        verify(repository).save(org.mockito.ArgumentMatchers.any());
        verify(orchestrator).applicationStatusUpdate(
                "SIM-01", Decision.ACCEPTED, "hello world from processApplication");
    }

    @Test
    void reportsReferredWhenTheExistingFlowFails() {
        DemoShowcaseRepository repository = mock(DemoShowcaseRepository.class);
        OrchestratorClient orchestrator = mock(OrchestratorClient.class);
        org.mockito.Mockito.when(repository.save(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new IllegalStateException("database unavailable"));
        ApplicationService service = new ApplicationService(Runnable::run, repository, orchestrator);

        service.processApplicationAsync(
                new ApplicationRequest("SIM-02", "corr-2", "process-application", null));

        ArgumentCaptor<String> comment = ArgumentCaptor.forClass(String.class);
        verify(orchestrator).applicationStatusUpdate(
                org.mockito.ArgumentMatchers.eq("SIM-02"),
                org.mockito.ArgumentMatchers.eq(Decision.REFERRED),
                comment.capture());
        org.assertj.core.api.Assertions.assertThat(comment.getValue())
                .contains("database unavailable");
    }
}
