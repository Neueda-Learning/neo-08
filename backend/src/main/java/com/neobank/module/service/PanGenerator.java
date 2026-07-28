package com.neobank.module.service;

import java.nio.charset.StandardCharsets;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Objects;
import java.util.random.RandomGenerator;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Generates test-range primary account numbers (PANs) and derives the only values that may be
 * persisted from them.
 *
 * <p>The generated PAN is held only in a local variable. Callers should send it to the card bureau,
 * derive its last four digits and salted hash, and then let the value leave scope.</p>
 */
public final class PanGenerator {

    private final RandomGenerator random;
    private final Object randomLock = new Object();

    /** Uses a cryptographically secure source for production card-number generation. */
    public PanGenerator() {
        this(new SecureRandom());
    }

    /** Accepts a deterministic source for repeatable tests. */
    public PanGenerator(RandomGenerator random) {
        this.random = Objects.requireNonNull(random, "random must not be null");
    }

    /**
     * Generates a numeric PAN with the supplied prefix and a trailing Luhn check digit.
     *
     * @param prefix fixed numeric test-range prefix
     * @param length total PAN length, including the check digit
     */
    public String generate(String prefix, int length) {
        int generatedDigits = generatedDigits(prefix, length);

        StringBuilder payload = new StringBuilder(length);
        payload.append(prefix);

        // RandomGenerator implementations are not all thread-safe (for example SplittableRandom).
        synchronized (randomLock) {
            while (payload.length() < prefix.length() + generatedDigits) {
                payload.append(random.nextInt(10));
            }
        }

        payload.append(luhnCheckDigit(payload));
        return payload.toString();
    }

    /**
     * Generates the same cryptographically derived test PAN for the same idempotency key.
     *
     * <p>This is used for orchestrator retries after a process interruption. The secret keeps the
     * number unpredictable from the public application id, while stability makes a repeated bureau
     * instruction byte-for-byte identical instead of risking a second card number.</p>
     */
    public String generateStable(
            String prefix,
            int length,
            String idempotencyKey,
            String secret) {
        int generatedDigits = generatedDigits(prefix, length);
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey must not be blank");
        }
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("secret must not be blank");
        }

        try {
            Mac hmac = Mac.getInstance("HmacSHA256");
            hmac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            hmac.update("neo08-card-pan".getBytes(StandardCharsets.UTF_8));
            hmac.update((byte) 0);
            BigInteger value = new BigInteger(
                    1,
                    hmac.doFinal(idempotencyKey.getBytes(StandardCharsets.UTF_8)));
            String digits = value.mod(BigInteger.TEN.pow(generatedDigits)).toString();

            StringBuilder payload = new StringBuilder(length);
            payload.append(prefix);
            payload.append("0".repeat(generatedDigits - digits.length()));
            payload.append(digits);
            payload.append(luhnCheckDigit(payload));
            return payload.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("HmacSHA256 is unavailable", impossible);
        } catch (java.security.InvalidKeyException invalidKey) {
            throw new IllegalArgumentException("secret is not a valid HMAC key", invalidKey);
        }
    }

    /** Returns the four displayable digits after rejecting malformed PAN text. */
    public static String lastFour(String pan) {
        requireDigits(pan, "pan");
        if (pan.length() < 4) {
            throw new IllegalArgumentException("pan must contain at least four digits");
        }
        return pan.substring(pan.length() - 4);
    }

    /**
     * Produces a deterministic salted SHA-256 digest without retaining the PAN.
     *
     * <p>A separator makes the salt/PAN boundary unambiguous.</p>
     */
    public static String saltedSha256(String pan, String salt) {
        requireDigits(pan, "pan");
        if (salt == null || salt.isBlank()) {
            throw new IllegalArgumentException("salt must not be blank");
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(salt.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            return HexFormat.of().formatHex(digest.digest(pan.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    /** Returns whether the supplied ASCII digit string satisfies the Luhn checksum. */
    public static boolean isLuhnValid(String pan) {
        if (!hasDigits(pan)) {
            return false;
        }

        int sum = 0;
        boolean doubleDigit = false;
        for (int index = pan.length() - 1; index >= 0; index--) {
            int digit = pan.charAt(index) - '0';
            if (doubleDigit) {
                digit *= 2;
                if (digit > 9) {
                    digit -= 9;
                }
            }
            sum += digit;
            doubleDigit = !doubleDigit;
        }
        return sum % 10 == 0;
    }

    private static int luhnCheckDigit(CharSequence payload) {
        int sum = 0;
        boolean doubleDigit = true;
        for (int index = payload.length() - 1; index >= 0; index--) {
            int digit = payload.charAt(index) - '0';
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

    private static int generatedDigits(String prefix, int length) {
        requireDigits(prefix, "prefix");
        if (length < prefix.length() + 2) {
            throw new IllegalArgumentException(
                    "length must leave room for at least one generated digit and the check digit");
        }
        return length - prefix.length() - 1;
    }

    private static void requireDigits(String value, String name) {
        if (!hasDigits(value)) {
            throw new IllegalArgumentException(name + " must contain only ASCII digits");
        }
    }

    private static boolean hasDigits(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character < '0' || character > '9') {
                return false;
            }
        }
        return true;
    }
}
