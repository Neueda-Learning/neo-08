package com.neobank.module;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Boots the whole module against H2. Liquibase creates the schema, Hibernate
 * validates it, and MockMvc drives the same HTTP surface the sidecar uses.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ModuleApplicationTests {

    /**
     * Captures worker tasks instead of sleeping. A test can inspect the durable
     * pre-202 checkpoint, then run exactly one off-thread job deterministically.
     */
    @TestConfiguration
    static class ControlledWorkerConfiguration {

        @Bean(name = "applicationTaskExecutor")
        ControlledExecutor applicationTaskExecutor() {
            return new ControlledExecutor();
        }
    }

    static final class ControlledExecutor implements Executor {
        private final Queue<Runnable> tasks = new ConcurrentLinkedQueue<>();

        @Override
        public void execute(Runnable command) {
            tasks.add(command);
        }

        void runNext() {
            Runnable task = tasks.poll();
            if (task == null) {
                throw new AssertionError("no worker task was scheduled");
            }
            task.run();
        }

        int pending() {
            return tasks.size();
        }

        void clear() {
            tasks.clear();
        }
    }

    private static final String APPLICATION = """
            {
              "applicationId": "%s",
              "correlationId": "sim-0001-4c1a-8f2b-1d5e9a000001",
              "command": "process-application",
              "application": {
                "applicationId": "%s",
                "channel": "MOBILE_APP",
                "submittedAt": "2026-07-25T09:14:00Z",
                "applicant": {
                  "fullName": "Maria Nowak",
                  "dateOfBirth": "1996-04-11",
                  "currentAddress": {
                    "line1": "42 Hanbury Street",
                    "city": "London",
                    "postcode": "E1 5JP",
                    "country": "GB"
                  }
                },
                "product": {
                  "productCode": "CREDIT_CARD_REWARDS",
                  "requestedCreditLimit": 3000
                },
                "delivery": {"useCurrentAddress": true}
              }
            }
            """;

    private static final String INVALID_ADDRESS_APPLICATION = """
            {
              "applicationId": "%s",
              "command": "process-application",
              "application": {
                "applicant": {"fullName": "Sofia Petrov"},
                "product": {"productCode": "CREDIT_CARD_STANDARD"},
                "delivery": {"useCurrentAddress": false}
              }
            }
            """;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ControlledExecutor workers;

    @BeforeEach
    void clearPendingWorkers() {
        workers.clear();
    }

    private static String application(String id) {
        return APPLICATION.formatted(id, id);
    }

    @Test
    void contextLoads() {
        // Reaching here proves all three change sets and ddl-auto=validate agree.
    }

    @Test
    void healthReportsUp() throws Exception {
        mvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.serviceId").value("neo08"))
                .andExpect(jsonPath("$.database.status").value("UP"));
    }

    @Test
    void infoReportsCardIdentityAndMockRegister() throws Exception {
        mvc.perform(get("/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serviceId").value("neo08"))
                .andExpect(jsonPath("$.domain").value("card"))
                .andExpect(jsonPath("$.team").value("Team 08"))
                .andExpect(jsonPath("$.mockedDependencies", hasSize(1)))
                .andExpect(jsonPath("$.mockedDependencies[0]")
                        .value("card-personalisation-bureau"));
    }

    @Test
    void openApiPublishesBothSidesOfTheLocalHttpSurface() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/applications'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/applications'].get").exists());
    }

    @Test
    void acknowledgementFollowsDurableIntakeThenWorkerCompletesTheCard() throws Exception {
        mvc.perform(post("/api/v1/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(application("IT-ONE")))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("in-progress"))
                .andExpect(jsonPath("$.applicationId").value("IT-ONE"))
                .andExpect(jsonPath("$.serviceId").value("neo08"))
                .andExpect(jsonPath("$.command").value("process-application"))
                .andExpect(jsonPath("$.*", hasSize(4)));

        assertThat(workers.pending()).isEqualTo(1);
        mvc.perform(get("/api/v1/applications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.applicationId == 'IT-ONE')].status")
                        .value(hasItem("in-progress")))
                .andExpect(jsonPath("$[?(@.applicationId == 'IT-ONE')].outcome")
                        .value(hasItem("IN_PROGRESS")))
                .andExpect(jsonPath("$[?(@.applicationId == 'IT-ONE')].createdAt")
                        .value(everyItem(notNullValue())));

        workers.runNext();

        MvcResult result = mvc.perform(get("/api/v1/applications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.applicationId == 'IT-ONE')].status")
                        .value(hasItem("ACCEPTED")))
                .andExpect(jsonPath("$[?(@.applicationId == 'IT-ONE')].outcome")
                        .value(hasItem("ISSUED")))
                .andExpect(jsonPath("$[?(@.applicationId == 'IT-ONE')].reasonCode")
                        .value(hasItem("CRD_ISSUED")))
                .andExpect(jsonPath("$[?(@.applicationId == 'IT-ONE')].panMasked")
                        .value(everyItem(org.hamcrest.Matchers.matchesPattern(
                                "\\*\\*\\*\\* \\*\\*\\*\\* \\*\\*\\*\\* [0-9]{4}"))))
                .andExpect(jsonPath("$[?(@.applicationId == 'IT-ONE')].issuingConfigVersion")
                        .value(hasItem(1)))
                .andReturn();

        assertThat(result.getResponse().getContentAsString())
                .doesNotMatch(".*999900[0-9]{10}.*");
    }

    @Test
    void invalidDeliveryIsReferredWithoutCreatingCardData() throws Exception {
        mvc.perform(post("/api/v1/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(INVALID_ADDRESS_APPLICATION.formatted("IT-ADDRESS")))
                .andExpect(status().isAccepted());

        workers.runNext();

        mvc.perform(get("/api/v1/applications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.applicationId == 'IT-ADDRESS')].status")
                        .value(hasItem("REFERRED")))
                .andExpect(jsonPath("$[?(@.applicationId == 'IT-ADDRESS')].outcome")
                        .value(hasItem("FAILED")))
                .andExpect(jsonPath("$[?(@.applicationId == 'IT-ADDRESS')].reasonCode")
                        .value(hasItem("CRD_DELIVERY_ADDRESS_INVALID")))
                .andExpect(jsonPath("$[?(@.applicationId == 'IT-ADDRESS')].panMasked")
                        .value(everyItem(nullValue())));
    }

    @Test
    void repeatedApplicationIdStaysSingleAndDoesNotScheduleASecondIssue() throws Exception {
        mvc.perform(post("/api/v1/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(application("IT-DUP")))
                .andExpect(status().isAccepted());

        mvc.perform(post("/api/v1/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(application("IT-DUP")))
                .andExpect(status().isAccepted());

        assertThat(workers.pending()).isEqualTo(1);
        mvc.perform(get("/api/v1/applications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.applicationId == 'IT-DUP')]").value(hasSize(1)));
    }

    @Test
    void applicationWithoutIdIsRejectedBeforeStorage() throws Exception {
        mvc.perform(post("/api/v1/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"correlationId":"c-1","command":"process-application",
                                 "application":{"channel":"WEB"}}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("applicationId")));

        assertThat(workers.pending()).isZero();
    }

    @Test
    void commandRemainsAnOptionalEchoInTheFixedContract() throws Exception {
        mvc.perform(post("/api/v1/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"applicationId":"IT-NO-CMD","application":{"channel":"WEB"}}
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("in-progress"))
                .andExpect(jsonPath("$.applicationId").value("IT-NO-CMD"))
                .andExpect(jsonPath("$.serviceId").value("neo08"))
                .andExpect(jsonPath("$.command").value(nullValue()))
                .andExpect(jsonPath("$.*", hasSize(4)));
    }

    @Test
    void malformedJsonIsA400WithAReadableError() throws Exception {
        mvc.perform(post("/api/v1/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"applicationId\":\"X\",,}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("malformed request body")));
    }
}
