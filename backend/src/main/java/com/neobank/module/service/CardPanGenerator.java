package com.neobank.module.service;

import com.neobank.module.dto.IssuingConfigSnapshot;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

/**
 * Generates a test-range, Luhn-valid PAN and immediately reduces it to last-four plus hash.
 *
 * <p>The full PAN exists only in the returned in-memory value long enough to instruct the bureau.
 * It is never logged or persisted.</p>
 */
@Component
public class CardPanGenerator {

    private static final int MAX_FRESH_ATTEMPTS = 20;
    private final SecureRandom random = new SecureRandom();

    public GeneratedPan generate(
            String applicationId,
            IssuingConfigSnapshot config,
            String previousLast4,
            String previousHash) {
        validateConfig(config);
        for (int attempt = 0; attempt < MAX_FRESH_ATTEMPTS; attempt++) {
            String pan = generateLuhnPan(config.panPrefix(), config.panLength());
            String hash = hash(applicationId, pan);
            String last4 = pan.substring(pan.length() - 4);
            if (!hash.equals(previousHash) && !last4.equals(previousLast4)) {
                return new GeneratedPan(
                        pan,
                        last4,
                        hash);
            }
        }
        throw new IllegalStateException("could not generate a fresh PAN");
    }

    private String generateLuhnPan(String prefix, int length) {
        StringBuilder partial = new StringBuilder(prefix);
        while (partial.length() < length - 1) {
            partial.append(random.nextInt(10));
        }
        partial.append(checkDigit(partial));
        return partial.toString();
    }

    private static int checkDigit(CharSequence partial) {
        int sum = 0;
        boolean doubleDigit = true;
        for (int index = partial.length() - 1; index >= 0; index--) {
            int digit = partial.charAt(index) - '0';
            if (doubleDigit) {
                digit *= 2;
                if (digit > 9) {
                    digit -= 9;
                }
            }
            sum += digit;
            doubleDigit = !doubleDigit;
        }
        return (10 - (sum % 10)) % 10;
    }

    private static String hash(String applicationId, String pan) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] value = digest.digest(
                    (applicationId + ":" + pan).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(value);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static void validateConfig(IssuingConfigSnapshot config) {
        if (config.panPrefix() == null
                || !config.panPrefix().matches("\\d+")
                || config.panPrefix().length() >= config.panLength()) {
            throw new IllegalStateException("IssuingConfig has an invalid PAN range");
        }
    }

    public record GeneratedPan(String fullPan, String last4, String hash) {
    }
}
