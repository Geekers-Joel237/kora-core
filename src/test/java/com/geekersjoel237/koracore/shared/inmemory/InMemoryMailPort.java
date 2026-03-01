package com.geekersjoel237.koracore.shared.inmemory;

import com.geekersjoel237.koracore.domain.port.MailPort;
import com.geekersjoel237.koracore.domain.port.OtpMailContext;

/**
 * Created on 01/03/2026
 *
 * @author Geekers_Joel237
 **/
public class InMemoryMailPort implements MailPort {
    @Override
    public void sendOtp(String toEmail, String otpCode, OtpMailContext context) {
        System.out.printf("Sending OTP [%s] to %s for %s%n", otpCode, toEmail, context);
    }
}
