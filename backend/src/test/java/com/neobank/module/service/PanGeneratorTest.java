package com.neobank.module.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.Test;

class PanGeneratorTest {

    @Test
    void generatesOneThousandUniqueLuhnValidTestRangeNumbers() {
        PanGenerator generator = new PanGenerator(new Random(20260728L));
        Set<String> generated = new HashSet<>();

        for (int count = 0; count < 1_000; count++) {
            String pan = generator.generate("999900", 16);

            assertThat(pan).hasSize(16).startsWith("999900").containsOnlyDigits();
            assertThat(PanGenerator.isLuhnValid(pan)).isTrue();
            generated.add(pan);
        }

        assertThat(generated).hasSize(1_000);
    }

    @Test
    void rejectsInvalidGenerationArguments() {
        PanGenerator generator = new PanGenerator(new Random(1L));

        assertThatNullPointerException().isThrownBy(() -> new PanGenerator(null));
        assertThatIllegalArgumentException().isThrownBy(() -> generator.generate(null, 16));
        assertThatIllegalArgumentException().isThrownBy(() -> generator.generate("", 16));
        assertThatIllegalArgumentException().isThrownBy(() -> generator.generate("9999A0", 16));
        assertThatIllegalArgumentException().isThrownBy(() -> generator.generate("１２３", 16));
        assertThatIllegalArgumentException().isThrownBy(() -> generator.generate("999900", 7));
        assertThatIllegalArgumentException().isThrownBy(() -> generator.generate("999900", -1));
    }

    @Test
    void stableGenerationIsIdempotentSecretSensitiveAndLuhnValid() {
        PanGenerator generator = new PanGenerator(new Random(1L));

        String first = generator.generateStable("999900", 16, "APP-123", "secret-one");
        String repeated = generator.generateStable("999900", 16, "APP-123", "secret-one");
        String otherApplication =
                generator.generateStable("999900", 16, "APP-124", "secret-one");
        String otherSecret =
                generator.generateStable("999900", 16, "APP-123", "secret-two");

        assertThat(first)
                .hasSize(16)
                .startsWith("999900")
                .matches(PanGenerator::isLuhnValid)
                .isEqualTo(repeated)
                .isNotEqualTo(otherApplication)
                .isNotEqualTo(otherSecret);
    }

    @Test
    void rejectsInvalidStableGenerationArguments() {
        PanGenerator generator = new PanGenerator(new Random(1L));

        assertThatIllegalArgumentException()
                .isThrownBy(() -> generator.generateStable("999900", 16, null, "secret"));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> generator.generateStable("999900", 16, "APP-1", ""));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> generator.generateStable("999900", 7, "APP-1", "secret"));
    }

    @Test
    void extractsExactlyFourDigitsAndRejectsMalformedValues() {
        assertThat(PanGenerator.lastFour("79927398713")).isEqualTo("8713");
        assertThat(PanGenerator.lastFour("0000")).isEqualTo("0000");

        assertThatIllegalArgumentException().isThrownBy(() -> PanGenerator.lastFour(null));
        assertThatIllegalArgumentException().isThrownBy(() -> PanGenerator.lastFour("123"));
        assertThatIllegalArgumentException().isThrownBy(() -> PanGenerator.lastFour("12x4"));
    }

    @Test
    void createsStableSaltSensitiveLowercaseHashes() {
        String first = PanGenerator.saltedSha256("79927398713", "test-salt");
        String repeated = PanGenerator.saltedSha256("79927398713", "test-salt");
        String differentSalt = PanGenerator.saltedSha256("79927398713", "other-salt");
        String differentPan = PanGenerator.saltedSha256("79927398714", "test-salt");

        assertThat(first).matches("[0-9a-f]{64}").isEqualTo(repeated);
        assertThat(differentSalt).matches("[0-9a-f]{64}").isNotEqualTo(first);
        assertThat(differentPan).matches("[0-9a-f]{64}").isNotEqualTo(first);
    }

    @Test
    void rejectsMalformedHashArguments() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> PanGenerator.saltedSha256(null, "salt"));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> PanGenerator.saltedSha256("123x", "salt"));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> PanGenerator.saltedSha256("1234", null));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> PanGenerator.saltedSha256("1234", ""));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> PanGenerator.saltedSha256("1234", "  "));
    }

    @Test
    void luhnPredicateRejectsMalformedAndChecksumInvalidValues() {
        assertThat(PanGenerator.isLuhnValid("79927398713")).isTrue();
        assertThat(PanGenerator.isLuhnValid("79927398714")).isFalse();
        assertThat(PanGenerator.isLuhnValid(null)).isFalse();
        assertThat(PanGenerator.isLuhnValid("")).isFalse();
        assertThat(PanGenerator.isLuhnValid("79927x98713")).isFalse();
    }

    @Test
    void serializesAccessToAnInjectedNonThreadSafeGenerator() throws Exception {
        PanGenerator generator = new PanGenerator(new FailOnConcurrentUseRandom());
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<Callable<String>> tasks = new ArrayList<>();
            for (int count = 0; count < 100; count++) {
                tasks.add(() -> generator.generate("999900", 16));
            }

            List<String> generated = new ArrayList<>();
            executor.invokeAll(tasks).forEach(future -> {
                try {
                    generated.add(future.get());
                } catch (Exception failure) {
                    throw new AssertionError(failure);
                }
            });

            assertThat(generated).hasSize(100)
                    .allSatisfy(pan -> {
                        assertThat(pan).hasSize(16).startsWith("999900");
                        assertThat(PanGenerator.isLuhnValid(pan)).isTrue();
                    });
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * Deliberately fails if called concurrently, making the generator's locking requirement
     * observable instead of relying on the implementation details of {@link Random}.
     */
    private static final class FailOnConcurrentUseRandom implements RandomGenerator {
        private final AtomicBoolean inUse = new AtomicBoolean();
        private final AtomicLong sequence = new AtomicLong();

        @Override
        public long nextLong() {
            if (!inUse.compareAndSet(false, true)) {
                throw new IllegalStateException("concurrent random access");
            }
            try {
                LockSupport.parkNanos(100_000);
                return sequence.getAndIncrement() * 0x9E3779B97F4A7C15L;
            } finally {
                inUse.set(false);
            }
        }
    }
}
