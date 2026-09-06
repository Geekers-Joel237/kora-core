package com.geekersjoel237.koracore.auth.adapters.out.otp;

import com.geekersjoel237.koracore.auth.application.mail.OtpMailTemplate;
import com.geekersjoel237.koracore.auth.domain.enums.OtpPurpose;
import com.geekersjoel237.koracore.auth.domain.exception.InvalidOtpException;
import com.geekersjoel237.koracore.auth.domain.exception.MailDeliveryException;
import com.geekersjoel237.koracore.auth.domain.exception.OtpExpiredException;
import com.geekersjoel237.koracore.auth.domain.vo.OtpCode;
import com.geekersjoel237.koracore.auth.ports.out.otp.OtpChallenge;
import com.geekersjoel237.koracore.shared.domain.exception.MailProviderException;
import com.geekersjoel237.koracore.shared.ports.out.mail.Mail;
import com.geekersjoel237.koracore.shared.ports.out.mail.MailPort;
import com.geekersjoel237.koracore.shared.ports.out.store.ExpiringStore;

import java.time.Duration;


public class MailedOtpChallenge implements OtpChallenge {

    private static final int MAX_DELIVERY_ATTEMPTS = 3;

    private final ExpiringStore<OtpCode> codes;
    private final MailPort mailPort;
    private final Duration validity;


    public MailedOtpChallenge(ExpiringStore<OtpCode> codes, MailPort mailPort, Duration validity) {
        this.codes = codes;
        this.mailPort = mailPort;
        this.validity = validity;
    }

    @Override
    public void issue(String email, OtpPurpose purpose) {
        OtpCode code = OtpCode.generate();
        codes.put(keyFor(email), code, validity);
        deliver(OtpMailTemplate.compose(email, code.value(), purpose, validity));
    }

    @Override
    public void consume(String email, OtpCode submitted) {
        String key = keyFor(email);

        // Gone means expired, consumed, or never issued — the store no longer tells
        // them apart, and neither should the answer a caller gets.
        OtpCode issued = codes.get(key)
                .orElseThrow(() -> new OtpExpiredException(
                        "OTP expired or already consumed for: " + email));

        if (!issued.equals(submitted))
            throw new InvalidOtpException("OTP code does not match");

        // Deleted on success only: a wrong guess must not burn the caller's code.
        codes.remove(key);
    }

    private static String keyFor(String email) {
        return "otp:" + email;
    }

    private void deliver(Mail mail) {
        MailProviderException lastFailure = null;

        for (int attempt = 1; attempt <= MAX_DELIVERY_ATTEMPTS; attempt++) {
            try {
                mailPort.send(mail);
                return;
            } catch (MailProviderException e) {
                lastFailure = e;
                if (attempt < MAX_DELIVERY_ATTEMPTS) backOff(attempt);
            }
        }
        throw new MailDeliveryException(
                "OTP delivery failed after " + MAX_DELIVERY_ATTEMPTS
                        + " attempts. Please try again in a few moments.", lastFailure);
    }

    private void backOff(int attempt) {
        try {
            Thread.sleep(100L * attempt);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MailDeliveryException(
                    "OTP delivery interrupted. Please try again in a few moments.", e);
        }
    }
}
