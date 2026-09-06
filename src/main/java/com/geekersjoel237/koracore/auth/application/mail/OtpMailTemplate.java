package com.geekersjoel237.koracore.auth.application.mail;

import com.geekersjoel237.koracore.auth.domain.enums.OtpPurpose;
import com.geekersjoel237.koracore.shared.ports.out.mail.Mail;

import java.time.Duration;

public final class OtpMailTemplate {

    private OtpMailTemplate() {
    }

    public static Mail compose(String email, String code, OtpPurpose purpose, Duration validity) {
        return new Mail(email, subjectFor(purpose), body(code, validity));
    }

    private static String subjectFor(OtpPurpose purpose) {
        return switch (purpose) {
            case REGISTRATION -> "Kora — verify your account";
            case LOGIN -> "Kora — login verification";
        };
    }

    private static String body(String code, Duration validity) {
        return """
                Your Kora verification code is %s.

                It is valid for %s. Do not share it with anyone — nobody at Kora will \
                ever ask you for it.""".formatted(code, humanize(validity));
    }

    private static String humanize(Duration validity) {
        long minutes = validity.toMinutes();
        if (minutes < 1) return validity.toSeconds() + " seconds";
        return minutes + (minutes == 1 ? " minute" : " minutes");
    }
}
