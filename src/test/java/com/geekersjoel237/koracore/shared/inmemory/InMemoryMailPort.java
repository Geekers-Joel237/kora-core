package com.geekersjoel237.koracore.shared.inmemory;

import com.geekersjoel237.koracore.domain.port.MailPort;

/**
 * Created on 01/03/2026
 *
 * @author Geekers_Joel237
 **/
public class InMemoryMailPort implements MailPort {
    @Override
    public void sendOtp(String toEmail, String otpCode, String subject) {
        // no-op — OTP is retrieved directly from OtpStore in tests
    }
}
