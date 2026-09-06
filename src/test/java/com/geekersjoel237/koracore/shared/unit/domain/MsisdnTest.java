package com.geekersjoel237.koracore.shared.unit.domain;

import com.geekersjoel237.koracore.shared.domain.vo.Msisdn;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A phone number is PII. In a mobile-money wallet it is also the account identifier,
 * so it appears in commands, in lookups and in the message of every "recipient not
 * found" error — three paths that all end in a log file.
 *
 * <p>The type carries the redaction, as {@code Pin} does, so a future record that
 * happens to hold one is covered without anyone remembering to be careful.
 *
 * <p>{@code PhoneNumber} gets the same treatment in {@code PhoneNumberTest}, over in
 * auth where it lives — asserting it from here made the kernel's tests name a module.
 */
class MsisdnTest {

    private static final String FULL = "+237600000123";

    @Test
    void an_msisdn_never_reveals_itself_when_printed() {
        assertThat(Msisdn.of(FULL).toString()).doesNotContain(FULL);
    }

    @Test
    void an_msisdn_never_reveals_itself_through_the_record_that_holds_it() {
        record Envelope(String customerId, Msisdn recipient) {
        }

        assertThat(new Envelope("cust-1", Msisdn.of(FULL)).toString())
                .describedAs("a command's generated toString() prints its components")
                .doesNotContain(FULL);
    }

    @Test
    void an_msisdn_keeps_enough_to_be_recognised_by_its_owner() {
        String masked = Msisdn.of(FULL).masked();

        assertThat(masked).startsWith("+237").endsWith("123").contains("*");
        assertThat(masked).doesNotContain(FULL);
    }

    @Test
    void an_msisdn_hands_its_value_over_when_asked() {
        assertThat(Msisdn.of(FULL).value()).isEqualTo(FULL);
    }

    @Test
    void an_msisdn_refuses_anything_that_cannot_be_dialled() {
        assertThatThrownBy(() -> Msisdn.of("237600000123")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Msisdn.of("+22")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Msisdn.of("+237ABC000123")).isInstanceOf(IllegalArgumentException.class);
    }

}
