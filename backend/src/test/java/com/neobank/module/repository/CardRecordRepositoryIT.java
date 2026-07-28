package com.neobank.module.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.neobank.module.model.BureauStatus;
import com.neobank.module.model.CardRecord;
import com.neobank.module.model.CardStatusHistory;
import com.neobank.module.model.StatusSource;
import com.neobank.module.service.CardRecordStore;
import com.neobank.module.service.CardRecordStore.FailedData;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Real-MySQL proof that Liquibase DDL and the card entities agree. H2 cannot
 * catch every CHAR/TIMESTAMP/FK difference that matters in deployment.
 */
@SpringBootTest
@Testcontainers
@Transactional
class CardRecordRepositoryIT {

    @Container
    static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("neo_08");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("service.orchestrator-url", () -> "http://localhost:9");
        registry.add("card.pan-hash-salt", () -> "mysql-it-salt");
    }

    @Autowired
    private CardRecordRepository cardRecords;

    @Autowired
    private CardStatusHistoryRepository history;

    @Autowired
    private IssuingConfigRepository configs;

    @Autowired
    private CardRecordStore store;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void schemaValidatesSeedsPolicyAndRemovesThePlaceholder() {
        assertThat(cardRecords.findAll()).isEmpty();
        assertThat(configs.findTopByOrderByVersionDesc())
                .get()
                .satisfies(config -> {
                    assertThat(config.getVersion()).isEqualTo(1);
                    assertThat(config.getPanPrefix()).isEqualTo("999900");
                    assertThat(config.getPanLength()).isEqualTo(16);
                    assertThat(config.getDeliveryCountries()).isEqualTo("[\"GB\",\"IE\"]");
                    assertThat(config.getAllowedProductCodes())
                            .contains("CREDIT_CARD_STANDARD")
                            .contains("CREDIT_CARD_REWARDS")
                            .contains("CREDIT_CARD_STUDENT")
                            .doesNotContain("CREDIT_CARD_PREMIUM");
                });

        Integer demoTables = jdbc.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = DATABASE() AND table_name = 'demo_showcase'
                """,
                Integer.class);
        assertThat(demoTables).isZero();
    }

    @Test
    void issuedCardAndInitialHistoryRoundTripWithOnlySafePanData() {
        CardRecord card = cardRecords.saveAndFlush(CardRecord.newInProgress("APP-1"));
        Instant decidedAt = Instant.parse("2026-07-28T08:00:00Z");
        card.markIssued(
                "CRD_ISSUED",
                "Card issued ending 4242.",
                "crd-000064",
                "4242",
                "a".repeat(64),
                "bur-77103",
                BureauStatus.REQUESTED,
                "CREDIT_CARD_REWARDS",
                1,
                decidedAt);
        cardRecords.saveAndFlush(card);
        history.saveAndFlush(new CardStatusHistory(
                "APP-1",
                BureauStatus.REQUESTED,
                StatusSource.ISSUE,
                decidedAt,
                null));

        CardRecord reloaded = cardRecords.findById("APP-1").orElseThrow();
        assertThat(reloaded.getOutcome().name()).isEqualTo("ISSUED");
        assertThat(reloaded.status()).isEqualTo("ACCEPTED");
        assertThat(reloaded.getPanLast4()).isEqualTo("4242");
        assertThat(reloaded.getPanHash()).hasSize(64);
        assertThat(reloaded.getIssuingConfigVersion()).isEqualTo(1);

        assertThat(history.findByApplicationIdOrderByObservedAtAsc("APP-1"))
                .singleElement()
                .satisfies(observation -> {
                    assertThat(observation.getStatus()).isEqualTo(BureauStatus.REQUESTED);
                    assertThat(observation.getSource()).isEqualTo(StatusSource.ISSUE);
                });
    }

    @Test
    void boardOrderingHasADeterministicApplicationIdTiebreak() {
        cardRecords.saveAndFlush(CardRecord.newInProgress("APP-A"));
        cardRecords.saveAndFlush(CardRecord.newInProgress("APP-B"));

        assertThat(cardRecords.findAllByOrderByCreatedAtDescApplicationIdDesc())
                .extracting(CardRecord::getApplicationId)
                .startsWith("APP-B", "APP-A");
    }

    @Test
    void processingLeaseSuppressesLiveDuplicatesAndRecoversStaleWork() {
        cardRecords.saveAndFlush(CardRecord.newInProgress("APP-LEASE"));
        Instant firstClaim = Instant.now().minusSeconds(10).truncatedTo(ChronoUnit.SECONDS);
        String firstToken = "00000000-0000-0000-0000-000000000001";
        String replacementToken = "00000000-0000-0000-0000-000000000002";

        assertThat(store.tryClaim(
                        "APP-LEASE",
                        firstToken,
                        firstClaim,
                        firstClaim.minusSeconds(1)))
                .isTrue();
        assertThat(store.tryClaim(
                        "APP-LEASE",
                        replacementToken,
                        firstClaim.plusSeconds(1),
                        firstClaim.minusSeconds(1)))
                .isFalse();

        assertThat(store.releaseStale(firstClaim.plusSeconds(1)))
                .containsExactly("APP-LEASE");
        CardRecord released = cardRecords.findById("APP-LEASE").orElseThrow();
        assertThat(released.status()).isEqualTo(CardRecord.IN_PROGRESS);
        assertThat(released.getProcessingStartedAt()).isNull();
        assertThat(released.getProcessingToken()).isNull();

        assertThat(store.tryClaim(
                        "APP-LEASE",
                        replacementToken,
                        firstClaim.plusSeconds(2),
                        firstClaim.plusSeconds(1)))
                .isTrue();
    }

    @Test
    void callbackOutboxIsClaimedAndCompletedWithAFencedToken() {
        cardRecords.saveAndFlush(CardRecord.newInProgress("APP-CALLBACK"));
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        String processingToken = "00000000-0000-0000-0000-000000000001";

        assertThat(store.tryClaim(
                        "APP-CALLBACK",
                        processingToken,
                        now,
                        now.minusSeconds(1)))
                .isTrue();
        String callbackToken = store.markFailed(new FailedData(
                        "APP-CALLBACK",
                        processingToken,
                        "CRD_TEST",
                        "Stored callback.",
                        "CREDIT_CARD_STANDARD",
                        1,
                        now))
                .orElseThrow()
                .callbackToken();

        assertThat(store.claimPendingCallbacks(
                        now.plusSeconds(1),
                        now.minusSeconds(1)))
                .isEmpty();
        String retryToken = store.claimPendingCallbacks(
                        now.plusSeconds(31),
                        now.plusSeconds(1))
                .getFirst()
                .callbackToken();
        assertThat(retryToken).isNotEqualTo(callbackToken);

        assertThat(store.markCallbackDelivered(
                        "APP-CALLBACK",
                        callbackToken,
                        now.plusSeconds(32)))
                .isFalse();
        assertThat(store.markCallbackDelivered(
                        "APP-CALLBACK",
                        retryToken,
                        now.plusSeconds(32)))
                .isTrue();

        CardRecord delivered = cardRecords.findById("APP-CALLBACK").orElseThrow();
        assertThat(delivered.getCallbackDeliveredAt()).isEqualTo(now.plusSeconds(32));
        assertThat(delivered.getCallbackClaimToken()).isNull();
        assertThat(store.claimPendingCallbacks(
                        now.plusSeconds(2),
                        now.plusSeconds(2)))
                .isEmpty();
    }
}
