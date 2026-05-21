package com.geekersjoel237.koracore.domain.port;

import com.geekersjoel237.koracore.domain.OtpMailContext;

public interface MailPort {
    void sendOtp(String toEmail, String otpCode, OtpMailContext context);
}