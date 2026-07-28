package com.neobank.module.integrations.orchestrator;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;

import com.neobank.module.model.Decision;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Pins the outbound half of the owned wire: URL, method and exactly three JSON
 * fields, with the application id only in the path.
 */
class OrchestratorClientTest {

    private MockRestServiceServer server;
    private OrchestratorClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new OrchestratorClient(
                builder.build(),
                "neo08",
                "http://orchestrator:8080");
    }

    @Test
    void putsTheExactThreeFieldCallbackToTheApplicationResource() {
        server.expect(once(), requestTo(
                        "http://orchestrator:8080/api/v1/applications/APP-123"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(content().json(
                        """
                        {
                          "serviceId": "neo08",
                          "status": "ACCEPTED",
                          "comment": "Card issued ending 4242."
                        }
                        """,
                        true))
                .andRespond(withNoContent());

        client.applicationStatusUpdate(
                "APP-123",
                Decision.ACCEPTED,
                "Card issued ending 4242.");

        server.verify();
    }

    @Test
    void anUnavailableOrchestratorDoesNotRollBackTheStoredDecision() {
        server.expect(once(), requestTo(
                        "http://orchestrator:8080/api/v1/applications/APP-FAIL"))
                .andExpect(method(HttpMethod.PUT))
                .andRespond(withServerError());

        assertThatCode(() -> client.applicationStatusUpdate(
                        "APP-FAIL",
                        Decision.REFERRED,
                        "Manual review required."))
                .doesNotThrowAnyException();

        server.verify();
    }
}
