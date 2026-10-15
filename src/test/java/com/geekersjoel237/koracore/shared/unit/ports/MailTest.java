package com.geekersjoel237.koracore.shared.unit.ports;

import com.geekersjoel237.koracore.shared.ports.out.mail.Mail;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MailTest {

    @Test
    void should_refuse_a_message_with_no_recipient() {
        assertThatThrownBy(() -> new Mail("  ", "subject", "body"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("recipient");
    }

    @Test
    void should_refuse_a_message_with_no_subject() {
        assertThatThrownBy(() -> new Mail("a@kora.test", null, "body"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("subject");
    }

    @Test
    void should_refuse_an_empty_message() {
        assertThatThrownBy(() -> new Mail("a@kora.test", "subject", ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("body");
    }
}
