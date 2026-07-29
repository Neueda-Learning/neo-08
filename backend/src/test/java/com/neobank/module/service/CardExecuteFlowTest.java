package com.neobank.module.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.neobank.module.integrations.cardissue.CardIssueBureauClient;
import com.neobank.module.integrations.cardissue.CardIssueBureauClient.AcceptedCard;
import com.neobank.module.integrations.orchestrator.OrchestratorClient;
import com.neobank.module.model.Decision;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CardExecuteFlowTest {

    @TestConfiguration
    static class SameThreadExecutor {
        @Bean(name = "applicationTaskExecutor")
        Executor applicationTaskExecutor() {
            return Runnable::run;
        }
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @MockBean
    private CardIssueBureauClient bureau;

    @MockBean
    private OrchestratorClient orchestrator;

    @Test
    void successfulExecutePersistsTheCompleteIssuedCardRecord() throws Exception {
        when(bureau.issue(anyString(), any()))
                .thenReturn(Optional.of(new AcceptedCard("BUR-100")));

        mvc.perform(post("/api/v1/card/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "applicationId": "CARD-FLOW-1",
                                  "correlationId": "correlation-1",
                                  "command": "issue-card",
                                  "application": {
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
                                  },
                                  "outputs": {
                                    "accountId": "ACC-100"
                                  }
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("in-progress"))
                .andExpect(jsonPath("$.applicationId").value("CARD-FLOW-1"))
                .andExpect(jsonPath("$.command").value("issue-card"));

        Map<String, Object> row = jdbc.queryForMap(
                """
                SELECT outcome, pan_last4, pan_hash, bureau_card_id, bureau_status,
                       account_id, product_code, issuing_config_version, issued_at,
                       failure_reason
                  FROM card_record
                 WHERE application_id = ?
                """,
                "CARD-FLOW-1");

        assertThat(row.get("outcome")).isEqualTo("ISSUED");
        assertThat(row.get("pan_last4")).asString().matches("\\d{4}");
        assertThat(row.get("pan_hash")).asString().matches("[0-9a-f]{64}");
        assertThat(row.get("bureau_card_id")).isEqualTo("BUR-100");
        assertThat(row.get("bureau_status")).isEqualTo("REQUESTED");
        assertThat(row.get("account_id")).isEqualTo("ACC-100");
        assertThat(row.get("product_code")).isEqualTo("CREDIT_CARD_REWARDS");
        assertThat(((Number) row.get("issuing_config_version")).intValue()).isEqualTo(1);
        assertThat(row.get("issued_at")).isNotNull();
        assertThat(row.get("failure_reason")).isNull();

        Integer historyRows = jdbc.queryForObject(
                """
                SELECT COUNT(*)
                  FROM card_status_history
                 WHERE application_id = ?
                   AND status = 'REQUESTED'
                   AND source = 'ISSUE'
                """,
                Integer.class,
                "CARD-FLOW-1");
        assertThat(historyRows).isEqualTo(1);

        verify(orchestrator).applicationStatusUpdate(
                eq("CARD-FLOW-1"),
                eq(Decision.ACCEPTED),
                contains("ISSUED"));
    }
}
