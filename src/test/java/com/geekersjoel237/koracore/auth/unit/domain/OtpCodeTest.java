package com.geekersjoel237.koracore.auth.unit.domain;

import com.geekersjoel237.koracore.auth.domain.vo.OtpCode;
import org.junit.jupiter.api.Test;

import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Replaces {@code OtpTest}, whose subject no longer exists.
 *
 * <p>What that test covered in two halves now lives in two places: the code itself is
 * here, and everything about how long it lasts is in
 * {@code InMemoryExpiringStoreTest}, which is where the lifetime moved.
 */
class OtpCodeTest {

    private static final String VALID = "123456";

    @Test
    void should_accept_six_digits() {
        assertThat(OtpCode.of(VALID).value()).isEqualTo(VALID);
    }

    /** A draw below 100000 must still be six digits, or it would not survive its own type. */
    @Test
    void should_generate_a_code_its_own_rule_accepts() {
        IntStream.range(0, 500).forEach(i -> {
            OtpCode generated = OtpCode.generate();
            assertThat(generated.value()).hasSize(6).containsOnlyDigits();
            assertThat(OtpCode.of(generated.value())).isEqualTo(generated);
        });
    }

    @Test
    void should_not_always_generate_the_same_code() {
        assertThat(IntStream.range(0, 50)
                .mapToObj(i -> OtpCode.generate().value())
                .distinct()
                .count())
                .isGreaterThan(1);
    }

    @Test
    void should_refuse_anything_that_is_not_six_digits() {
        assertThatThrownBy(() -> OtpCode.of(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> OtpCode.of("")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> OtpCode.of("   ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> OtpCode.of("12345")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> OtpCode.of("1234567")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> OtpCode.of("12345a")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> OtpCode.of(" 12345")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_compare_by_value() {
        assertThat(OtpCode.of(VALID)).isEqualTo(OtpCode.of(VALID));
        assertThat(OtpCode.of(VALID)).isNotEqualTo(OtpCode.of("000000"));
    }

    /**
     * The challenge puts a code into exception messages and the store keys on it. A
     * record's generated {@code toString} would have printed the secret in both.
     */
    @Test
    void should_never_print_itself() {
        assertThat(OtpCode.of(VALID).toString()).isEqualTo("***").doesNotContain(VALID);
        assertThat("code=" + OtpCode.of(VALID)).doesNotContain(VALID);
    }
}
