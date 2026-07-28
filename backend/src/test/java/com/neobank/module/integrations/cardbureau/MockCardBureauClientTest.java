package com.neobank.module.integrations.cardbureau;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.neobank.module.integrations.cardbureau.CardBureauClient.DeliveryAddress;
import com.neobank.module.integrations.cardbureau.CardBureauClient.IssueCard;
import com.neobank.module.model.BureauStatus;
import com.neobank.module.service.PanGenerator;
import java.util.Random;
import org.junit.jupiter.api.Test;

class MockCardBureauClientTest {

    private final MockCardBureauClient bureau = new MockCardBureauClient();
    private final PanGenerator pans = new PanGenerator(new Random(8L));
    private final DeliveryAddress address =
            new DeliveryAddress("42 Hanbury Street", null, "London", "E1 5JP", "GB");

    @Test
    void acceptsACompleteTransientInstruction() {
        var issued = bureau.issue(new IssueCard(
                "APP-1",
                "Maria Nowak",
                pans.generate("999900", 16),
                "CREDIT_CARD_REWARDS",
                address));

        assertThat(issued.bureauCardId()).matches("bur-[0-9a-f]{12}");
        assertThat(issued.status()).isEqualTo(BureauStatus.REQUESTED);
    }

    @Test
    void applicationIdMakesTheMockInstructionIdempotent() {
        String firstPan = pans.generate("999900", 16);
        String secondPan = pans.generate("999900", 16);

        var first = bureau.issue(new IssueCard(
                "APP-SAME",
                "Maria Nowak",
                firstPan,
                "CREDIT_CARD_REWARDS",
                address));
        var replay = bureau.issue(new IssueCard(
                "APP-SAME",
                "Maria Nowak",
                secondPan,
                "CREDIT_CARD_REWARDS",
                address));

        assertThat(firstPan).isNotEqualTo(secondPan);
        assertThat(replay.bureauCardId()).isEqualTo(first.bureauCardId());
    }

    @Test
    void rejectsAnInvalidPanWithoutEchoingIt() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> bureau.issue(new IssueCard(
                        "APP-1",
                        "Maria Nowak",
                        "9999000000000000",
                        "CREDIT_CARD_REWARDS",
                        address)))
                .withMessage("card bureau instruction is incomplete");
    }
}
