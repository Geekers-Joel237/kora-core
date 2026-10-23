package com.geekersjoel237.koracore.auth.unit.application;

import com.geekersjoel237.koracore.auth.ports.out.otp.OtpMailTemplate;
import com.geekersjoel237.koracore.auth.domain.enums.OtpPurpose;
import com.geekersjoel237.koracore.shared.ports.out.mail.Mail;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class OtpMailTemplateTest {

    private static final Duration FIVE_MINUTES = Duration.ofMinutes(5);

    @Test
    void should_put_the_code_in_the_body() {
        Mail mail = OtpMailTemplate.compose(
                "joel@kora.test", "483920", OtpPurpose.LOGIN, FIVE_MINUTES);

        assertThat(mail.body()).contains("483920");
    }

    @Test
    void should_address_the_mail_to_the_customer() {
        Mail mail = OtpMailTemplate.compose(
                "joel@kora.test", "483920", OtpPurpose.LOGIN, FIVE_MINUTES);

        assertThat(mail.to()).isEqualTo("joel@kora.test");
    }

    @Test
    void should_say_why_it_was_sent_in_the_subject() {
        assertThat(OtpMailTemplate.compose("a@kora.test", "111111",
                OtpPurpose.REGISTRATION, FIVE_MINUTES).subject())
                .isEqualTo("Kora — verify your account");
        assertThat(OtpMailTemplate.compose("a@kora.test", "111111",
                OtpPurpose.LOGIN, FIVE_MINUTES).subject())
                .isEqualTo("Kora — login verification");
    }

    @Test
    void should_state_the_validity_it_was_given() {
        assertThat(OtpMailTemplate.compose("a@kora.test", "111111",
                OtpPurpose.LOGIN, Duration.ofMinutes(5)).body())
                .contains("5 minutes");
        assertThat(OtpMailTemplate.compose("a@kora.test", "111111",
                OtpPurpose.LOGIN, Duration.ofMinutes(10)).body())
                .contains("10 minutes");
    }

    @Test
    void should_not_say_minutes_for_a_single_minute() {
        assertThat(OtpMailTemplate.compose("a@kora.test", "111111",
                OtpPurpose.LOGIN, Duration.ofMinutes(1)).body())
                .contains("1 minute")
                .doesNotContain("1 minutes");
    }

    @Test
    void should_fall_back_to_seconds_for_a_short_window() {
        assertThat(OtpMailTemplate.compose("a@kora.test", "111111",
                OtpPurpose.LOGIN, Duration.ofSeconds(30)).body())
                .contains("30 seconds");
    }

    @Test
    void should_warn_against_sharing_it() {
        assertThat(OtpMailTemplate.compose("a@kora.test", "111111",
                OtpPurpose.LOGIN, FIVE_MINUTES).body())
                .containsIgnoringCase("do not share");
    }
}
