package com.geekersjoel237.koracore.shared.unit.domain;

import com.geekersjoel237.koracore.shared.domain.vo.Pin;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A PIN is the one field in a payment command that must never reach a log line.
 *
 * <p>Guarding that by reviewing every logger call does not scale: the anti-replay
 * middleware will fingerprint whole commands, a debug statement prints one, an
 * exception message interpolates another. The type carries the guarantee instead —
 * every one of those paths goes through {@code toString()}.
 *
 * <p>Equality still compares the real value: a redacted {@code toString()} that also
 * broke {@code equals} would make every PIN equal to every other.
 */
class PinTest {

    private static final String SECRET = "483920";

    @Test
    void never_reveals_its_value_when_printed() {
        assertThat(Pin.of(SECRET).toString()).doesNotContain(SECRET);
    }

    @Test
    void never_reveals_its_value_through_string_interpolation() {
        Pin pin = Pin.of(SECRET);

        assertThat("pin=" + pin)
                .describedAs("concatenation calls toString(), which is how PINs reach logs")
                .doesNotContain(SECRET);
    }

    @Test
    void never_reveals_its_value_through_the_record_that_holds_it() {
        record Envelope(String customerId, Pin pin) {
        }

        assertThat(new Envelope("cust-1", Pin.of(SECRET)).toString())
                .describedAs("a command's generated toString() prints its components")
                .doesNotContain(SECRET);
    }

    @Test
    void still_compares_on_the_real_value() {
        assertThat(Pin.of(SECRET)).isEqualTo(Pin.of(SECRET));
        assertThat(Pin.of(SECRET)).isNotEqualTo(Pin.of("000000"));
    }

    @Test
    void hands_the_value_over_when_asked() {
        assertThat(Pin.of(SECRET).value()).isEqualTo(SECRET);
    }

    @Test
    void refuses_to_exist_without_a_value() {
        assertThatThrownBy(() -> Pin.of("  ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Pin.of(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
