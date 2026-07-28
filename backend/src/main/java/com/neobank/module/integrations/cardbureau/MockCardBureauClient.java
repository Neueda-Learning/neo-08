package com.neobank.module.integrations.cardbureau;

import com.neobank.module.model.BureauStatus;
import com.neobank.module.service.PanGenerator;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

/**
 * Local stand-in for the external card-personalisation bureau named by
 * {@code MOCKED_DEPENDENCIES}. It validates the transient instruction and returns
 * a stable bureau id, but deliberately stores nothing. The module derives a
 * stable test PAN for retries, so the full repeated instruction is identical.
 *
 * <p>The stable id makes an instruction idempotent by application id: retrying
 * the same instruction cannot mint a second bureau identity.</p>
 */
@Component
public class MockCardBureauClient implements CardBureauClient {

    @Override
    public IssuedCard issue(IssueCard command) {
        if (command == null
                || !hasText(command.applicationId())
                || !hasText(command.cardholderName())
                || !hasText(command.productCode())
                || command.deliveryAddress() == null
                || !PanGenerator.isLuhnValid(command.fullPan())) {
            throw new IllegalArgumentException("card bureau instruction is incomplete");
        }
        return new IssuedCard("bur-" + stableSuffix(command.applicationId()), BureauStatus.REQUESTED);
    }

    private static String stableSuffix(String applicationId) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(applicationId.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 6);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
